package com.neram.pos.service;

import com.neram.pos.dto.CashierShiftDTO;
import com.neram.pos.dto.DrawerAnalysisDTO;
import com.neram.pos.dto.DrawerAnalysisDTO.*;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashierShiftService {

    private final CashierShiftRepository shiftRepository;
    private final CashMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ShiftSettingRepository shiftSettingRepository;
    private final BillRepository billRepository;
    private final BillRefundRepository billRefundRepository;
    private final CafeExpenseRepository cafeExpenseRepository;
    private final com.neram.pos.repository.BillPaymentRepository billPaymentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Optional<CashierShift> getOpenShiftForRegister(String registerId) {
        String reg = (registerId != null && !registerId.trim().isEmpty()) ? registerId.trim() : "REG-01";
        return shiftRepository.findByRegisterIdAndStatus(reg, CashierShift.ShiftStatus.OPEN);
    }

    public Optional<CashierShift> getActiveShift() {
        List<CashierShift> openShifts = shiftRepository.findAllByStatusOrderByOpenedAtDesc(CashierShift.ShiftStatus.OPEN);
        return openShifts.stream().findFirst();
    }

    @Transactional
    public CashierShiftDTO.ShiftResponse openShift(CashierShiftDTO.OpenShiftRequest req) {
        String registerId = (req.getRegisterId() != null && !req.getRegisterId().trim().isEmpty())
                ? req.getRegisterId().trim() : "REG-01";

        if (!isShiftModuleEnabled()) {
            User currentUser = getCurrentUser();
            if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
                throw new IllegalStateException("Cashier Shift module is currently disabled by Administrator.");
            }
        }

        // Bills do not carry a register identifier, so this POS can safely reconcile
        // payments only while one drawer shift is open across the system.
        shiftRepository.findAllByStatusOrderByOpenedAtDesc(CashierShift.ShiftStatus.OPEN)
                .stream().findFirst().ifPresent(existing -> {
                    throw new IllegalStateException("Shift #" + existing.getId() + " is already open for register '"
                            + existing.getRegisterId() + "'. Close it before opening another shift.");
                });

        User currentUser = getCurrentUser();
        BigDecimal openingFloat = req.getOpeningFloat() != null
                ? req.getOpeningFloat().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (openingFloat.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Opening float cannot be negative.");
        }

        // Validate previous shift handover reference if provided
        Long prevShiftId = req.getPreviousShiftId();
        if (prevShiftId != null) {
            CashierShift prevShift = shiftRepository.findById(prevShiftId)
                    .orElseThrow(() -> new IllegalArgumentException("Previous shift #" + prevShiftId + " not found."));
            if (prevShift.getStatus() != CashierShift.ShiftStatus.CLOSED) {
                throw new IllegalStateException("Previous shift #" + prevShiftId + " must be closed before handover carry-forward.");
            }
        }

        CashierShift shift = CashierShift.builder()
                .registerId(registerId)
                .cashierId(currentUser != null ? currentUser.getId() : 1L)
                .cashierName(currentUser != null ? currentUser.getUsername() : "Cashier")
                .openedAt(LocalDateTime.now())
                .openingFloat(openingFloat)
                .previousShiftId(prevShiftId)
                .carryForwardRef(req.getCarryForwardRef())
                .status(CashierShift.ShiftStatus.OPEN)
                .notes(req.getNotes())
                .cashSales(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .upiSales(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .cardSales(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .cashRefunds(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .upiRefunds(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .cardRefunds(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .cashInTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .cashOutTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .transfersOutTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .transfersInTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .isCorrected(false)
                .createdAt(LocalDateTime.now())
                .build();

        CashierShift saved = shiftRepository.save(shift);
        String auditDetail = String.format("Opened Shift #%d on %s | Cashier: %s | Float: ₹%.2f%s",
                saved.getId(), saved.getRegisterId(), saved.getCashierName(), saved.getOpeningFloat(),
                (prevShiftId != null ? String.format(" | Handover from Shift #%d (%s)", prevShiftId, req.getCarryForwardRef()) : ""));
        auditLogService.log("OPEN_SHIFT", "SHIFT", saved.getId(), null, auditDetail);
        return toDTO(saved);
    }

    @Transactional
    public CashierShiftDTO.CashMovementResponse recordCashMovement(Long shiftId, CashierShiftDTO.CashMovementRequest req) {
        CashierShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found with id: " + shiftId));

        if (shift.getStatus() == CashierShift.ShiftStatus.CLOSED) {
            throw new IllegalStateException("Cannot add cash movements to a closed shift.");
        }
        if (!isShiftModuleEnabled()) {
            User currentUser = getCurrentUser();
            if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
                throw new IllegalStateException("Cashier Shift module is currently disabled by Administrator.");
            }
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Movement amount must be greater than zero.");
        }
        if (req.getReason() == null || req.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for drawer cash movements.");
        }

        CashMovement.MovementType type = CashMovement.MovementType.valueOf(req.getMovementType().toUpperCase());
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        User currentUser = getCurrentUser();

        String transferType = (req.getTransferType() != null && !req.getTransferType().trim().isEmpty())
                ? req.getTransferType().trim().toUpperCase()
                : (type == CashMovement.MovementType.CASH_IN ? "MANUAL_IN" : "PETTY_CASH");

        if (type == CashMovement.MovementType.CASH_OUT) {
            assertSufficientDrawerCash(shift, amount);
        }

        CashMovement movement = CashMovement.builder()
                .shiftId(shift.getId())
                .movementType(type)
                .amount(amount)
                .reason(req.getReason().trim())
                .transferType(transferType)
                .destinationAccount(req.getDestinationAccount())
                .referenceNumber(req.getReferenceNumber())
                .userId(currentUser != null ? currentUser.getId() : null)
                .userName(currentUser != null ? currentUser.getUsername() : "Staff")
                .approvedBy(req.getApprovedBy())
                .createdAt(LocalDateTime.now())
                .build();

        CashMovement saved = movementRepository.save(movement);

        // Update corresponding totals on shift
        if (type == CashMovement.MovementType.CASH_IN) {
            if ("BANK_TO_DRAWER".equals(transferType) || "SAFE_TO_DRAWER".equals(transferType)) {
                shift.setTransfersInTotal(shift.getTransfersInTotal().add(amount));
            } else {
                shift.setCashInTotal(shift.getCashInTotal().add(amount));
            }
        } else {
            if ("DRAWER_TO_BANK".equals(transferType) || "DRAWER_TO_SAFE".equals(transferType)) {
                shift.setTransfersOutTotal(shift.getTransfersOutTotal().add(amount));
            } else {
                shift.setCashOutTotal(shift.getCashOutTotal().add(amount));
            }
        }
        shiftRepository.save(shift);

        auditLogService.log("CASH_MOVEMENT", "SHIFT", shift.getId(), null,
                String.format("%s (%s) of ₹%.2f on Shift #%d: %s", type, transferType, amount, shift.getId(), req.getReason()));
        return toMovementDTO(saved);
    }

    /**
     * Record a formal Drawer to Bank/Safe or Bank/Safe to Drawer transfer.
     * Reduces or increases the physical expected drawer cash and appears in the shift ledger.
     * Does NOT treat the transfer as a sale, expense, or profit.
     */
    @Transactional
    public CashierShiftDTO.CashMovementResponse recordTransfer(Long shiftId, CashierShiftDTO.TransferRequest req) {
        CashierShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found with id: " + shiftId));

        if (shift.getStatus() == CashierShift.ShiftStatus.CLOSED) {
            throw new IllegalStateException("Cannot record cash transfers to a closed shift.");
        }
        if (!isShiftModuleEnabled()) {
            User currentUser = getCurrentUser();
            if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
                throw new IllegalStateException("Cashier Shift module is currently disabled by Administrator.");
            }
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }
        if (req.getReason() == null || req.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for cash transfers.");
        }
        if (req.getTransferType() == null || req.getTransferType().trim().isEmpty()) {
            throw new IllegalArgumentException("Transfer type is required (e.g., DRAWER_TO_BANK, DRAWER_TO_SAFE, BANK_TO_DRAWER, SAFE_TO_DRAWER).");
        }

        String tType = req.getTransferType().trim().toUpperCase();
        CashMovement.MovementType movementType;
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (tType.startsWith("DRAWER_TO_")) {
            assertSufficientDrawerCash(shift, amount);
            movementType = CashMovement.MovementType.CASH_OUT;
            shift.setTransfersOutTotal(shift.getTransfersOutTotal().add(amount));
        } else if (tType.endsWith("_TO_DRAWER")) {
            movementType = CashMovement.MovementType.CASH_IN;
            shift.setTransfersInTotal(shift.getTransfersInTotal().add(amount));
        } else {
            throw new IllegalArgumentException("Invalid transfer type '" + req.getTransferType() + "'. Expected DRAWER_TO_BANK, DRAWER_TO_SAFE, BANK_TO_DRAWER, or SAFE_TO_DRAWER.");
        }

        User currentUser = getCurrentUser();

        CashMovement movement = CashMovement.builder()
                .shiftId(shift.getId())
                .movementType(movementType)
                .amount(amount)
                .reason(req.getReason().trim())
                .transferType(tType)
                .destinationAccount(req.getDestinationAccount() != null ? req.getDestinationAccount().trim() : null)
                .referenceNumber(req.getReferenceNumber() != null ? req.getReferenceNumber().trim() : null)
                .userId(currentUser != null ? currentUser.getId() : null)
                .userName(currentUser != null ? currentUser.getUsername() : "Staff")
                .approvedBy(req.getApprovedBy())
                .createdAt(LocalDateTime.now())
                .build();

        CashMovement saved = movementRepository.save(movement);
        shiftRepository.save(shift);

        String auditDetail = String.format("Recorded transfer %s of ₹%.2f (Account: %s, Ref: %s) on Shift #%d: %s",
                tType, amount, req.getDestinationAccount(), req.getReferenceNumber(), shift.getId(), req.getReason());
        auditLogService.log("DRAWER_TRANSFER", "SHIFT", shift.getId(), null, auditDetail);

        return toMovementDTO(saved);
    }

    /**
     * Explicit, permission-controlled, timestamped variance correction.
     * Preserves original countedCash and variance, updating corrected fields and writing an audit trail.
     */
    @Transactional
    public CashierShiftDTO.ShiftResponse correctVariance(Long shiftId, CashierShiftDTO.CorrectVarianceRequest req) {
        CashierShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found with id: " + shiftId));

        if (shift.getStatus() != CashierShift.ShiftStatus.CLOSED) {
            throw new IllegalStateException("Only closed shifts can have their physical cash count and variance corrected.");
        }

        User currentUser = getCurrentUser();
        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.MANAGER)) {
            throw new IllegalStateException("Only Managers and Administrators have permission to correct closed shift counts.");
        }

        if (req == null || req.getCountedCash() == null || req.getCountedCash().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valid corrected counted cash is required.");
        }
        if (req.getReason() == null || req.getReason().trim().length() < 5) {
            throw new IllegalArgumentException("A descriptive reason (at least 5 characters) is required for audit trail when correcting cash variance.");
        }

        BigDecimal newCounted = req.getCountedCash().setScale(2, RoundingMode.HALF_UP);
        BigDecimal expected = shift.getExpectedCash() != null ? shift.getExpectedCash() : calculateExpectedCash(shift);
        BigDecimal newVariance = newCounted.subtract(expected).setScale(2, RoundingMode.HALF_UP);

        BigDecimal oldVariance = shift.getVariance();
        BigDecimal oldCounted = shift.getCountedCash();

        shift.setIsCorrected(true);
        shift.setCorrectedCountedCash(newCounted);
        shift.setCorrectedVariance(newVariance);
        shift.setCorrectionReason(req.getReason().trim());
        shift.setCorrectedByUserId(currentUser.getId());
        shift.setCorrectedByUserName(currentUser.getUsername());
        shift.setCorrectedAt(LocalDateTime.now());

        CashierShift saved = shiftRepository.save(shift);

        String prevDetails = String.format("Counted: ₹%.2f | Variance: ₹%.2f", oldCounted, oldVariance);
        String newDetails = String.format("Corrected Counted: ₹%.2f | Corrected Variance: ₹%.2f | Reason: %s | By: %s",
                newCounted, newVariance, req.getReason().trim(), currentUser.getUsername());
        auditLogService.log("CORRECT_SHIFT_VARIANCE", "SHIFT", saved.getId(), prevDetails, newDetails);

        return toDTO(saved);
    }

    @Transactional
    public void recordBillPayment(Bill bill) {
        if (bill == null || bill.getNetAmount() == null) return;

        Optional<CashierShift> activeShiftOpt = getActiveShift();
        if (activeShiftOpt.isEmpty()) {
            if (isShiftModuleEnabled()) {
                throw new IllegalStateException("Open a manager shift before accepting bill payments.");
            }
            log.warn("No open shift found to link paid bill #{}", bill.getBillNumber());
            return;
        }

        CashierShift shift = activeShiftOpt.get();
        bill.setShiftId(shift.getId());
        BigDecimal net = bill.getNetAmount().setScale(2, RoundingMode.HALF_UP);

        List<SplitPayments.Part> parts = splitPartsOf(bill);
        if (!parts.isEmpty()) {
            addToShift(shift, SplitPayments.byMode(parts), false);
        } else {
            addToShift(shift, singleMode(bill.getPaymentMode(), net), false);
        }
        shiftRepository.save(shift);
    }

    private static Map<Bill.PaymentMode, BigDecimal> singleMode(Bill.PaymentMode mode, BigDecimal amount) {
        Map<Bill.PaymentMode, BigDecimal> m = new EnumMap<>(Bill.PaymentMode.class);
        if (mode != null) m.put(mode, amount);
        return m;
    }

    /** Adds (or removes) money per payment mode on the shift's cash, UPI and card sales. Removing never goes below zero. */
    private static void addToShift(CashierShift shift, Map<Bill.PaymentMode, BigDecimal> money, boolean remove) {
        for (Map.Entry<Bill.PaymentMode, BigDecimal> e : money.entrySet()) {
            BigDecimal v = remove ? e.getValue().negate() : e.getValue();
            if (e.getKey() == Bill.PaymentMode.CASH) shift.setCashSales(shift.getCashSales().add(v).max(BigDecimal.ZERO));
            else if (e.getKey() == Bill.PaymentMode.UPI) shift.setUpiSales(shift.getUpiSales().add(v).max(BigDecimal.ZERO));
            else if (e.getKey() == Bill.PaymentMode.CARD) shift.setCardSales(shift.getCardSales().add(v).max(BigDecimal.ZERO));
        }
    }

    /** The parts of a bill paid in more than one way; empty for a bill paid one way. */
    private List<SplitPayments.Part> splitPartsOf(Bill bill) {
        if (bill.getId() == null || bill.getPaymentSummary() == null) return List.of();
        return billPaymentRepository.findByBillIdOrderByIdAsc(bill.getId()).stream()
                .map(p -> new SplitPayments.Part(Bill.PaymentMode.valueOf(p.getMode()), p.getAmount(), p.getReference())).collect(Collectors.toList());
    }

    @Transactional
    public void recordBillAdjustment(Bill bill, Bill.PaymentMode oldMode, BigDecimal oldNet) {
        recordBillAdjustment(bill, oldMode, oldNet, List.of(), List.of());
    }

    /** A paid bill was edited: take back what it had put on the shift (per part if it was split) and add what it is now. */
    @Transactional
    public void recordBillAdjustment(Bill bill, Bill.PaymentMode oldMode, BigDecimal oldNet, List<SplitPayments.Part> oldParts, List<SplitPayments.Part> newParts) {
        if (bill == null || bill.getShiftId() == null) return;
        shiftRepository.findById(bill.getShiftId()).ifPresent(shift -> {
            if (oldParts != null && !oldParts.isEmpty()) addToShift(shift, SplitPayments.byMode(oldParts), true);
            else if (oldNet != null && oldMode != null) addToShift(shift, singleMode(oldMode, oldNet), true);
            BigDecimal newNet = bill.getNetAmount() != null ? bill.getNetAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            if (newParts != null && !newParts.isEmpty()) addToShift(shift, SplitPayments.byMode(newParts), false);
            else addToShift(shift, singleMode(bill.getPaymentMode(), newNet), false);
            shiftRepository.save(shift);
        });
    }

    @Transactional
    public void recordRefund(BillRefund refund) {
        if (refund == null || refund.getAmount() == null) return;

        Optional<CashierShift> activeShiftOpt = getActiveShift();
        if (activeShiftOpt.isEmpty()) {
            if (isShiftModuleEnabled()) {
                throw new IllegalStateException("Open a manager shift before recording refunds.");
            }
            return;
        }

        CashierShift shift = activeShiftOpt.get();
        refund.setShiftId(shift.getId());
        BigDecimal amt = refund.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (refund.getPaymentMode() == Bill.PaymentMode.CASH) {
            shift.setCashRefunds(shift.getCashRefunds().add(amt));
        } else if (refund.getPaymentMode() == Bill.PaymentMode.UPI) {
            shift.setUpiRefunds(shift.getUpiRefunds().add(amt));
        } else if (refund.getPaymentMode() == Bill.PaymentMode.CARD) {
            shift.setCardRefunds(shift.getCardRefunds().add(amt));
        }
        shiftRepository.save(shift);
    }

    /**
     * Exact traceable drawer reconciliation formula:
     * expected drawer cash = opening float + cash sales + cash added - cash refunds paid in cash
     *                         - petty cash and other cash paid out - transfers from drawer to bank/safe
     *                         + transfers back into the drawer
     */
    /** Money cannot leave the drawer beyond what the drawer is expected to hold. */
    private void assertSufficientDrawerCash(CashierShift shift, BigDecimal amount) {
        BigDecimal available = calculateExpectedCash(shift);
        if (amount.compareTo(available) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Insufficient cash in drawer: ₹%.2f requested but only ₹%.2f is available.", amount, available.max(BigDecimal.ZERO)));
        }
    }

    public BigDecimal calculateExpectedCash(CashierShift shift) {
        BigDecimal opening = shift.getOpeningFloat() != null ? shift.getOpeningFloat() : BigDecimal.ZERO;
        BigDecimal sales = shift.getCashSales() != null ? shift.getCashSales() : BigDecimal.ZERO;
        BigDecimal in = shift.getCashInTotal() != null ? shift.getCashInTotal() : BigDecimal.ZERO;
        BigDecimal refunds = shift.getCashRefunds() != null ? shift.getCashRefunds() : BigDecimal.ZERO;
        BigDecimal out = shift.getCashOutTotal() != null ? shift.getCashOutTotal() : BigDecimal.ZERO;
        BigDecimal transfersOut = shift.getTransfersOutTotal() != null ? shift.getTransfersOutTotal() : BigDecimal.ZERO;
        BigDecimal transfersIn = shift.getTransfersInTotal() != null ? shift.getTransfersInTotal() : BigDecimal.ZERO;

        return opening.add(sales).add(in)
                .subtract(refunds).subtract(out)
                .subtract(transfersOut).add(transfersIn)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public CashierShiftDTO.ShiftResponse closeShift(Long shiftId, CashierShiftDTO.CloseShiftRequest req) {
        CashierShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found with id: " + shiftId));

        if (shift.getStatus() == CashierShift.ShiftStatus.CLOSED) {
            log.info("Shift #{} is already closed; returning existing closed record.", shiftId);
            return toDTO(shift);
        }

        User currentUser = getCurrentUser();
        if (currentUser != null && !currentUser.getId().equals(shift.getCashierId())) {
            boolean isElevated = currentUser.getRole() == User.Role.ADMIN || currentUser.getRole() == User.Role.MANAGER;
            if (!isElevated) {
                throw new IllegalStateException("Cashiers cannot close another cashier's shift without manager/admin permissions.");
            }
        }

        if (req == null || req.getCountedCash() == null) {
            throw new IllegalArgumentException("Enter the physical cash count before closing the shift.");
        }
        BigDecimal counted = req.getCountedCash().setScale(2, RoundingMode.HALF_UP);
        if (counted.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Counted cash cannot be negative.");
        }

        BigDecimal expected = calculateExpectedCash(shift);
        BigDecimal variance = counted.subtract(expected).setScale(2, RoundingMode.HALF_UP);

        shift.setStatus(CashierShift.ShiftStatus.CLOSED);
        shift.setClosedAt(LocalDateTime.now());
        shift.setClosingUserId(currentUser != null ? currentUser.getId() : null);
        shift.setClosingUserName(currentUser != null ? currentUser.getUsername() : "User");
        shift.setCountedCash(counted);
        shift.setExpectedCash(expected);
        shift.setVariance(variance);
        if (req.getNotes() != null && !req.getNotes().trim().isEmpty()) {
            String closingNote = "Closing / handover: " + req.getNotes().trim();
            String openingNotes = shift.getNotes();
            shift.setNotes(openingNotes == null || openingNotes.trim().isEmpty()
                    ? closingNote
                    : "Opening: " + openingNotes.trim() + "\n" + closingNote);
        }

        CashierShift saved = shiftRepository.save(shift);
        auditLogService.log("CLOSE_SHIFT", "SHIFT", saved.getId(), null,
                String.format("Closed Shift #%d | Expected: ₹%.2f | Counted: ₹%.2f | Variance: ₹%.2f",
                        saved.getId(), expected, counted, variance));
        return toDTO(saved);
    }

    public List<CashierShiftDTO.ShiftResponse> getAllShifts() {
        return shiftRepository.findAllByOrderByOpenedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CashierShiftDTO.ShiftResponse getShiftById(Long id) {
        CashierShift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found with id: " + id));
        return toDTO(shift);
    }

    public CashierShiftDTO.ShiftResponse getCurrentShiftDetails() {
        CashierShift shift = getActiveShift()
                .orElseThrow(() -> new IllegalStateException("No open cashier shift currently active."));
        return toDTO(shift);
    }

    // ============================================================
    // DRAWER ANALYSIS, ANOMALY DETECTION & AGGREGATIONS
    // ============================================================

    public DrawerAnalysisDTO getDrawerAnalysis(String periodParam, LocalDate customStart, LocalDate customEnd,
                                               Integer month, Integer year, Long cashierId, String registerId, String status) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        if (month != null && year != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else if (periodParam != null && !periodParam.isBlank()) {
            switch (periodParam.toUpperCase()) {
                case "TODAY":
                    startDate = today;
                    endDate = today;
                    break;
                case "YESTERDAY":
                    startDate = today.minusDays(1);
                    endDate = today.minusDays(1);
                    break;
                case "THIS_WEEK":
                    startDate = today.with(DayOfWeek.MONDAY);
                    endDate = today;
                    break;
                case "LAST_WEEK":
                    startDate = today.minusWeeks(1).with(DayOfWeek.MONDAY);
                    endDate = today.minusWeeks(1).with(DayOfWeek.SUNDAY);
                    break;
                case "THIS_MONTH":
                    startDate = today.withDayOfMonth(1);
                    endDate = today;
                    break;
                case "LAST_MONTH":
                    LocalDate firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
                    startDate = firstOfLastMonth;
                    endDate = firstOfLastMonth.withDayOfMonth(firstOfLastMonth.lengthOfMonth());
                    break;
                case "THIS_YEAR":
                    startDate = today.withDayOfYear(1);
                    endDate = today;
                    break;
                case "ALL":
                    startDate = LocalDate.of(2020, 1, 1);
                    endDate = today.plusDays(1);
                    break;
                case "CUSTOM":
                default:
                    startDate = customStart != null ? customStart : today.withDayOfMonth(1);
                    endDate = customEnd != null ? customEnd : today;
            }
        } else if (customStart != null || customEnd != null) {
            startDate = customStart != null ? customStart : today.withDayOfMonth(1);
            endDate = customEnd != null ? customEnd : today;
        } else {
            startDate = today.withDayOfMonth(1);
            endDate = today;
        }

        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(23, 59, 59);

        // Fetch shift settings for anomaly thresholds
        ShiftSetting settings = shiftSettingRepository.findById(1L).orElseGet(() ->
                ShiftSetting.builder().id(1L).enabled(true).build());

        BigDecimal varianceThreshold = settings.getVarianceThreshold() != null
                ? settings.getVarianceThreshold() : new BigDecimal("500.00");
        BigDecimal largeMovementThreshold = settings.getLargeMovementThreshold() != null
                ? settings.getLargeMovementThreshold() : new BigDecimal("5000.00");
        boolean flagMissingCount = !Boolean.FALSE.equals(settings.getFlagMissingCount());
        boolean flagTransfersWithoutRef = !Boolean.FALSE.equals(settings.getFlagTransfersWithoutRef());

        // Fetch shifts in range
        List<CashierShift> rawShifts = shiftRepository.findByOpenedAtBetweenOrderByOpenedAtDesc(rangeStart, rangeEnd);

        // Filter by cashierId, registerId, status if specified
        List<CashierShift> filteredShifts = rawShifts.stream()
                .filter(s -> cashierId == null || cashierId.equals(s.getCashierId()))
                .filter(s -> registerId == null || registerId.isBlank() || registerId.equalsIgnoreCase(s.getRegisterId()))
                .filter(s -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) || status.equalsIgnoreCase(s.getStatus().name()))
                .collect(Collectors.toList());

        // Initialize aggregations
        int totalShifts = filteredShifts.size();
        int openShifts = 0;
        int closedShifts = 0;

        BigDecimal totalFloat = BigDecimal.ZERO;
        BigDecimal totalCashSales = BigDecimal.ZERO;
        BigDecimal totalUpiSales = BigDecimal.ZERO;
        BigDecimal totalCardSales = BigDecimal.ZERO;
        BigDecimal totalCashRefunds = BigDecimal.ZERO;
        BigDecimal totalUpiRefunds = BigDecimal.ZERO;
        BigDecimal totalCardRefunds = BigDecimal.ZERO;
        BigDecimal totalCashIn = BigDecimal.ZERO;
        BigDecimal totalCashOut = BigDecimal.ZERO;
        BigDecimal totalTransfersOut = BigDecimal.ZERO;
        BigDecimal totalTransfersIn = BigDecimal.ZERO;
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalCounted = BigDecimal.ZERO;

        int shortageCount = 0;
        BigDecimal totalShortage = BigDecimal.ZERO;
        int surplusCount = 0;
        BigDecimal totalSurplus = BigDecimal.ZERO;
        int balancedCount = 0;
        int anomalousShiftsCount = 0;

        List<ShiftReconciliationItemDTO> shiftItems = new ArrayList<>();
        Map<LocalDate, List<CashierShift>> shiftsByDate = new TreeMap<>(Comparator.reverseOrder());
        Map<String, List<CashierShift>> shiftsByMonth = new TreeMap<>(Comparator.reverseOrder());

        // Track cashier discrepancy occurrences for repeated variance anomaly rule
        Map<Long, Integer> cashierVarianceCount = new HashMap<>();
        for (CashierShift s : filteredShifts) {
            if (s.getStatus() == CashierShift.ShiftStatus.CLOSED) {
                BigDecimal v = (s.getIsCorrected() != null && s.getIsCorrected() && s.getCorrectedVariance() != null)
                        ? s.getCorrectedVariance() : (s.getVariance() != null ? s.getVariance() : BigDecimal.ZERO);
                if (v.compareTo(BigDecimal.ZERO) != 0) {
                    cashierVarianceCount.put(s.getCashierId(), cashierVarianceCount.getOrDefault(s.getCashierId(), 0) + 1);
                }
            }
        }

        for (CashierShift s : filteredShifts) {
            boolean isOpen = s.getStatus() == CashierShift.ShiftStatus.OPEN;
            if (isOpen) openShifts++;
            else closedShifts++;

            BigDecimal openingFloat = s.getOpeningFloat() != null ? s.getOpeningFloat() : BigDecimal.ZERO;
            BigDecimal cashSales = s.getCashSales() != null ? s.getCashSales() : BigDecimal.ZERO;
            BigDecimal upiSales = s.getUpiSales() != null ? s.getUpiSales() : BigDecimal.ZERO;
            BigDecimal cardSales = s.getCardSales() != null ? s.getCardSales() : BigDecimal.ZERO;
            BigDecimal cashRefunds = s.getCashRefunds() != null ? s.getCashRefunds() : BigDecimal.ZERO;
            BigDecimal upiRefunds = s.getUpiRefunds() != null ? s.getUpiRefunds() : BigDecimal.ZERO;
            BigDecimal cardRefunds = s.getCardRefunds() != null ? s.getCardRefunds() : BigDecimal.ZERO;
            BigDecimal cashIn = s.getCashInTotal() != null ? s.getCashInTotal() : BigDecimal.ZERO;
            BigDecimal cashOut = s.getCashOutTotal() != null ? s.getCashOutTotal() : BigDecimal.ZERO;
            BigDecimal transfersOut = s.getTransfersOutTotal() != null ? s.getTransfersOutTotal() : BigDecimal.ZERO;
            BigDecimal transfersIn = s.getTransfersInTotal() != null ? s.getTransfersInTotal() : BigDecimal.ZERO;

            BigDecimal expected = calculateExpectedCash(s);
            BigDecimal counted = s.getCountedCash() != null ? s.getCountedCash() : BigDecimal.ZERO;
            BigDecimal variance = isOpen ? BigDecimal.ZERO : counted.subtract(expected).setScale(2, RoundingMode.HALF_UP);

            // Use corrected variance if explicit correction was made
            BigDecimal effectiveVariance = (s.getIsCorrected() != null && s.getIsCorrected() && s.getCorrectedVariance() != null)
                    ? s.getCorrectedVariance() : variance;
            BigDecimal effectiveCounted = (s.getIsCorrected() != null && s.getIsCorrected() && s.getCorrectedCountedCash() != null)
                    ? s.getCorrectedCountedCash() : counted;

            totalFloat = totalFloat.add(openingFloat);
            totalCashSales = totalCashSales.add(cashSales);
            totalUpiSales = totalUpiSales.add(upiSales);
            totalCardSales = totalCardSales.add(cardSales);
            totalCashRefunds = totalCashRefunds.add(cashRefunds);
            totalUpiRefunds = totalUpiRefunds.add(upiRefunds);
            totalCardRefunds = totalCardRefunds.add(cardRefunds);
            totalCashIn = totalCashIn.add(cashIn);
            totalCashOut = totalCashOut.add(cashOut);
            totalTransfersOut = totalTransfersOut.add(transfersOut);
            totalTransfersIn = totalTransfersIn.add(transfersIn);
            totalExpected = totalExpected.add(expected);
            if (!isOpen) {
                totalCounted = totalCounted.add(effectiveCounted);
                if (effectiveVariance.compareTo(BigDecimal.ZERO) < 0) {
                    shortageCount++;
                    totalShortage = totalShortage.add(effectiveVariance.abs());
                } else if (effectiveVariance.compareTo(BigDecimal.ZERO) > 0) {
                    surplusCount++;
                    totalSurplus = totalSurplus.add(effectiveVariance);
                } else {
                    balancedCount++;
                }
            }

            // Movements, Bills, Refunds
            List<CashMovement> movements = movementRepository.findByShiftIdOrderByCreatedAtAsc(s.getId());
            int billCount = billRepository.findByShiftId(s.getId()).size();
            int refundCount = billRefundRepository.findByShiftId(s.getId()).size();

            // Calculate shift duration
            LocalDateTime endDt = s.getClosedAt() != null ? s.getClosedAt() : LocalDateTime.now();
            long durationMins = Duration.between(s.getOpenedAt(), endDt).toMinutes();

            // Anomaly Evaluation
            List<AnomalyFlagDTO> anomalies = new ArrayList<>();

            // 1. Large Variance
            if (!isOpen && effectiveVariance.abs().compareTo(varianceThreshold) >= 0) {
                String severity = effectiveVariance.compareTo(BigDecimal.ZERO) < 0 ? "HIGH" : "MEDIUM";
                String typeDesc = effectiveVariance.compareTo(BigDecimal.ZERO) < 0 ? "Shortage" : "Surplus";
                anomalies.add(AnomalyFlagDTO.builder()
                        .flagType("LARGE_VARIANCE")
                        .severity(severity)
                        .description(String.format("Cash %s of ₹%.2f exceeds anomaly threshold (₹%.2f). Expected: ₹%.2f, Counted: ₹%.2f.",
                                typeDesc, effectiveVariance.abs(), varianceThreshold, expected, effectiveCounted))
                        .build());
            }

            // 2. Missing physical count
            if (!isOpen && flagMissingCount && s.getCountedCash() == null) {
                anomalies.add(AnomalyFlagDTO.builder()
                        .flagType("MISSING_PHYSICAL_COUNT")
                        .severity("HIGH")
                        .description("Shift was marked closed without an entered physical cash count.")
                        .build());
            }

            // 3. Shift left open > 24 hours
            if (isOpen && s.getOpenedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                long openHours = Duration.between(s.getOpenedAt(), LocalDateTime.now()).toHours();
                anomalies.add(AnomalyFlagDTO.builder()
                        .flagType("SHIFT_LEFT_OPEN")
                        .severity("HIGH")
                        .description(String.format("Shift opened %d hours ago has not been reconciled or closed.", openHours))
                        .build());
            }

            // 4. Missing transfer reference
            if (flagTransfersWithoutRef) {
                for (CashMovement m : movements) {
                    String tType = m.getTransferType();
                    if (tType != null && (tType.contains("BANK") || tType.contains("SAFE"))) {
                        if (m.getReferenceNumber() == null || m.getReferenceNumber().trim().isEmpty()
                                || m.getDestinationAccount() == null || m.getDestinationAccount().trim().isEmpty()) {
                            anomalies.add(AnomalyFlagDTO.builder()
                                    .flagType("TRANSFER_WITHOUT_REF")
                                    .severity("MEDIUM")
                                    .description(String.format("Transfer %s of ₹%.2f lacks reference or destination account.",
                                            tType, m.getAmount()))
                                    .build());
                            break;
                        }
                    }
                }
            }

            // 5. Large cash movement
            for (CashMovement m : movements) {
                if (m.getAmount() != null && m.getAmount().compareTo(largeMovementThreshold) >= 0) {
                    anomalies.add(AnomalyFlagDTO.builder()
                            .flagType("LARGE_CASH_MOVEMENT")
                            .severity("MEDIUM")
                            .description(String.format("Movement of ₹%.2f exceeds threshold of ₹%.2f (%s).",
                                    m.getAmount(), largeMovementThreshold, m.getReason()))
                            .build());
                    break;
                }
            }

            // 6. Repeated cashier variances
            if (cashierVarianceCount.getOrDefault(s.getCashierId(), 0) >= 3) {
                anomalies.add(AnomalyFlagDTO.builder()
                        .flagType("REPEATED_CASHIER_VARIANCE")
                        .severity("HIGH")
                        .description(String.format("Cashier %s has %d variance discrepancies across shifts in this period.",
                                s.getCashierName(), cashierVarianceCount.get(s.getCashierId())))
                        .build());
            }

            if (!anomalies.isEmpty()) {
                anomalousShiftsCount++;
            }

            // Plain English Reconciliation Equation String
            String eq = String.format("₹%.2f (Float) + ₹%.2f (Cash Sales) + ₹%.2f (Cash Added) - ₹%.2f (Cash Refunds) - ₹%.2f (Petty Cash) - ₹%.2f (To Bank/Safe) + ₹%.2f (From Bank/Safe) = ₹%.2f (Expected)",
                    openingFloat, cashSales, cashIn, cashRefunds, cashOut, transfersOut, transfersIn, expected);

            shiftItems.add(ShiftReconciliationItemDTO.builder()
                    .shift(toDTO(s))
                    .durationMinutes(durationMins)
                    .reconciliationEquation(eq)
                    .anomalyFlags(anomalies)
                    .billCount(billCount)
                    .refundCount(refundCount)
                    .movementsCount(movements.size())
                    .build());

            // Group by Date & Month for breakdown tables
            LocalDate shiftDate = s.getOpenedAt().toLocalDate();
            shiftsByDate.computeIfAbsent(shiftDate, k -> new ArrayList<>()).add(s);

            String monthKey = s.getOpenedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            shiftsByMonth.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(s);
        }

        // Compute Daily Summaries
        List<DailyDrawerSummaryDTO> dailySummaries = new ArrayList<>();
        for (Map.Entry<LocalDate, List<CashierShift>> entry : shiftsByDate.entrySet()) {
            LocalDate d = entry.getKey();
            List<CashierShift> dShifts = entry.getValue();

            BigDecimal dFloat = BigDecimal.ZERO;
            BigDecimal dCashSales = BigDecimal.ZERO;
            BigDecimal dUpiSales = BigDecimal.ZERO;
            BigDecimal dCardSales = BigDecimal.ZERO;
            BigDecimal dCashRefunds = BigDecimal.ZERO;
            BigDecimal dCashIn = BigDecimal.ZERO;
            BigDecimal dCashOut = BigDecimal.ZERO;
            BigDecimal dTransfersOut = BigDecimal.ZERO;
            BigDecimal dTransfersIn = BigDecimal.ZERO;
            BigDecimal dExpected = BigDecimal.ZERO;
            BigDecimal dCounted = BigDecimal.ZERO;
            BigDecimal dVariance = BigDecimal.ZERO;
            int dShortage = 0;
            int dSurplus = 0;

            for (CashierShift ds : dShifts) {
                dFloat = dFloat.add(ds.getOpeningFloat() != null ? ds.getOpeningFloat() : BigDecimal.ZERO);
                dCashSales = dCashSales.add(ds.getCashSales() != null ? ds.getCashSales() : BigDecimal.ZERO);
                dUpiSales = dUpiSales.add(ds.getUpiSales() != null ? ds.getUpiSales() : BigDecimal.ZERO);
                dCardSales = dCardSales.add(ds.getCardSales() != null ? ds.getCardSales() : BigDecimal.ZERO);
                dCashRefunds = dCashRefunds.add(ds.getCashRefunds() != null ? ds.getCashRefunds() : BigDecimal.ZERO);
                dCashIn = dCashIn.add(ds.getCashInTotal() != null ? ds.getCashInTotal() : BigDecimal.ZERO);
                dCashOut = dCashOut.add(ds.getCashOutTotal() != null ? ds.getCashOutTotal() : BigDecimal.ZERO);
                dTransfersOut = dTransfersOut.add(ds.getTransfersOutTotal() != null ? ds.getTransfersOutTotal() : BigDecimal.ZERO);
                dTransfersIn = dTransfersIn.add(ds.getTransfersInTotal() != null ? ds.getTransfersInTotal() : BigDecimal.ZERO);

                BigDecimal dsExp = calculateExpectedCash(ds);
                dExpected = dExpected.add(dsExp);
                if (ds.getStatus() == CashierShift.ShiftStatus.CLOSED) {
                    BigDecimal dsCnt = (ds.getIsCorrected() != null && ds.getIsCorrected() && ds.getCorrectedCountedCash() != null)
                            ? ds.getCorrectedCountedCash() : (ds.getCountedCash() != null ? ds.getCountedCash() : BigDecimal.ZERO);
                    dCounted = dCounted.add(dsCnt);
                    BigDecimal dsVar = (ds.getIsCorrected() != null && ds.getIsCorrected() && ds.getCorrectedVariance() != null)
                            ? ds.getCorrectedVariance() : (ds.getVariance() != null ? ds.getVariance() : dsCnt.subtract(dsExp));
                    dVariance = dVariance.add(dsVar);
                    if (dsVar.compareTo(BigDecimal.ZERO) < 0) dShortage++;
                    else if (dsVar.compareTo(BigDecimal.ZERO) > 0) dSurplus++;
                }
            }

            dailySummaries.add(DailyDrawerSummaryDTO.builder()
                    .date(d)
                    .shiftCount(dShifts.size())
                    .openingTotal(dFloat.setScale(2, RoundingMode.HALF_UP))
                    .cashSales(dCashSales.setScale(2, RoundingMode.HALF_UP))
                    .upiSales(dUpiSales.setScale(2, RoundingMode.HALF_UP))
                    .cardSales(dCardSales.setScale(2, RoundingMode.HALF_UP))
                    .cashRefunds(dCashRefunds.setScale(2, RoundingMode.HALF_UP))
                    .cashIn(dCashIn.setScale(2, RoundingMode.HALF_UP))
                    .cashOut(dCashOut.setScale(2, RoundingMode.HALF_UP))
                    .transfersOut(dTransfersOut.setScale(2, RoundingMode.HALF_UP))
                    .transfersIn(dTransfersIn.setScale(2, RoundingMode.HALF_UP))
                    .expectedTotal(dExpected.setScale(2, RoundingMode.HALF_UP))
                    .countedTotal(dCounted.setScale(2, RoundingMode.HALF_UP))
                    .netVariance(dVariance.setScale(2, RoundingMode.HALF_UP))
                    .shortageCount(dShortage)
                    .surplusCount(dSurplus)
                    .build());
        }

        // Compute Monthly Summaries
        List<MonthlyDrawerSummaryDTO> monthlySummaries = new ArrayList<>();
        for (Map.Entry<String, List<CashierShift>> entry : shiftsByMonth.entrySet()) {
            String my = entry.getKey();
            List<CashierShift> mShifts = entry.getValue();

            BigDecimal mFloat = BigDecimal.ZERO;
            BigDecimal mCashSales = BigDecimal.ZERO;
            BigDecimal mExp = BigDecimal.ZERO;
            BigDecimal mCnt = BigDecimal.ZERO;
            BigDecimal mVar = BigDecimal.ZERO;
            int mShortage = 0;
            int mSurplus = 0;

            for (CashierShift ms : mShifts) {
                mFloat = mFloat.add(ms.getOpeningFloat() != null ? ms.getOpeningFloat() : BigDecimal.ZERO);
                mCashSales = mCashSales.add(ms.getCashSales() != null ? ms.getCashSales() : BigDecimal.ZERO);
                BigDecimal msExp = calculateExpectedCash(ms);
                mExp = mExp.add(msExp);
                if (ms.getStatus() == CashierShift.ShiftStatus.CLOSED) {
                    BigDecimal msCnt = (ms.getIsCorrected() != null && ms.getIsCorrected() && ms.getCorrectedCountedCash() != null)
                            ? ms.getCorrectedCountedCash() : (ms.getCountedCash() != null ? ms.getCountedCash() : BigDecimal.ZERO);
                    mCnt = mCnt.add(msCnt);
                    BigDecimal msVar = (ms.getIsCorrected() != null && ms.getIsCorrected() && ms.getCorrectedVariance() != null)
                            ? ms.getCorrectedVariance() : (ms.getVariance() != null ? ms.getVariance() : msCnt.subtract(msExp));
                    mVar = mVar.add(msVar);
                    if (msVar.compareTo(BigDecimal.ZERO) < 0) mShortage++;
                    else if (msVar.compareTo(BigDecimal.ZERO) > 0) mSurplus++;
                }
            }

            monthlySummaries.add(MonthlyDrawerSummaryDTO.builder()
                    .monthYear(my)
                    .shiftCount(mShifts.size())
                    .openingTotal(mFloat.setScale(2, RoundingMode.HALF_UP))
                    .cashSales(mCashSales.setScale(2, RoundingMode.HALF_UP))
                    .expectedTotal(mExp.setScale(2, RoundingMode.HALF_UP))
                    .countedTotal(mCnt.setScale(2, RoundingMode.HALF_UP))
                    .netVariance(mVar.setScale(2, RoundingMode.HALF_UP))
                    .shortageCount(mShortage)
                    .surplusCount(mSurplus)
                    .build());
        }

        BigDecimal netVariance = totalCounted.subtract(totalExpected).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalSalesAll = totalCashSales.add(totalUpiSales).add(totalCardSales).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRefundsAll = totalCashRefunds.add(totalUpiRefunds).add(totalCardRefunds).setScale(2, RoundingMode.HALF_UP);

        // Distinctions: Drawer Cash Flow, Bank Transfers, Operating Expenses, Accounting Profit
        BigDecimal drawerCashFlow = totalCashSales.subtract(totalCashRefunds)
                .add(totalCashIn).subtract(totalCashOut)
                .subtract(totalTransfersOut).add(totalTransfersIn)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal businessExpenses = cafeExpenseRepository.findByExpenseDateBetween(startDate, endDate).stream()
                .filter(e -> "PAID".equalsIgnoreCase(e.getStatus()))
                .map(CafeExpense::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netSales = totalSalesAll.subtract(totalRefundsAll).setScale(2, RoundingMode.HALF_UP);
        BigDecimal accountingProfit = netSales.subtract(businessExpenses).setScale(2, RoundingMode.HALF_UP);

        FinancialDistinctionsDTO distinctions = FinancialDistinctionsDTO.builder()
                .drawerCashFlow(drawerCashFlow)
                .bankSafeTransfers(totalTransfersOut.setScale(2, RoundingMode.HALF_UP))
                .businessExpenses(businessExpenses)
                .accountingProfit(accountingProfit)
                .explanation("Bank/safe deposits are physical custody transfers, NOT business expenses or revenue reductions. Drawer cash flow measures physical cash leaving and entering the register. Accounting profit measures net customer revenue minus cafe operating expenses.")
                .build();

        DrawerSummaryDTO summary = DrawerSummaryDTO.builder()
                .totalShifts(totalShifts)
                .openShifts(openShifts)
                .closedShifts(closedShifts)
                .totalOpeningFloat(totalFloat.setScale(2, RoundingMode.HALF_UP))
                .totalCashSales(totalCashSales.setScale(2, RoundingMode.HALF_UP))
                .totalUpiSales(totalUpiSales.setScale(2, RoundingMode.HALF_UP))
                .totalCardSales(totalCardSales.setScale(2, RoundingMode.HALF_UP))
                .totalSales(totalSalesAll)
                .totalCashRefunds(totalCashRefunds.setScale(2, RoundingMode.HALF_UP))
                .totalUpiRefunds(totalUpiRefunds.setScale(2, RoundingMode.HALF_UP))
                .totalCardRefunds(totalCardRefunds.setScale(2, RoundingMode.HALF_UP))
                .totalRefunds(totalRefundsAll)
                .totalCashAdded(totalCashIn.setScale(2, RoundingMode.HALF_UP))
                .totalCashPaidOut(totalCashOut.setScale(2, RoundingMode.HALF_UP))
                .totalTransfersToBankSafe(totalTransfersOut.setScale(2, RoundingMode.HALF_UP))
                .totalTransfersFromBankSafe(totalTransfersIn.setScale(2, RoundingMode.HALF_UP))
                .totalExpectedCash(totalExpected.setScale(2, RoundingMode.HALF_UP))
                .totalCountedCash(totalCounted.setScale(2, RoundingMode.HALF_UP))
                .netVariance(netVariance)
                .shortageCount(shortageCount)
                .totalShortageAmount(totalShortage.setScale(2, RoundingMode.HALF_UP))
                .surplusCount(surplusCount)
                .totalSurplusAmount(totalSurplus.setScale(2, RoundingMode.HALF_UP))
                .balancedCount(balancedCount)
                .anomalousShiftsCount(anomalousShiftsCount)
                .distinctions(distinctions)
                .build();

        AnomalyThresholdsDTO thresholdsDTO = AnomalyThresholdsDTO.builder()
                .varianceThreshold(varianceThreshold)
                .largeMovementThreshold(largeMovementThreshold)
                .flagMissingCount(flagMissingCount)
                .flagTransfersWithoutRef(flagTransfersWithoutRef)
                .build();

        return DrawerAnalysisDTO.builder()
                .period(periodParam != null ? periodParam : "CUSTOM")
                .startDate(startDate)
                .endDate(endDate)
                .filterCashierId(cashierId)
                .filterRegisterId(registerId)
                .filterStatus(status)
                .thresholds(thresholdsDTO)
                .summary(summary)
                .shifts(shiftItems)
                .dailySummaries(dailySummaries)
                .monthlySummaries(monthlySummaries)
                .build();
    }

    /**
     * One page of a drawer analysis table: "shifts" (optionally only the ones with anomaly flags), "daily" or "monthly".
     * The analysis for the period and filters is computed as usual and the chosen table is sliced.
     */
    public org.springframework.data.domain.Page<?> getDrawerAnalysisTable(String table, boolean anomaliesOnly, String periodParam, LocalDate customStart,
                                                                         LocalDate customEnd, Integer month, Integer year, Long cashierId,
                                                                         String registerId, String status, org.springframework.data.domain.Pageable pageable) {
        DrawerAnalysisDTO analysis = getDrawerAnalysis(periodParam, customStart, customEnd, month, year, cashierId, registerId, status);
        String t = table == null ? "shifts" : table.trim();
        switch (t) {
            case "daily":
                return com.neram.pos.util.PagedResponse.slice(analysis.getDailySummaries(), pageable);
            case "monthly":
                return com.neram.pos.util.PagedResponse.slice(analysis.getMonthlySummaries(), pageable);
            case "shifts": {
                java.util.List<DrawerAnalysisDTO.ShiftReconciliationItemDTO> shifts = analysis.getShifts();
                if (anomaliesOnly) {
                    shifts = shifts.stream().filter(s -> s.getAnomalyFlags() != null && !s.getAnomalyFlags().isEmpty()).collect(java.util.stream.Collectors.toList());
                }
                return com.neram.pos.util.PagedResponse.slice(shifts, pageable);
            }
            default:
                throw new IllegalArgumentException("Unknown drawer table: " + table);
        }
    }

    public byte[] exportDrawerAnalysisCsv(String periodParam, LocalDate customStart, LocalDate customEnd,
                                          Integer month, Integer year, Long cashierId, String registerId, String status) {
        DrawerAnalysisDTO analysis = getDrawerAnalysis(periodParam, customStart, customEnd, month, year, cashierId, registerId, status);
        StringBuilder sb = new StringBuilder();

        // CSV Header
        sb.append("Shift ID,Register,Cashier,Status,Opened At,Closed At,Duration (Mins),Opening Float,Cash Sales,UPI Sales,Card Sales,Total Sales,Cash Refunds,Cash In,Cash Out,Transfers to Bank/Safe,Transfers from Bank/Safe,Expected Cash,Counted Cash,Variance,Is Corrected,Corrected Count,Corrected Variance,Correction Reason,Anomaly Flags,Handover Ref,Notes\n");

        for (ShiftReconciliationItemDTO item : analysis.getShifts()) {
            CashierShiftDTO.ShiftResponse s = item.getShift();
            String anomalies = item.getAnomalyFlags().stream()
                    .map(a -> a.getFlagType() + ": " + a.getDescription())
                    .collect(Collectors.joining("; "));

            sb.append(s.getId()).append(",")
                    .append(escapeCsv(s.getRegisterId())).append(",")
                    .append(escapeCsv(s.getCashierName())).append(",")
                    .append(s.getStatus()).append(",")
                    .append(s.getOpenedAt() != null ? s.getOpenedAt().format(DATETIME_FMT) : "").append(",")
                    .append(s.getClosedAt() != null ? s.getClosedAt().format(DATETIME_FMT) : "").append(",")
                    .append(item.getDurationMinutes()).append(",")
                    .append(s.getOpeningFloat()).append(",")
                    .append(s.getCashSales()).append(",")
                    .append(s.getUpiSales()).append(",")
                    .append(s.getCardSales()).append(",")
                    .append(s.getTotalSales()).append(",")
                    .append(s.getCashRefunds()).append(",")
                    .append(s.getCashInTotal()).append(",")
                    .append(s.getCashOutTotal()).append(",")
                    .append(s.getTransfersOutTotal()).append(",")
                    .append(s.getTransfersInTotal()).append(",")
                    .append(s.getExpectedCash()).append(",")
                    .append(s.getCountedCash() != null ? s.getCountedCash() : "").append(",")
                    .append(s.getVariance()).append(",")
                    .append(Boolean.TRUE.equals(s.getIsCorrected())).append(",")
                    .append(s.getCorrectedCountedCash() != null ? s.getCorrectedCountedCash() : "").append(",")
                    .append(s.getCorrectedVariance() != null ? s.getCorrectedVariance() : "").append(",")
                    .append(escapeCsv(s.getCorrectionReason())).append(",")
                    .append(escapeCsv(anomalies)).append(",")
                    .append(escapeCsv(s.getCarryForwardRef())).append(",")
                    .append(escapeCsv(s.getNotes())).append("\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        String s = val.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
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

    public CashierShiftDTO.ShiftResponse toDTO(CashierShift shift) {
        BigDecimal expected = calculateExpectedCash(shift);
        BigDecimal variance = shift.getStatus() == CashierShift.ShiftStatus.CLOSED
                ? (shift.getVariance() != null ? shift.getVariance() : BigDecimal.ZERO)
                : BigDecimal.ZERO;

        BigDecimal totalSales = (shift.getCashSales() != null ? shift.getCashSales() : BigDecimal.ZERO)
                .add(shift.getUpiSales() != null ? shift.getUpiSales() : BigDecimal.ZERO)
                .add(shift.getCardSales() != null ? shift.getCardSales() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        List<CashierShiftDTO.CashMovementResponse> movementDTOs = movementRepository.findByShiftIdOrderByCreatedAtAsc(shift.getId()).stream()
                .map(this::toMovementDTO)
                .collect(Collectors.toList());

        return CashierShiftDTO.ShiftResponse.builder()
                .id(shift.getId())
                .registerId(shift.getRegisterId())
                .cashierId(shift.getCashierId())
                .cashierName(shift.getCashierName())
                .openedAt(shift.getOpenedAt())
                .openingFloat(shift.getOpeningFloat())
                .previousShiftId(shift.getPreviousShiftId())
                .carryForwardRef(shift.getCarryForwardRef())
                .isCorrected(shift.getIsCorrected())
                .correctedCountedCash(shift.getCorrectedCountedCash())
                .correctedVariance(shift.getCorrectedVariance())
                .correctionReason(shift.getCorrectionReason())
                .correctedByUserId(shift.getCorrectedByUserId())
                .correctedByUserName(shift.getCorrectedByUserName())
                .correctedAt(shift.getCorrectedAt())
                .status(shift.getStatus().name())
                .closedAt(shift.getClosedAt())
                .closingUserId(shift.getClosingUserId())
                .closingUserName(shift.getClosingUserName())
                .countedCash(shift.getCountedCash())
                .expectedCash(shift.getStatus() == CashierShift.ShiftStatus.CLOSED ? shift.getExpectedCash() : expected)
                .variance(variance)
                .notes(shift.getNotes())
                .cashSales(shift.getCashSales())
                .upiSales(shift.getUpiSales())
                .cardSales(shift.getCardSales())
                .totalSales(totalSales)
                .cashRefunds(shift.getCashRefunds())
                .upiRefunds(shift.getUpiRefunds())
                .cardRefunds(shift.getCardRefunds())
                .cashInTotal(shift.getCashInTotal())
                .cashOutTotal(shift.getCashOutTotal())
                .transfersOutTotal(shift.getTransfersOutTotal())
                .transfersInTotal(shift.getTransfersInTotal())
                .movements(movementDTOs)
                .build();
    }

    private CashierShiftDTO.CashMovementResponse toMovementDTO(CashMovement m) {
        return CashierShiftDTO.CashMovementResponse.builder()
                .id(m.getId())
                .shiftId(m.getShiftId())
                .movementType(m.getMovementType().name())
                .transferType(m.getTransferType())
                .amount(m.getAmount())
                .destinationAccount(m.getDestinationAccount())
                .referenceNumber(m.getReferenceNumber())
                .reason(m.getReason())
                .userId(m.getUserId())
                .userName(m.getUserName())
                .approvedBy(m.getApprovedBy())
                .createdAt(m.getCreatedAt())
                .build();
    }

    public CashierShiftDTO.ShiftSettingsDTO getSettingsDTO() {
        ShiftSetting s = shiftSettingRepository.findById(1L).orElseGet(() -> {
            ShiftSetting init = ShiftSetting.builder()
                    .id(1L)
                    .enabled(true)
                    .updatedAt(LocalDateTime.now())
                    .build();
            return shiftSettingRepository.save(init);
        });
        return CashierShiftDTO.ShiftSettingsDTO.builder()
                .enabled(Boolean.TRUE.equals(s.getEnabled()))
                .updatedAt(s.getUpdatedAt())
                .updatedBy(s.getUpdatedBy())
                .updatedByName(s.getUpdatedByName())
                .varianceThreshold(s.getVarianceThreshold())
                .largeMovementThreshold(s.getLargeMovementThreshold())
                .flagMissingCount(s.getFlagMissingCount())
                .flagTransfersWithoutRef(s.getFlagTransfersWithoutRef())
                .build();
    }

    @Transactional
    public CashierShiftDTO.ShiftSettingsDTO updateSettings(CashierShiftDTO.ShiftSettingsDTO req) {
        ShiftSetting s = shiftSettingRepository.findById(1L).orElseGet(() ->
                ShiftSetting.builder().id(1L).enabled(true).build()
        );
        if (req.getEnabled() != null) {
            s.setEnabled(req.getEnabled());
        }
        if (req.getVarianceThreshold() != null && req.getVarianceThreshold().compareTo(BigDecimal.ZERO) >= 0) {
            s.setVarianceThreshold(req.getVarianceThreshold().setScale(2, RoundingMode.HALF_UP));
        }
        if (req.getLargeMovementThreshold() != null && req.getLargeMovementThreshold().compareTo(BigDecimal.ZERO) >= 0) {
            s.setLargeMovementThreshold(req.getLargeMovementThreshold().setScale(2, RoundingMode.HALF_UP));
        }
        if (req.getFlagMissingCount() != null) {
            s.setFlagMissingCount(req.getFlagMissingCount());
        }
        if (req.getFlagTransfersWithoutRef() != null) {
            s.setFlagTransfersWithoutRef(req.getFlagTransfersWithoutRef());
        }

        s.setUpdatedAt(LocalDateTime.now());
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            s.setUpdatedBy(currentUser.getId());
            s.setUpdatedByName(currentUser.getUsername());
        }
        ShiftSetting saved = shiftSettingRepository.save(s);
        auditLogService.log("UPDATE_SHIFT_SETTINGS", "SETTING", 1L, null,
                "Shift settings updated by " + (currentUser != null ? currentUser.getUsername() : "Admin"));
        return getSettingsDTO();
    }

    @Transactional
    public CashierShiftDTO.ShiftSettingsDTO updateSettings(Boolean enabled) {
        CashierShiftDTO.ShiftSettingsDTO dto = new CashierShiftDTO.ShiftSettingsDTO();
        dto.setEnabled(enabled);
        return updateSettings(dto);
    }

    public boolean isShiftModuleEnabled() {
        return shiftSettingRepository.findById(1L)
                .map(s -> Boolean.TRUE.equals(s.getEnabled()))
                .orElse(true);
    }
}
