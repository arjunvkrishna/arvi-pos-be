package com.neram.pos.service;

import com.neram.pos.dto.BillRefundDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillRefund;
import com.neram.pos.entity.User;
import com.neram.pos.repository.BillRefundRepository;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillRefundService {

    private final BillRefundRepository refundRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final CashierShiftService cashierShiftService;
    private final AuditLogService auditLogService;

    @Transactional
    public BillRefundDTO.RefundResponse processRefund(Long billId, BillRefundDTO.RefundRequest req) {
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found with id: " + billId));

        if (bill.getStatus() != Bill.Status.PAID && bill.getStatus() != Bill.Status.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Only paid bills can be refunded. Current bill status is: " + bill.getStatus());
        }

        // Idempotency check: if request has idempotency key, return existing successful refund if present
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().trim().isEmpty()) {
            Optional<BillRefund> existing = refundRepository.findByIdempotencyKey(req.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                BillRefund past = existing.get();
                if (past.getBillId().equals(billId)) {
                    log.info("Duplicate refund request with idempotency key: {} for bill: {}", req.getIdempotencyKey(), billId);
                    return toDTO(past, bill);
                } else {
                    throw new IllegalArgumentException("Idempotency key has already been used for another bill #" + past.getBillId());
                }
            }
        }

        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero.");
        }
        if (req.getReason() == null || req.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("A valid reason is required for refunds.");
        }

        BigDecimal refundAmt = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal originalNet = bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO;

        // Compute successful refunds already recorded
        List<BillRefund> pastRefunds = refundRepository.findByBillIdAndStatus(billId, BillRefund.RefundStatus.SUCCESS);
        BigDecimal totalRefundedSoFar = pastRefunds.stream()
                .map(BillRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal remainingRefundable = originalNet.subtract(totalRefundedSoFar).setScale(2, RoundingMode.HALF_UP);

        if (refundAmt.compareTo(remainingRefundable) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Requested refund of ₹%.2f exceeds remaining refundable balance of ₹%.2f (Original Bill: ₹%.2f, Already Refunded: ₹%.2f)",
                    refundAmt, remainingRefundable, originalNet, totalRefundedSoFar));
        }

        Bill.PaymentMode pMode = bill.getPaymentMode();
        if (req.getPaymentMode() != null && !req.getPaymentMode().trim().isEmpty()) {
            try {
                pMode = Bill.PaymentMode.valueOf(req.getPaymentMode().trim().toUpperCase());
            } catch (Exception ignored) {}
        }

        User currentUser = getCurrentUser();

        BillRefund refund = BillRefund.builder()
                .billId(bill.getId())
                .amount(refundAmt)
                .paymentMode(pMode)
                .reason(req.getReason().trim())
                .refundedBy(currentUser != null ? currentUser.getId() : null)
                .refundedByName(currentUser != null ? currentUser.getUsername() : "Staff")
                .refundedAt(LocalDateTime.now())
                .status(BillRefund.RefundStatus.SUCCESS)
                .idempotencyKey(req.getIdempotencyKey() != null ? req.getIdempotencyKey().trim() : null)
                .externalReference(req.getExternalReference() != null ? req.getExternalReference().trim() : null)
                .createdAt(LocalDateTime.now())
                .build();

        BillRefund savedRefund = refundRepository.save(refund);

        // Update bill status
        BigDecimal newRemaining = remainingRefundable.subtract(refundAmt).setScale(2, RoundingMode.HALF_UP);
        if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
            bill.setStatus(Bill.Status.REFUNDED);
        } else {
            bill.setStatus(Bill.Status.PARTIALLY_REFUNDED);
        }
        billRepository.save(bill);

        // Link with active cashier shift
        cashierShiftService.recordRefund(savedRefund);
        refundRepository.save(savedRefund);

        auditLogService.log("REFUND_BILL", "BILL", bill.getId(),
                String.format("Net: ₹%.2f | Prior Refunds: ₹%.2f", originalNet, totalRefundedSoFar),
                String.format("Refunded ₹%.2f via %s for Bill #%s. Remaining: ₹%.2f. Reason: %s",
                        refundAmt, pMode, bill.getBillNumber(), newRemaining, req.getReason()));

        return toDTO(savedRefund, bill);
    }

    public List<BillRefundDTO.RefundResponse> getRefundsForBill(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found with id: " + billId));
        return refundRepository.findByBillId(billId).stream()
                .map(r -> toDTO(r, bill))
                .collect(Collectors.toList());
    }

    public List<BillRefundDTO.RefundResponse> getAllRefunds() {
        return refundRepository.findAll().stream()
                .map(r -> {
                    Bill b = billRepository.findById(r.getBillId()).orElse(null);
                    return toDTO(r, b);
                })
                .collect(Collectors.toList());
    }

    private User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                return userRepository.findByUsername(auth.getName()).orElse(null);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private BillRefundDTO.RefundResponse toDTO(BillRefund r, Bill b) {
        BigDecimal originalNet = b != null && b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
        List<BillRefund> allSuccessful = refundRepository.findByBillIdAndStatus(r.getBillId(), BillRefund.RefundStatus.SUCCESS);
        BigDecimal totalRefunded = allSuccessful.stream()
                .map(BillRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = originalNet.subtract(totalRefunded).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return BillRefundDTO.RefundResponse.builder()
                .id(r.getId())
                .billId(r.getBillId())
                .billNumber(b != null ? b.getBillNumber() : null)
                .amount(r.getAmount())
                .paymentMode(r.getPaymentMode() != null ? r.getPaymentMode().name() : "CASH")
                .reason(r.getReason())
                .refundedBy(r.getRefundedBy())
                .refundedByName(r.getRefundedByName())
                .refundedAt(r.getRefundedAt())
                .status(r.getStatus().name())
                .idempotencyKey(r.getIdempotencyKey())
                .externalReference(r.getExternalReference())
                .shiftId(r.getShiftId())
                .originalBillNetAmount(originalNet)
                .remainingRefundableAmount(remaining)
                .build();
    }
}
