package com.neram.pos.service;

import com.neram.pos.dto.GiftCardDTO;
import com.neram.pos.entity.Customer;
import com.neram.pos.entity.GiftCard;
import com.neram.pos.entity.GiftCardTransaction;
import com.neram.pos.repository.CustomerRepository;
import com.neram.pos.repository.GiftCardRepository;
import com.neram.pos.repository.GiftCardTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class GiftCardService {

    private final GiftCardRepository giftCardRepository;
    private final GiftCardTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final CashierShiftService cashierShiftService;

    private static final String ALPHANUM = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // exclude easily confused chars (0,1,I,O)
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCode() {
        StringBuilder sb = new StringBuilder("GC-");
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    public Page<GiftCardDTO> listCards(Pageable pageable) {
        return giftCardRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDTO);
    }

    public Optional<GiftCardDTO> getByCode(String code) {
        if (code == null) return Optional.empty();
        return giftCardRepository.findByCode(code.trim().toUpperCase()).map(this::toDTO);
    }

    @Transactional
    public GiftCardDTO issueGiftCard(BigDecimal initialBalance, String paymentMode, Long customerId, LocalDate expiresAt, Long issuedBy) {
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial balance must be greater than zero.");
        }
        if (paymentMode == null || paymentMode.isBlank()) {
            throw new IllegalArgumentException("Payment mode must be specified (CASH, UPI, CARD).");
        }
        String cleanMode = paymentMode.trim().toUpperCase();

        String code = generateCode();
        while (giftCardRepository.findByCode(code).isPresent()) {
            code = generateCode();
        }

        GiftCard card = GiftCard.builder()
                .code(code)
                .initialBalance(initialBalance)
                .currentBalance(initialBalance)
                .status(GiftCard.Status.ACTIVE)
                .paymentMode(cleanMode)
                .issuedBy(issuedBy)
                .issuedToCustomerId(customerId)
                .expiresAt(expiresAt)
                .build();
        GiftCard saved = giftCardRepository.save(card);

        // Record initial issuance transaction
        GiftCardTransaction tx = GiftCardTransaction.builder()
                .giftCardId(saved.getId())
                .amount(initialBalance)
                .balanceAfter(initialBalance)
                .build();
        transactionRepository.save(tx);

        // If paid in cash, record in active cashier shift drawer float
        if ("CASH".equals(cleanMode)) {
            cashierShiftService.recordGiftCardCashSale(initialBalance);
        }

        return toDTO(saved);
    }

    @Transactional
    public void redeem(String code, BigDecimal amount, Long billId) {
        if (code == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid redemption request.");
        }
        GiftCard card = giftCardRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Gift card not found: " + code));

        if (card.getStatus() != GiftCard.Status.ACTIVE) {
            throw new IllegalArgumentException("Gift card is " + card.getStatus());
        }
        if (card.getExpiresAt() != null && card.getExpiresAt().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Gift card expired on " + card.getExpiresAt());
        }

        int updated = giftCardRepository.deductBalanceSafely(card.getId(), amount, GiftCard.Status.ACTIVE);
        if (updated == 0) {
            throw new IllegalArgumentException("Insufficient balance on gift card. Available: ₹" + card.getCurrentBalance());
        }

        BigDecimal balanceAfter = card.getCurrentBalance().subtract(amount);
        GiftCardTransaction tx = GiftCardTransaction.builder()
                .giftCardId(card.getId())
                .billId(billId)
                .amount(amount.negate())
                .balanceAfter(balanceAfter)
                .build();
        transactionRepository.save(tx);
    }

    @Transactional
    public void refundForBill(Long billId) {
        if (billId == null) return;
        List<GiftCardTransaction> txs = transactionRepository.findByBillId(billId);
        boolean alreadyRefunded = txs.stream().anyMatch(t -> t.getAmount().signum() > 0);
        if (alreadyRefunded) {
            log.info("Gift card refund already processed for bill {}", billId);
            return;
        }
        for (GiftCardTransaction tx : txs) {
            if (tx.getAmount().signum() < 0) { // was a redemption
                BigDecimal refundAmt = tx.getAmount().abs();
                giftCardRepository.restoreBalanceSafely(tx.getGiftCardId(), refundAmt);

                GiftCard card = giftCardRepository.findById(tx.getGiftCardId()).orElse(null);
                BigDecimal newBal = card != null ? card.getCurrentBalance() : refundAmt;

                GiftCardTransaction restoreTx = GiftCardTransaction.builder()
                        .giftCardId(tx.getGiftCardId())
                        .billId(billId)
                        .amount(refundAmt)
                        .balanceAfter(newBal)
                        .build();
                transactionRepository.save(restoreTx);
            }
        }
    }

    public GiftCardDTO toDTO(GiftCard g) {
        String customerName = null;
        if (g.getIssuedToCustomerId() != null) {
            customerName = customerRepository.findById(g.getIssuedToCustomerId())
                    .map(Customer::getName).orElse(null);
        }
        return GiftCardDTO.builder()
                .id(g.getId())
                .code(g.getCode())
                .initialBalance(g.getInitialBalance())
                .currentBalance(g.getCurrentBalance())
                .status(g.getStatus().name())
                .paymentMode(g.getPaymentMode())
                .issuedToCustomerId(g.getIssuedToCustomerId())
                .issuedToCustomerName(customerName)
                .expiresAt(g.getExpiresAt())
                .createdAt(g.getCreatedAt())
                .build();
    }
}
