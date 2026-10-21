package com.neram.pos.service;

import com.neram.pos.dto.BillItemResponseDTO;
import com.neram.pos.dto.BillRequestDTO;
import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.BillRefund;
import com.neram.pos.entity.User;
import com.neram.pos.repository.BillItemRepository;
import com.neram.pos.repository.BillRefundRepository;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.DiningTableRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final DiningTableRepository diningTableRepository;
    private final UserRepository userRepository;
    private final com.neram.pos.repository.MenuItemRepository menuItemRepository;
    private final AuditLogService auditLogService;
    private final GstSettingService gstSettingService;
    private final CashierShiftService cashierShiftService;
    private final RecipeService recipeService;
    private final BillRefundRepository billRefundRepository;
    private final PromotionService promotionService;
    private final AppSettingService appSettingService;
    @Lazy
    private final ThermalPrinterService thermalPrinterService;

    public List<BillResponseDTO> getAllBills() {
        return billRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** Bounded text search (bill number / customer / outdoor token); avoids shipping the entire bill history to the client. */
    public List<BillResponseDTO> searchBills(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return billRepository.searchByText(q, org.springframework.data.domain.PageRequest.of(0, 20))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BillResponseDTO> getDailyBills(LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        LocalDateTime start = queryDate.atStartOfDay();
        LocalDateTime end = queryDate.atTime(LocalTime.MAX);
        return billRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BillResponseDTO> getMonthlyBills(int year, int month) {
        LocalDate startMonth = LocalDate.of(year, month, 1);
        LocalDate endMonth = startMonth.withDayOfMonth(startMonth.lengthOfMonth());
        LocalDateTime start = startMonth.atStartOfDay();
        LocalDateTime end = endMonth.atTime(LocalTime.MAX);
        return billRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public BillResponseDTO getBillById(Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));
        return toDTO(bill);
    }

    public BillResponseDTO getBillByNumber(String billNumber) {
        if (billNumber == null || billNumber.trim().isEmpty()) return null;
        return billRepository.findByBillNumber(billNumber.trim())
                .map(this::toDTO)
                .orElse(null);
    }

    public String getLocationDisplay(Bill b) {
        if (b.getOrderType() == Bill.OrderType.OUTDOOR) {
            String token = b.getOutdoorToken() != null ? b.getOutdoorToken() : "Outdoor";
            String cust = b.getCustomerName() != null ? b.getCustomerName() : "Customer";
            return "Outdoor (" + cust + " • " + token + ")";
        } else if (b.getOrderType() == Bill.OrderType.TAKEAWAY) {
            String token = b.getTakeawayToken() != null ? b.getTakeawayToken() : "TAK";
            String cust = b.getCustomerName() != null ? b.getCustomerName() : "Guest";
            return "Takeaway (" + cust + " • " + token + ")";
        }
        return "Table " + (b.getTableNumber() != null ? b.getTableNumber() : "Takeaway");
    }

    @Transactional
    public synchronized String generateOutdoorToken() {
        Integer maxNum = billRepository.findMaxOutdoorTokenNumber();
        int next = (maxNum != null && maxNum > 0) ? maxNum + 1 : 1;
        String candidate = String.format("OUT-%03d", next);
        while (billRepository.existsByOutdoorToken(candidate)) {
            next++;
            candidate = String.format("OUT-%03d", next);
        }
        return candidate;
    }

    public String getNextOutdoorToken() {
        Integer maxNum = billRepository.findMaxOutdoorTokenNumber();
        int next = (maxNum != null && maxNum > 0) ? maxNum + 1 : 1;
        String candidate = String.format("OUT-%03d", next);
        while (billRepository.existsByOutdoorToken(candidate)) {
            next++;
            candidate = String.format("OUT-%03d", next);
        }
        return candidate;
    }

    @Transactional
    public synchronized String generateTakeawayToken() {
        Integer maxNum = billRepository.findMaxTakeawayTokenNumber();
        int next = (maxNum != null && maxNum > 0) ? maxNum + 1 : 1;
        String candidate = String.format("TAK-%03d", next);
        while (billRepository.existsByTakeawayToken(candidate)) {
            next++;
            candidate = String.format("TAK-%03d", next);
        }
        return candidate;
    }

    public String getNextTakeawayToken() {
        Integer maxNum = billRepository.findMaxTakeawayTokenNumber();
        int next = (maxNum != null && maxNum > 0) ? maxNum + 1 : 1;
        String candidate = String.format("TAK-%03d", next);
        while (billRepository.existsByTakeawayToken(candidate)) {
            next++;
            candidate = String.format("TAK-%03d", next);
        }
        return candidate;
    }

    @Transactional
    public BillResponseDTO createBill(BillRequestDTO dto) {
        Bill bill = new Bill();
        String billNumber = generateBillNumber();
        bill.setBillNumber(billNumber);

        String oType = dto.getOrderType() != null ? dto.getOrderType().toUpperCase() : null;
        if ("OUTDOOR".equals(oType)) {
            if (dto.getCustomerName() == null || dto.getCustomerName().trim().isBlank()) {
                throw new IllegalArgumentException("Customer name is required for outdoor orders.");
            }
            bill.setOrderType(Bill.OrderType.OUTDOOR);
            bill.setTableNumber(null);
            bill.setCustomerName(dto.getCustomerName().trim());
            bill.setOutdoorToken(dto.getOutdoorToken() != null && !dto.getOutdoorToken().isBlank() ? dto.getOutdoorToken().trim() : generateOutdoorToken());
        } else if ("TAKEAWAY".equals(oType) || (dto.getTableNumber() != null && dto.getTableNumber().equalsIgnoreCase("Takeaway"))) {
            bill.setOrderType(Bill.OrderType.TAKEAWAY);
            bill.setTableNumber(null);
            bill.setCustomerName(dto.getCustomerName() != null && !dto.getCustomerName().trim().isBlank() ? dto.getCustomerName().trim() : "Takeaway Guest");
            bill.setTakeawayToken(dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank() ? dto.getTakeawayToken().trim() : generateTakeawayToken());
            bill.setOutdoorToken(null);
        } else {
            bill.setOrderType(Bill.OrderType.TABLE);
            if (dto.getTableNumber() != null && !dto.getTableNumber().trim().isBlank()) {
                bill.setTableNumber(dto.getTableNumber().trim());
            } else if ("TABLE".equals(oType)) {
                throw new IllegalArgumentException("Please select a table.");
            } else {
                bill.setTableNumber(null);
            }
            bill.setCustomerName(dto.getCustomerName());
            bill.setOutdoorToken(null);
            bill.setTakeawayToken(null);
        }

        Bill.PaymentMode mode = Bill.PaymentMode.CASH;
        if (dto.getPaymentMode() != null) {
            try {
                mode = Bill.PaymentMode.valueOf(dto.getPaymentMode().toUpperCase());
            } catch (Exception ignored) {}
        }
        bill.setPaymentMode(mode);
        bill.setStatus(Bill.Status.PAID);
        bill.setNotes(dto.getNotes());
        bill.setCreatedAt(LocalDateTime.now());

        // Get current user id
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> bill.setCreatedBy(u.getId()));
            }
        } catch (Exception ignored) {}

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new IllegalArgumentException("Bill must contain at least one item");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        List<BillItem> items = new ArrayList<>();
        if (dto.getItems() != null) {
            for (BillRequestDTO.BillItemRequestDTO itemDto : dto.getItems()) {
                BillItem item = new BillItem();
                item.setBill(bill);
                item.setMenuItemId(itemDto.getMenuItemId());
                item.setItemName(itemDto.getItemName());
                item.setQuantity(itemDto.getQuantity() != null ? itemDto.getQuantity() : 1);
                item.setCustomization(itemDto.getCustomization());
                item.setIsTakeaway(Boolean.TRUE.equals(itemDto.getIsTakeaway()) || "TAKEAWAY".equals(oType));
                BigDecimal price = resolveLinePrice(itemDto.getMenuItemId(), item.getQuantity(), itemDto.getPrice(), true);
                item.setPrice(price);
                BigDecimal lineSubtotal = price.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                item.setSubtotal(lineSubtotal);
                subtotal = subtotal.add(lineSubtotal);
                items.add(item);
            }
        }
        bill.setItems(items);
        bill.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));

        if (!isManagerOrAdmin()) {
            if (dto.getDiscountPercent() != null && Math.abs(dto.getDiscountPercent()) > 0.0001) {
                throw new AccessDeniedException("Staff users cannot set or change discounts. Only Manager and Admin users are permitted.");
            }
            if (dto.getGstEnabled() != null || dto.getTaxPercent() != null || dto.getCgstPercent() != null || dto.getSgstPercent() != null) {
                throw new AccessDeniedException("Staff users cannot set or change GST. Only Manager and Admin users are permitted.");
            }
        }

        computeBillAmounts(bill, dto.getDiscountPercent(), dto.getTaxPercent(), dto.getGstEnabled(), dto.getCgstPercent(), dto.getSgstPercent(), dto.getParcelCharge());

        Bill saved = billRepository.save(bill);
        if (saved.getStatus() == Bill.Status.PAID) {
            cashierShiftService.recordBillPayment(saved);
            saved = billRepository.save(saved);
            recipeService.deductIngredientsForOrder(saved);
        }

        String createDetails = String.format("Created Bill #%s | %s | Net: ₹%.2f | Mode: %s | Items: %d",
                saved.getBillNumber(),
                getLocationDisplay(saved),
                saved.getNetAmount(),
                saved.getPaymentMode(),
                saved.getItems() != null ? saved.getItems().size() : 0);
        auditLogService.log("CREATE_BILL", "BILL", saved.getId(), null, createDetails);
        return toDTO(saved);
    }

    @Transactional
    public BillResponseDTO updateBill(Long id, BillRequestDTO dto) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));

        // Fully refunded bills cannot be edited
        if (bill.getStatus() == Bill.Status.REFUNDED) {
            throw new IllegalStateException("Fully refunded bills cannot be edited.");
        }

        String oldItemsStr = bill.getItems() != null ? bill.getItems().stream()
                .map(i -> String.format("%s x%d (₹%s)", i.getItemName(), i.getQuantity(), i.getSubtotal()))
                .collect(Collectors.joining(", ")) : "";
        BigDecimal oldSubtotal = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;
        double oldDiscountPct = bill.getDiscountPercent() != null ? bill.getDiscountPercent() : 0.0;
        double oldTaxPct = bill.getTaxPercent() != null ? bill.getTaxPercent() : 0.0;
        BigDecimal oldNet = bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO;
        String oldTable = bill.getTableNumber() != null ? bill.getTableNumber() : "Takeaway";
        String oldMode = bill.getPaymentMode() != null ? bill.getPaymentMode().name() : "CASH";

        String oldVal = String.format("Table: %s, Mode: %s, Discount: %.1f%%, Tax: %.1f%%, Items: [%s], Net: ₹%s",
                oldTable, oldMode, oldDiscountPct, oldTaxPct, oldItemsStr, oldNet);

        if (dto.getOrderType() != null) {
            String targetType = dto.getOrderType().toUpperCase();
            if ("OUTDOOR".equals(targetType)) {
                String cName = dto.getCustomerName() != null ? dto.getCustomerName().trim() : (bill.getCustomerName() != null ? bill.getCustomerName().trim() : "");
                if (cName.isBlank()) throw new IllegalArgumentException("Customer name is required for outdoor orders.");
                bill.setOrderType(Bill.OrderType.OUTDOOR);
                bill.setCustomerName(cName);
                bill.setTableNumber(null);
                if (bill.getOutdoorToken() == null) {
                    bill.setOutdoorToken(generateOutdoorToken());
                }
            } else if ("TAKEAWAY".equals(targetType)) {
                bill.setOrderType(Bill.OrderType.TAKEAWAY);
                bill.setTableNumber(null);
                bill.setOutdoorToken(null);
                if (bill.getTakeawayToken() == null) {
                    bill.setTakeawayToken(dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank() ? dto.getTakeawayToken().trim() : generateTakeawayToken());
                }
                if (dto.getCustomerName() != null) bill.setCustomerName(dto.getCustomerName().trim());
            } else if ("TABLE".equals(targetType)) {
                bill.setOrderType(Bill.OrderType.TABLE);
                if (dto.getTableNumber() != null && !dto.getTableNumber().isBlank()) {
                    bill.setTableNumber(dto.getTableNumber().trim());
                }
                bill.setOutdoorToken(null);
                bill.setTakeawayToken(null);
                if (dto.getCustomerName() != null) bill.setCustomerName(dto.getCustomerName());
            }
        } else {
            if (dto.getTableNumber() != null) bill.setTableNumber(dto.getTableNumber());
            if (dto.getCustomerName() != null) bill.setCustomerName(dto.getCustomerName());
        }
        if (dto.getPaymentMode() != null) {
            try {
                bill.setPaymentMode(Bill.PaymentMode.valueOf(dto.getPaymentMode().toUpperCase()));
            } catch (Exception ignored) {}
        }
        if (dto.getNotes() != null) bill.setNotes(dto.getNotes());

        // Process updated items if provided
        if (dto.getItems() != null) {
            if (dto.getItems().isEmpty()) {
                throw new IllegalArgumentException("Bill must contain at least one item");
            }
            bill.getItems().clear();
            BigDecimal newSubtotal = BigDecimal.ZERO;
            for (BillRequestDTO.BillItemRequestDTO itemDto : dto.getItems()) {
                if (itemDto.getQuantity() == null || itemDto.getQuantity() <= 0) continue;
                BillItem item = new BillItem();
                item.setBill(bill);
                item.setMenuItemId(itemDto.getMenuItemId());
                item.setItemName(itemDto.getItemName());
                item.setQuantity(itemDto.getQuantity());
                item.setCustomization(itemDto.getCustomization());
                item.setIsTakeaway(Boolean.TRUE.equals(itemDto.getIsTakeaway()) || bill.getOrderType() == Bill.OrderType.TAKEAWAY);
                if (itemDto.getPrice() != null && itemDto.getPrice().signum() < 0) {
                    throw new IllegalArgumentException("Item price cannot be negative");
                }
                BigDecimal price = itemDto.getPrice() != null ? itemDto.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                item.setPrice(price);
                BigDecimal lineSubtotal = price.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                item.setSubtotal(lineSubtotal);
                item.setPrintedQuantity(item.getQuantity());
                newSubtotal = newSubtotal.add(lineSubtotal);
                bill.getItems().add(item);
            }
            if (bill.getItems().isEmpty()) {
                throw new IllegalArgumentException("Bill must contain at least one item with valid quantity");
            }
            bill.setSubtotal(newSubtotal.setScale(2, RoundingMode.HALF_UP));
        }

        if (!isManagerOrAdmin()) {
            if (dto.getDiscountPercent() != null && (bill.getDiscountPercent() == null ? Math.abs(dto.getDiscountPercent()) > 0.0001 : !Objects.equals(dto.getDiscountPercent(), bill.getDiscountPercent()))) {
                throw new AccessDeniedException("Staff users cannot set or change discounts. Only Manager and Admin users are permitted.");
            }
            if (dto.getGstEnabled() != null || dto.getTaxPercent() != null || dto.getCgstPercent() != null || dto.getSgstPercent() != null) {
                throw new AccessDeniedException("Staff users cannot set or change GST. Only Manager and Admin users are permitted.");
            }
        }

        // Recalculate discount, taxes, parcel charge and net amount
        computeBillAmounts(bill, dto.getDiscountPercent(), dto.getTaxPercent(), dto.getGstEnabled(), dto.getCgstPercent(), dto.getSgstPercent(), dto.getParcelCharge());

        Bill saved = billRepository.save(bill);

        if (saved.getStatus() == Bill.Status.PAID && saved.getShiftId() != null) {
            Bill.PaymentMode oldPaymentMode = null;
            try {
                oldPaymentMode = Bill.PaymentMode.valueOf(oldMode);
            } catch (Exception ignored) {}
            cashierShiftService.recordBillAdjustment(saved, oldPaymentMode, oldNet);
        }

        String newItemsStr = saved.getItems() != null ? saved.getItems().stream()
                .map(i -> String.format("%s x%d (₹%s)", i.getItemName(), i.getQuantity(), i.getSubtotal()))
                .collect(Collectors.joining(", ")) : "";
        BigDecimal netDiff = saved.getNetAmount().subtract(oldNet).setScale(2, RoundingMode.HALF_UP);
        String diffStr = (netDiff.compareTo(BigDecimal.ZERO) >= 0 ? "+₹" : "-₹") + netDiff.abs();
        String reasonStr = (saved.getNotes() != null && !saved.getNotes().trim().isEmpty()) ? saved.getNotes().trim() : "Bill updated";

        String newVal = String.format("Updated Bill #%s -> Table: %s, Mode: %s, Discount: %.1f%%, Tax: %.1f%%, Items: [%s], Net: ₹%s (Diff: %s), Reason: %s",
                saved.getBillNumber(),
                saved.getTableNumber() != null ? saved.getTableNumber() : "Takeaway",
                saved.getPaymentMode(),
                saved.getDiscountPercent() != null ? saved.getDiscountPercent() : 0.0,
                saved.getTaxPercent() != null ? saved.getTaxPercent() : 0.0,
                newItemsStr,
                saved.getNetAmount(),
                diffStr,
                reasonStr);

        auditLogService.log("EDIT_BILL", "BILL", saved.getId(), oldVal, newVal);
        return toDTO(saved);
    }

    @Transactional
    public void deleteBill(Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));

        // Enforce rule: Paid bills cannot be deleted outright
        if (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.REFUNDED || bill.getStatus() == Bill.Status.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Paid bills cannot be deleted. Use refunds or adjustments instead.");
        }

        String billInfo = String.format("Bill #%s | Table: %s | Net: ₹%s | Status: %s",
                bill.getBillNumber(),
                bill.getTableNumber() != null ? bill.getTableNumber() : "Takeaway",
                bill.getNetAmount(),
                bill.getStatus());
        billRepository.delete(bill);
        auditLogService.log("DELETE_BILL", "BILL", id, billInfo, "Deleted bill #" + bill.getBillNumber());
    }

    @Transactional
    public BillResponseDTO markAsPrinted(Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));
        bill.setPrintedAt(LocalDateTime.now());
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> bill.setPrintedBy(u.getId()));
            }
        } catch (Exception ignored) {}
        Bill saved = billRepository.save(bill);
        BillResponseDTO dto = toDTO(saved);
        auditLogService.log("PRINT_BILL", "BILL", saved.getId(), null,
                String.format("Printed Bill #%s | Table: %s | Net: ₹%s | Order Staff: %s | Cashier: %s",
                        saved.getBillNumber(), saved.getTableNumber(), saved.getNetAmount(), dto.getCreatedByName(), dto.getPrintedByName() != null ? dto.getPrintedByName() : "Cashier"));
        return dto;
    }

    @Transactional
    public BillResponseDTO cancelBill(Long id) {
        Bill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Order/Bill not found with id: " + id));

        if (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.REFUNDED || bill.getStatus() == Bill.Status.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Paid bills cannot be cancelled or voided. Use the refund workflow instead.");
        }
        if (bill.getStatus() == Bill.Status.CANCELLED || bill.getStatus() == Bill.Status.VOIDED) {
            throw new IllegalStateException("Order is already cancelled or voided.");
        }

        User currentUser = getCurrentUser();
        String prevStatus = bill.getStatus().name();

        bill.setStatus(Bill.Status.CANCELLED);
        bill.setVoidedAt(LocalDateTime.now());
        if (currentUser != null) {
            bill.setVoidedBy(currentUser.getId());
            bill.setVoidedByName(currentUser.getUsername());
        }
        bill.setVoidReason("Cancelled by user/staff");
        if (bill.getKotPrintedAt() != null) {
            bill.setKotCancelledAt(LocalDateTime.now());
        }

        recipeService.handleOrderCancellation(bill, false, "Cancelled by user/staff");

        Bill saved = billRepository.save(bill);

        // If KOT was already sent to kitchen, automatically print/enqueue cancellation notice
        if (saved.getKotPrintedAt() != null) {
            try {
                String idemKey = "KOT_CANCEL_" + saved.getId();
                thermalPrinterService.printCancelledKot(saved.getId(), saved.getVoidReason(), idemKey);
            } catch (Exception e) {
                log.warn("Failed to automatically print cancelled KOT for bill #{}: {}", saved.getId(), e.getMessage());
            }
        }

        auditLogService.log("CANCEL_BILL", "BILL", saved.getId(), "Status: " + prevStatus,
                String.format("Cancelled order #%s by %s",
                        saved.getBillNumber(),
                        currentUser != null ? currentUser.getUsername() : "Staff"));
        return toDTO(saved);
    }

    @Transactional
    public BillResponseDTO voidBill(Long id, String reason, Boolean foodWasPrepared) {
        Bill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Order/Bill not found with id: " + id));

        if (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.REFUNDED || bill.getStatus() == Bill.Status.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Paid bills cannot be cancelled or voided. Use the refund workflow instead.");
        }
        if (bill.getStatus() == Bill.Status.CANCELLED || bill.getStatus() == Bill.Status.VOIDED) {
            throw new IllegalStateException("Order is already cancelled or voided.");
        }

        String voidReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Order voided";
        User currentUser = getCurrentUser();
        String prevStatus = bill.getStatus().name();

        bill.setStatus(Bill.Status.VOIDED);
        bill.setVoidedAt(LocalDateTime.now());
        if (currentUser != null) {
            bill.setVoidedBy(currentUser.getId());
            bill.setVoidedByName(currentUser.getUsername());
        }
        bill.setVoidReason(voidReason);
        if (bill.getKotPrintedAt() != null) {
            bill.setKotCancelledAt(LocalDateTime.now());
        }

        // Adjust recipe inventory consumption:
        // If not prepared: restores usable stock via REFUND_REVERSAL.
        // If prepared: records WASTE; does not restore stock.
        boolean wasPrepared = Boolean.TRUE.equals(foodWasPrepared);
        recipeService.handleOrderCancellation(bill, wasPrepared, voidReason);

        Bill saved = billRepository.save(bill);

        // If KOT was already sent to kitchen, automatically print/enqueue cancellation notice
        if (saved.getKotPrintedAt() != null) {
            try {
                String idemKey = "KOT_CANCEL_" + saved.getId();
                thermalPrinterService.printCancelledKot(saved.getId(), voidReason, idemKey);
            } catch (Exception e) {
                log.warn("Failed to automatically print cancelled KOT for bill #{}: {}", saved.getId(), e.getMessage());
            }
        }

        auditLogService.log("VOID_ORDER", "BILL", saved.getId(), "Status: " + prevStatus,
                String.format("Voided order #%s (Net: ₹%s) by %s | Reason: %s | Food Prepared: %s",
                        saved.getBillNumber(), saved.getNetAmount(),
                        currentUser != null ? currentUser.getUsername() : "Staff",
                        voidReason, wasPrepared));
        return toDTO(saved);
    }


    @Transactional
    public BillResponseDTO saveOrUpdateTableOrder(com.neram.pos.dto.TableOrderRequestDTO dto) {
        String reqOrderType = dto.getOrderType() != null ? dto.getOrderType().toUpperCase() : null;
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);

        Bill bill;

        if ("OUTDOOR".equals(reqOrderType)) {
            // OUTDOOR Order validation
            if (dto.getCustomerName() == null || dto.getCustomerName().trim().isBlank()) {
                throw new IllegalArgumentException("Customer name is required for outdoor orders.");
            }
            String custName = dto.getCustomerName().trim();
            if (dto.getOrderId() == null && dto.getOutdoorToken() != null && !dto.getOutdoorToken().isBlank()) {
                billRepository.lockOrderKey("OUTDOOR:" + dto.getOutdoorToken().trim());
            }

            // Check if existing active outdoor order by orderId or outdoorToken
            Optional<Bill> existing = Optional.empty();
            if (dto.getOrderId() != null) {
                existing = billRepository.findByIdAndStatusIn(dto.getOrderId(), activeStatuses);
            }
            if (existing.isEmpty() && dto.getOutdoorToken() != null && !dto.getOutdoorToken().isBlank()) {
                existing = billRepository.findFirstByOutdoorTokenAndStatusInOrderByCreatedAtDesc(dto.getOutdoorToken().trim(), activeStatuses);
            }

            if (existing.isPresent()) {
                bill = existing.get();
                bill.setCustomerName(custName);
                if (dto.getNotes() != null) bill.setNotes(dto.getNotes());
            } else {
                bill = new Bill();
                String bNo = generateBillNumber();
                bill.setBillNumber(bNo);
                bill.setKotNumber(bNo);
                bill.setOrderType(Bill.OrderType.OUTDOOR);
                bill.setTableNumber(null);
                bill.setCustomerName(custName);
                bill.setOutdoorToken(generateOutdoorToken());
                bill.setStatus(Bill.Status.ACTIVE);
                bill.setPaymentMode(Bill.PaymentMode.CASH);
                bill.setCreatedAt(LocalDateTime.now());
                if (dto.getNotes() != null) bill.setNotes(dto.getNotes());
            }
        } else if ("TAKEAWAY".equals(reqOrderType) || (dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank())) {
            // TAKEAWAY Order
            String custName = (dto.getCustomerName() != null && !dto.getCustomerName().trim().isBlank())
                    ? dto.getCustomerName().trim() : "Takeaway Guest";

            if (dto.getOrderId() == null && dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank()) {
                billRepository.lockOrderKey("TAKEAWAY:" + dto.getTakeawayToken().trim());
            }

            // Check if existing active takeaway order by orderId or takeawayToken
            Optional<Bill> existing = Optional.empty();
            if (dto.getOrderId() != null) {
                existing = billRepository.findByIdAndStatusIn(dto.getOrderId(), activeStatuses);
            }
            if (existing.isEmpty() && dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank()) {
                existing = billRepository.findFirstByTakeawayTokenAndStatusInOrderByCreatedAtDesc(dto.getTakeawayToken().trim(), activeStatuses);
            }

            if (existing.isPresent()) {
                bill = existing.get();
                bill.setCustomerName(custName);
                if (dto.getNotes() != null) bill.setNotes(dto.getNotes());
            } else {
                bill = new Bill();
                String bNo = generateBillNumber();
                bill.setBillNumber(bNo);
                bill.setKotNumber(bNo);
                bill.setOrderType(Bill.OrderType.TAKEAWAY);
                bill.setTableNumber(null);
                bill.setCustomerName(custName);
                bill.setTakeawayToken(dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank() ? dto.getTakeawayToken().trim() : generateTakeawayToken());
                bill.setOutdoorToken(null);
                bill.setStatus(Bill.Status.ACTIVE);
                bill.setPaymentMode(Bill.PaymentMode.CASH);
                bill.setCreatedAt(LocalDateTime.now());
                if (dto.getNotes() != null) bill.setNotes(dto.getNotes());
            }
        } else {
            // TABLE or legacy TAKEAWAY Order
            String tableNo = (dto.getTableNumber() != null && !dto.getTableNumber().isBlank()) ? dto.getTableNumber().trim() : null;
            if (tableNo == null || tableNo.isBlank()) {
                throw new IllegalArgumentException("Please select a table.");
            }
            if (!tableNo.equalsIgnoreCase("Takeaway") && !diningTableRepository.existsByNameIgnoreCaseAndActiveTrue(tableNo)) {
                throw new IllegalArgumentException("Invalid table selected: " + tableNo);
            }

            diningTableRepository.lockActiveByNameIgnoreCase(tableNo);

            bill = billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(tableNo, activeStatuses)
                    .orElseGet(() -> {
                        Bill newBill = new Bill();
                        String bNo = generateBillNumber();
                        newBill.setBillNumber(bNo);
                        newBill.setKotNumber(bNo);
                        newBill.setTableNumber(tableNo);
                        newBill.setOrderType(tableNo.equalsIgnoreCase("Takeaway") ? Bill.OrderType.TAKEAWAY : Bill.OrderType.TABLE);
                        newBill.setOutdoorToken(null);
                        newBill.setStatus(Bill.Status.ACTIVE);
                        newBill.setPaymentMode(Bill.PaymentMode.CASH);
                        newBill.setCreatedAt(LocalDateTime.now());
                        return newBill;
                    });

            if (dto.getCustomerName() != null) bill.setCustomerName(dto.getCustomerName().trim());
            if (dto.getNotes() != null) bill.setNotes(dto.getNotes());
        }

        boolean isManagerOrAdmin = isManagerOrAdmin();
        if (!isManagerOrAdmin) {
            if (dto.getDiscountPercent() != null) {
                if (bill.getId() == null && Math.abs(dto.getDiscountPercent()) > 0.0001) {
                    throw new AccessDeniedException("Staff users cannot set or change discounts. Only Manager and Admin users are permitted.");
                } else if (bill.getId() != null && (bill.getDiscountPercent() == null ? Math.abs(dto.getDiscountPercent()) > 0.0001 : !Objects.equals(dto.getDiscountPercent(), bill.getDiscountPercent()))) {
                    throw new AccessDeniedException("Staff users cannot set or change discounts. Only Manager and Admin users are permitted.");
                }
            }
            if (dto.getGstEnabled() != null || dto.getTaxPercent() != null || dto.getCgstPercent() != null || dto.getSgstPercent() != null) {
                throw new AccessDeniedException("Staff users cannot set or change GST. Only Manager and Admin users are permitted.");
            }
        }

        // Always update createdBy to the user currently adding/modifying the order
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> bill.setCreatedBy(u.getId()));
            }
        } catch (Exception ignored) {}

        // If bill was already printed, it is awaiting payment confirmation - prevent modifying items
        if (bill.getStatus() == Bill.Status.BILL_PRINTED) {
            throw new IllegalStateException("Bill has already been printed and is awaiting payment confirmation. Manager must confirm payment before placing a new order on this table.");
        }

        // Map existing printed quantities and snapshot old items if KOT was already printed
        boolean wasKotPrinted = (bill.getKotPrintedAt() != null);
        java.util.Map<String, Integer> printedQtyMap = new java.util.HashMap<>();
        java.util.Map<String, String> oldItemNames = new java.util.HashMap<>();
        java.util.Map<String, Integer> oldItemQuantities = new java.util.HashMap<>();
        java.util.Map<String, String> oldItemNotes = new java.util.HashMap<>();

        if (bill.getItems() != null) {
            // Defensive fallback for legacy rows: a KOT was printed but no line carries a printed quantity.
            boolean noPrintedQuantityRecorded = bill.getItems().stream()
                    .noneMatch(i -> i.getPrintedQuantity() != null && i.getPrintedQuantity() > 0);
            for (BillItem oldItem : bill.getItems()) {
                String key = (oldItem.getMenuItemId() != null ? "ID_" + oldItem.getMenuItemId() : "NAME_" + oldItem.getItemName()) + "_" + Boolean.TRUE.equals(oldItem.getIsTakeaway());
                int pQty = oldItem.getPrintedQuantity() != null ? oldItem.getPrintedQuantity() : 0;
                printedQtyMap.put(key, pQty);
                if (wasKotPrinted) {
                    String parcelSuffix = Boolean.TRUE.equals(oldItem.getIsTakeaway()) ? " [PARCEL]" : "";
                    oldItemNames.put(key, oldItem.getItemName() + parcelSuffix);
                    // Baseline is exactly what the kitchen has already received (printed quantity, 0 if never printed).
                    // Dishes saved after the last KOT but not yet sent therefore stay in the change list until they are
                    // printed, instead of being forgotten by the next save.
                    int baselineQty = noPrintedQuantityRecorded
                            ? (oldItem.getQuantity() != null ? oldItem.getQuantity() : 0)
                            : pQty;
                    oldItemQuantities.put(key, baselineQty);
                    oldItemNotes.put(key, oldItem.getCustomization() != null ? oldItem.getCustomization().trim() : "");
                }
            }
            bill.getItems().clear();
        } else {
            bill.setItems(new ArrayList<>());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        if (dto.getItems() != null) {
            for (com.neram.pos.dto.TableOrderRequestDTO.OrderItemDTO itemDto : dto.getItems()) {
                BillItem item = new BillItem();
                item.setBill(bill);
                item.setMenuItemId(itemDto.getMenuItemId());
                item.setItemName(itemDto.getItemName());
                int qty = itemDto.getQuantity() != null ? itemDto.getQuantity() : 1;
                item.setQuantity(qty);
                item.setCustomization(itemDto.getCustomization());
                item.setIsTakeaway(Boolean.TRUE.equals(itemDto.getIsTakeaway()) || "TAKEAWAY".equals(reqOrderType) || (bill.getOrderType() == Bill.OrderType.TAKEAWAY));

                String key = (itemDto.getMenuItemId() != null ? "ID_" + itemDto.getMenuItemId() : "NAME_" + itemDto.getItemName()) + "_" + Boolean.TRUE.equals(item.getIsTakeaway());
                int prevPrinted = printedQtyMap.getOrDefault(key, 0);
                // The printed quantity cannot exceed current quantity
                item.setPrintedQuantity(Math.min(prevPrinted, qty));

                // Lines already on the bill may be re-sent after the dish went unavailable; only new lines must be available.
                BigDecimal price = resolveLinePrice(itemDto.getMenuItemId(), qty, itemDto.getPrice(), !printedQtyMap.containsKey(key));
                item.setPrice(price);
                BigDecimal lineSubtotal = price.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                item.setSubtotal(lineSubtotal);
                subtotal = subtotal.add(lineSubtotal);
                bill.getItems().add(item);
            }
        }

        if (bill.getId() == null && bill.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        java.util.List<String> changeLines = new java.util.ArrayList<>();
        if (wasKotPrinted) {
            java.util.Map<String, Integer> newItemQuantities = new java.util.HashMap<>();
            java.util.Map<String, String> newItemNotes = new java.util.HashMap<>();
            java.util.Map<String, String> newItemNames = new java.util.HashMap<>();

            for (BillItem item : bill.getItems()) {
                String key = (item.getMenuItemId() != null ? "ID_" + item.getMenuItemId() : "NAME_" + item.getItemName()) + "_" + Boolean.TRUE.equals(item.getIsTakeaway());
                newItemQuantities.put(key, item.getQuantity() != null ? item.getQuantity() : 0);
                newItemNotes.put(key, item.getCustomization() != null ? item.getCustomization().trim() : "");
                String parcelSuffix = Boolean.TRUE.equals(item.getIsTakeaway()) ? " [PARCEL]" : "";
                newItemNames.put(key, item.getItemName() + parcelSuffix);
            }

            // Check added or modified items
            for (java.util.Map.Entry<String, Integer> entry : newItemQuantities.entrySet()) {
                String key = entry.getKey();
                int newQty = entry.getValue();
                String name = newItemNames.get(key);
                String newNote = newItemNotes.getOrDefault(key, "");
                int oldQty = oldItemQuantities.getOrDefault(key, 0);
                String oldNote = oldItemNotes.getOrDefault(key, "");

                String noteStr = !newNote.isBlank() ? " (" + newNote + ")" : "";

                if (oldQty == 0) {
                    changeLines.add(String.format("[+] ADDED: %dx %s%s", newQty, name, noteStr));
                } else if (newQty != oldQty || !Objects.equals(newNote, oldNote)) {
                    if (newQty > oldQty) {
                        int diff = newQty - oldQty;
                        changeLines.add(String.format("[*] INCREASED: %s (Qty: %d -> %d, +%d)%s", name, oldQty, newQty, diff, noteStr));
                    } else if (newQty < oldQty) {
                        int diff = oldQty - newQty;
                        changeLines.add(String.format("[-] DECREASED: %s (Qty: %d -> %d, -%d)%s", name, oldQty, newQty, diff, noteStr));
                    } else {
                        changeLines.add(String.format("[*] NOTE UPDATED: %s%s", name, noteStr));
                    }
                }
            }

            // Check removed items
            for (java.util.Map.Entry<String, Integer> entry : oldItemQuantities.entrySet()) {
                String key = entry.getKey();
                int oldQty = entry.getValue();
                if (!newItemQuantities.containsKey(key) && oldQty > 0) {
                    String name = oldItemNames.getOrDefault(key, "Item");
                    changeLines.add(String.format("[-] REMOVED: %dx %s", oldQty, name));
                }
            }

            // Also preserve any previous unprinted REMOVED lines if those items are still absent
            if (bill.getKotLastChanges() != null && !bill.getKotLastChanges().isBlank()) {
                for (String prevLine : bill.getKotLastChanges().split("\r?\n")) {
                    String trimmed = prevLine.trim();
                    if (trimmed.startsWith("[-] REMOVED:")) {
                        boolean alreadyCovered = false;
                        for (String cl : changeLines) {
                            if (cl.startsWith("[-] REMOVED:") && cl.equals(trimmed)) {
                                alreadyCovered = true;
                                break;
                            }
                        }
                        if (!alreadyCovered) {
                            String rest = trimmed.substring("[-] REMOVED:".length()).trim();
                            int xIdx = rest.indexOf('x');
                            String remItemName = (xIdx > 0) ? rest.substring(xIdx + 1).trim() : rest;
                            boolean reAdded = newItemNames.values().stream().anyMatch(n -> n.equalsIgnoreCase(remItemName));
                            if (!reAdded) {
                                changeLines.add(trimmed);
                            }
                        }
                    }
                }
            }

            if (!changeLines.isEmpty()) {
                // The update number identifies a KOT print, so it advances once per print cycle: further saves made
                // before the pending update is printed extend the same update instead of starting a new one.
                boolean updatePending = bill.getKotUpdatedAt() != null && bill.getKotPrintedAt() != null
                        && !bill.getKotUpdatedAt().isBefore(bill.getKotPrintedAt());
                int currentCount = bill.getKotUpdateCount() != null ? bill.getKotUpdateCount() : 0;
                bill.setKotUpdateCount(updatePending && currentCount > 0 ? currentCount : currentCount + 1);
                bill.setKotUpdatedAt(LocalDateTime.now());
                bill.setKotLastChanges(String.join("\n", changeLines));
            } else if (bill.getKotUpdatedAt() != null && bill.getKotPrintedAt() != null
                    && !bill.getKotUpdatedAt().isBefore(bill.getKotPrintedAt())) {
                // Everything saved since the last KOT was taken out again: nothing is left to send to the kitchen.
                bill.setKotLastChanges(null);
            }
        }

        bill.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        Double discToApply = isManagerOrAdmin ? (dto.getDiscountPercent() != null ? dto.getDiscountPercent() : bill.getDiscountPercent()) : bill.getDiscountPercent();
        Double taxToApply = isManagerOrAdmin ? dto.getTaxPercent() : bill.getTaxPercent();
        Boolean gstToApply = isManagerOrAdmin ? dto.getGstEnabled() : bill.getGstEnabled();
        Double cgstToApply = isManagerOrAdmin ? dto.getCgstPercent() : bill.getCgstPercent();
        Double sgstToApply = isManagerOrAdmin ? dto.getSgstPercent() : bill.getSgstPercent();
        computeBillAmounts(bill, discToApply, taxToApply, gstToApply, cgstToApply, sgstToApply, dto.getParcelCharge());

        Bill saved = billRepository.save(bill);

        auditLogService.log("ORDER_UPDATE", "BILL", saved.getId(), null,
                String.format("Updated Order for %s | Ref #%s | Items: %d | Subtotal: ₹%s",
                        getLocationDisplay(saved), saved.getBillNumber(), saved.getItems().size(), saved.getSubtotal()));
        return toDTO(saved);
    }

    public BillResponseDTO getActiveOrderByOutdoorToken(String outdoorToken) {
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);
        return billRepository.findFirstByOutdoorTokenAndStatusInOrderByCreatedAtDesc(outdoorToken, activeStatuses)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional
    public BillResponseDTO changeOrderLocation(Long id, com.neram.pos.dto.ChangeLocationRequestDTO dto) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        if (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.CANCELLED) {
            throw new IllegalStateException("Cannot change location of completed or cancelled order.");
        }

        String targetType = dto.getOrderType() != null ? dto.getOrderType().toUpperCase() : "TABLE";
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);

        if ("OUTDOOR".equals(targetType)) {
            String cName = dto.getCustomerName() != null ? dto.getCustomerName().trim() : (bill.getCustomerName() != null ? bill.getCustomerName().trim() : "");
            if (cName.isBlank()) {
                throw new IllegalArgumentException("Customer name is required for outdoor orders.");
            }
            bill.setCustomerName(cName);
            if (bill.getOutdoorToken() == null) {
                bill.setOutdoorToken(generateOutdoorToken());
            }
            bill.setOrderType(Bill.OrderType.OUTDOOR);
            bill.setTableNumber(null);
            bill.setTakeawayToken(null);
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
            Bill saved = billRepository.save(bill);
            auditLogService.log("CHANGE_ORDER_LOCATION", "BILL", saved.getId(), null,
                    String.format("Changed order #%s location to Outdoor (%s • %s)",
                            saved.getBillNumber(), saved.getCustomerName(), saved.getOutdoorToken()));
            return toDTO(saved);
        } else if ("TAKEAWAY".equals(targetType)) {
            String cName = dto.getCustomerName() != null && !dto.getCustomerName().trim().isBlank() ? dto.getCustomerName().trim() : (bill.getCustomerName() != null ? bill.getCustomerName().trim() : "Takeaway Guest");
            bill.setCustomerName(cName);
            if (bill.getTakeawayToken() == null) {
                bill.setTakeawayToken(dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank() ? dto.getTakeawayToken().trim() : generateTakeawayToken());
            }
            bill.setOrderType(Bill.OrderType.TAKEAWAY);
            bill.setTableNumber(null);
            bill.setOutdoorToken(null);
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
            Bill saved = billRepository.save(bill);
            auditLogService.log("CHANGE_ORDER_LOCATION", "BILL", saved.getId(), null,
                    String.format("Changed order #%s location to Takeaway (%s • %s)",
                            saved.getBillNumber(), saved.getCustomerName(), saved.getTakeawayToken()));
            return toDTO(saved);
        } else {
            // Target is TABLE or legacy TAKEAWAY
            if (dto.getTableNumber() == null || dto.getTableNumber().trim().isBlank()) {
                throw new IllegalArgumentException("Please select a table.");
            }
            String targetTable = dto.getTableNumber().trim();
            if (!targetTable.equalsIgnoreCase("Takeaway") && !diningTableRepository.existsByNameIgnoreCaseAndActiveTrue(targetTable)) {
                throw new IllegalArgumentException("Invalid table selected: " + targetTable);
            }
            // Check if another active order is already on that table
            Optional<Bill> existingActive = billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(targetTable, activeStatuses);
            if (existingActive.isPresent() && !existingActive.get().getId().equals(bill.getId())) {
                throw new IllegalStateException("Table " + targetTable + " already has an active order!");
            }
            bill.setTableNumber(targetTable);
            bill.setOrderType(targetTable.equalsIgnoreCase("Takeaway") ? Bill.OrderType.TAKEAWAY : Bill.OrderType.TABLE);
            bill.setOutdoorToken(null);
            bill.setTakeawayToken(null);
            if (dto.getCustomerName() != null && !dto.getCustomerName().trim().isBlank()) {
                bill.setCustomerName(dto.getCustomerName().trim());
            }
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
            Bill saved = billRepository.save(bill);
            auditLogService.log("CHANGE_ORDER_LOCATION", "BILL", saved.getId(), null,
                    String.format("Changed order #%s location to Table %s", saved.getBillNumber(), saved.getTableNumber()));
            return toDTO(saved);
        }
    }

    public List<BillResponseDTO> getActiveOrders() {
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);
        return billRepository.findByStatusInOrderByCreatedAtAsc(activeStatuses)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public BillResponseDTO getActiveOrderByTable(String tableNumber) {
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);
        return billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(tableNumber, activeStatuses)
                .map(this::toDTO)
                .orElse(null);
    }

    public BillResponseDTO getActiveOrderByTakeawayToken(String takeawayToken) {
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);
        return billRepository.findFirstByTakeawayTokenAndStatusInOrderByCreatedAtDesc(takeawayToken, activeStatuses)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional
    public BillResponseDTO requestKitchenOrder(Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        if (bill.getStatus() == Bill.Status.KOT_REQUESTED) {
            throw new IllegalStateException("Kitchen ticket has already been sent to kitchen and is awaiting print.");
        }
        if (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.CANCELLED) {
            throw new IllegalStateException("Cannot request kitchen ticket for completed or cancelled bill.");
        }

        boolean hasUnprintedItems = bill.getItems() != null && bill.getItems().stream()
                .anyMatch(it -> (it.getQuantity() != null ? it.getQuantity() : 0) > (it.getPrintedQuantity() != null ? it.getPrintedQuantity() : 0));
        if (bill.getStatus() == Bill.Status.KOT_PRINTED && !hasUnprintedItems) {
            throw new IllegalStateException("All items have already been printed. Add another dish and save order first.");
        }

        bill.setStatus(Bill.Status.KOT_REQUESTED);
        if (bill.getKotNumber() == null) {
            bill.setKotNumber(bill.getBillNumber());
        }
        Bill saved = billRepository.save(bill);
        auditLogService.log("REQUEST_KOT", "BILL", saved.getId(), null,
                String.format("Kitchen Order Ticket requested for Table %s | Ref #%s | Items: %d",
                        saved.getTableNumber(), saved.getBillNumber(), saved.getItems().size()));
        return toDTO(saved);
    }

    @Transactional
    public BillResponseDTO printKitchenOrder(Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        boolean wasPreviouslyPrinted = (bill.getKotPrintedAt() != null);
        boolean hasKotUpdates = (bill.getKotUpdateCount() != null && bill.getKotUpdateCount() > 0)
                && (bill.getKotLastChanges() != null && !bill.getKotLastChanges().isBlank());

        bill.setKotPrintedAt(LocalDateTime.now());
        if (bill.getKotNumber() == null) {
            bill.setKotNumber(bill.getBillNumber());
        }

        // Identify updated items / delta quantities for KOT ticket
        List<BillItemResponseDTO> latestItems = new ArrayList<>();
        if (wasPreviouslyPrinted && hasKotUpdates) {
            // When order is updated, ONLY list the updated items
            latestItems = parseKotChanges(bill.getKotLastChanges(), bill.getItems());
        }

        // If not an update or parse returned empty, identify unprinted delta items
        if (latestItems.isEmpty() && bill.getItems() != null) {
            for (BillItem it : bill.getItems()) {
                int currentQty = it.getQuantity() != null ? it.getQuantity() : 0;
                int printedQty = it.getPrintedQuantity() != null ? it.getPrintedQuantity() : 0;
                int delta = currentQty - printedQty;
                if (delta > 0) {
                    BigDecimal itemPrice = it.getPrice() != null ? it.getPrice() : BigDecimal.ZERO;
                    BigDecimal itemSubtotal = itemPrice.multiply(BigDecimal.valueOf(delta)).setScale(2, RoundingMode.HALF_UP);
                    String updateType = wasPreviouslyPrinted ? (printedQty == 0 ? "ADDED" : "INCREASED") : null;
                    String updateMsg = wasPreviouslyPrinted
                            ? (printedQty == 0 ? String.format("[+%d NEW ITEM]", delta) : String.format("[+%d ADDED] (Total: %d)", delta, currentQty))
                            : null;
                    latestItems.add(BillItemResponseDTO.builder()
                            .id(it.getId())
                            .menuItemId(it.getMenuItemId())
                            .itemName(it.getItemName())
                            .quantity(delta) // show only the new quantity to kitchen
                            .printedQuantity(printedQty)
                            .deltaQuantity(delta)
                            .previousQuantity(printedQty)
                            .currentTotalQuantity(currentQty)
                            .price(itemPrice)
                            .subtotal(itemSubtotal)
                            .customization(it.getCustomization())
                            .updateType(updateType)
                            .updateMessage(updateMsg)
                            .isTakeaway(Boolean.TRUE.equals(it.getIsTakeaway()))
                            .build());
                }
            }
        }

        // If all items were somehow already printed (e.g. reprint of full ticket), print all current items
        if (latestItems.isEmpty() && bill.getItems() != null) {
            for (BillItem it : bill.getItems()) {
                latestItems.add(BillItemResponseDTO.builder()
                        .id(it.getId())
                        .menuItemId(it.getMenuItemId())
                        .itemName(it.getItemName())
                        .quantity(it.getQuantity())
                        .printedQuantity(it.getQuantity())
                        .deltaQuantity(it.getQuantity())
                        .previousQuantity(it.getQuantity())
                        .currentTotalQuantity(it.getQuantity())
                        .price(it.getPrice())
                        .subtotal(it.getSubtotal())
                        .customization(it.getCustomization())
                        .isTakeaway(Boolean.TRUE.equals(it.getIsTakeaway()))
                        .build());
            }
        }

        // Mark all items as fully printed to kitchen
        if (bill.getItems() != null) {
            for (BillItem it : bill.getItems()) {
                it.setPrintedQuantity(it.getQuantity());
            }
        }

        // Set status to KOT_PRINTED so waiter immediately knows the print status
        bill.setStatus(Bill.Status.KOT_PRINTED);

        Bill saved = billRepository.save(bill);
        recipeService.deductIngredientsForOrder(saved);

        auditLogService.log("PRINT_KOT", "BILL", saved.getId(), null,
                String.format("Printed Kitchen Order Ticket (KOT) for Table %s | KOT/Bill #%s | Items Printed: %d",
                        saved.getTableNumber(), saved.getKotNumber(), latestItems.size()));

        BillResponseDTO response = toDTO(saved);
        response.setLatestKotItems(latestItems);
        return response;
    }

    /**
     * Parses the kotLastChanges change-log into structured BillItemResponseDTOs with updateType,
     * updateMessage, deltaQuantity, previousQuantity, and currentTotalQuantity.
     */
    public static List<BillItemResponseDTO> parseKotChanges(String kotLastChanges, List<BillItem> currentBillItems) {
        List<BillItemResponseDTO> items = new ArrayList<>();
        if (kotLastChanges == null || kotLastChanges.isBlank()) {
            return items;
        }

        java.util.Map<String, BillItem> currentItemMap = new java.util.HashMap<>();
        if (currentBillItems != null) {
            for (BillItem bi : currentBillItems) {
                if (bi.getItemName() != null) {
                    currentItemMap.put(bi.getItemName().trim().toLowerCase(), bi);
                }
            }
        }

        String[] lines = kotLastChanges.split("\r?\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[+] ADDED:")) {
                String rest = line.substring("[+] ADDED:".length()).trim();
                int xIndex = rest.indexOf('x');
                if (xIndex > 0) {
                    try {
                        int qty = Integer.parseInt(rest.substring(0, xIndex).trim());
                        String nameAndNote = rest.substring(xIndex + 1).trim();
                        String name = nameAndNote;
                        String note = null;
                        if (nameAndNote.endsWith(")") && nameAndNote.contains("(")) {
                            int openParen = nameAndNote.lastIndexOf('(');
                            name = nameAndNote.substring(0, openParen).trim();
                            note = nameAndNote.substring(openParen + 1, nameAndNote.length() - 1).trim();
                        }
                        BillItem matched = currentItemMap.get(name.toLowerCase());
                        items.add(BillItemResponseDTO.builder()
                                .id(matched != null ? matched.getId() : null)
                                .menuItemId(matched != null ? matched.getMenuItemId() : null)
                                .itemName(name)
                                .quantity(qty)
                                .deltaQuantity(qty)
                                .previousQuantity(0)
                                .currentTotalQuantity(matched != null ? matched.getQuantity() : qty)
                                .price(matched != null ? matched.getPrice() : BigDecimal.ZERO)
                                .subtotal(matched != null && matched.getPrice() != null ? matched.getPrice().multiply(BigDecimal.valueOf(qty)) : BigDecimal.ZERO)
                                .customization(note != null ? note : (matched != null ? matched.getCustomization() : null))
                                .updateType("ADDED")
                                .updateMessage(String.format("[+%d NEW ITEM]", qty))
                                .build());
                    } catch (Exception ignored) {}
                }
            } else if (line.startsWith("[*] INCREASED:")) {
                String rest = line.substring("[*] INCREASED:".length()).trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+?)\\s*\\(Qty:\\s*(\\d+)\\s*->\\s*(\\d+),\\s*\\+(\\d+)\\)(?:\\s*\\((.+)\\))?$").matcher(rest);
                if (m.matches()) {
                    String name = m.group(1).trim();
                    int oldQty = Integer.parseInt(m.group(2));
                    int newQty = Integer.parseInt(m.group(3));
                    int diff = Integer.parseInt(m.group(4));
                    String note = m.group(5);
                    BillItem matched = currentItemMap.get(name.toLowerCase());
                    items.add(BillItemResponseDTO.builder()
                            .id(matched != null ? matched.getId() : null)
                            .menuItemId(matched != null ? matched.getMenuItemId() : null)
                            .itemName(name)
                            .quantity(diff) // Delta quantity for kitchen preparation
                            .deltaQuantity(diff)
                            .previousQuantity(oldQty)
                            .currentTotalQuantity(newQty)
                            .price(matched != null ? matched.getPrice() : BigDecimal.ZERO)
                            .subtotal(matched != null && matched.getPrice() != null ? matched.getPrice().multiply(BigDecimal.valueOf(diff)) : BigDecimal.ZERO)
                            .customization(note != null ? note : (matched != null ? matched.getCustomization() : null))
                            .updateType("INCREASED")
                            .updateMessage(String.format("[+%d ADDED] (Total: %d)", diff, newQty))
                            .build());
                }
            } else if (line.startsWith("[-] DECREASED:")) {
                String rest = line.substring("[-] DECREASED:".length()).trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+?)\\s*\\(Qty:\\s*(\\d+)\\s*->\\s*(\\d+),\\s*-(\\d+)\\)(?:\\s*\\((.+)\\))?$").matcher(rest);
                if (m.matches()) {
                    String name = m.group(1).trim();
                    int oldQty = Integer.parseInt(m.group(2));
                    int newQty = Integer.parseInt(m.group(3));
                    int diff = Integer.parseInt(m.group(4));
                    String note = m.group(5);
                    BillItem matched = currentItemMap.get(name.toLowerCase());
                    items.add(BillItemResponseDTO.builder()
                            .id(matched != null ? matched.getId() : null)
                            .menuItemId(matched != null ? matched.getMenuItemId() : null)
                            .itemName(name)
                            .quantity(-diff)
                            .deltaQuantity(-diff)
                            .previousQuantity(oldQty)
                            .currentTotalQuantity(newQty)
                            .price(matched != null ? matched.getPrice() : BigDecimal.ZERO)
                            .subtotal(BigDecimal.ZERO)
                            .customization(note != null ? note : (matched != null ? matched.getCustomization() : null))
                            .updateType("DECREASED")
                            .updateMessage(String.format("[-%d REDUCED] (Total: %d)", diff, newQty))
                            .build());
                }
            } else if (line.startsWith("[-] REMOVED:")) {
                String rest = line.substring("[-] REMOVED:".length()).trim();
                int xIndex = rest.indexOf('x');
                int qty = 1;
                String name = rest;
                if (xIndex > 0) {
                    try {
                        qty = Integer.parseInt(rest.substring(0, xIndex).trim());
                        name = rest.substring(xIndex + 1).trim();
                    } catch (Exception ignored) {}
                }
                items.add(BillItemResponseDTO.builder()
                        .itemName(name)
                        .quantity(-qty)
                        .deltaQuantity(-qty)
                        .previousQuantity(qty)
                        .currentTotalQuantity(0)
                        .price(BigDecimal.ZERO)
                        .subtotal(BigDecimal.ZERO)
                        .updateType("REMOVED")
                        .updateMessage("[-CANCELLED / REMOVED]")
                        .build());
            } else if (line.startsWith("[*] NOTE UPDATED:")) {
                String rest = line.substring("[*] NOTE UPDATED:".length()).trim();
                String name = rest;
                String note = "";
                if (rest.endsWith(")") && rest.contains("(")) {
                    int openParen = rest.lastIndexOf('(');
                    name = rest.substring(0, openParen).trim();
                    note = rest.substring(openParen + 1, rest.length() - 1).trim();
                }
                BillItem matched = currentItemMap.get(name.toLowerCase());
                int curQty = matched != null ? matched.getQuantity() : 1;
                items.add(BillItemResponseDTO.builder()
                        .id(matched != null ? matched.getId() : null)
                        .menuItemId(matched != null ? matched.getMenuItemId() : null)
                        .itemName(name)
                        .quantity(curQty)
                        .deltaQuantity(0)
                        .previousQuantity(curQty)
                        .currentTotalQuantity(curQty)
                        .price(matched != null ? matched.getPrice() : BigDecimal.ZERO)
                        .subtotal(matched != null && matched.getPrice() != null ? matched.getPrice().multiply(BigDecimal.valueOf(curQty)) : BigDecimal.ZERO)
                        .customization(note)
                        .updateType("NOTE_UPDATED")
                        .updateMessage("[NOTE UPDATED] " + note)
                        .build());
            }
        }
        return items;
    }

    @Transactional
    public BillResponseDTO requestBill(Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        if (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.CANCELLED) {
            throw new IllegalStateException("Cannot request bill for completed or cancelled order.");
        }
        if (bill.getStatus() == Bill.Status.BILL_REQUESTED) {
            throw new IllegalStateException("Bill has already been requested for this order.");
        }
        if (bill.getStatus() == Bill.Status.BILL_PRINTED) {
            throw new IllegalStateException("Bill has already been printed for this order.");
        }
        if (bill.getStatus() != Bill.Status.KOT_PRINTED || bill.getKotPrintedAt() == null) {
            if (bill.getStatus() == Bill.Status.KOT_REQUESTED) {
                throw new IllegalStateException("Kitchen ticket has been requested but not yet printed by manager.");
            }
            throw new IllegalStateException("Bill can only be requested after the order has been sent to kitchen and printed.");
        }

        boolean hasUnprintedItems = bill.getItems() != null && bill.getItems().stream()
                .anyMatch(it -> (it.getQuantity() != null ? it.getQuantity() : 0) > (it.getPrintedQuantity() != null ? it.getPrintedQuantity() : 0));
        if (hasUnprintedItems) {
            throw new IllegalStateException("Cannot request bill: new items have been added that are not yet printed for the kitchen.");
        }

        bill.setStatus(Bill.Status.BILL_REQUESTED);
        Bill saved = billRepository.save(bill);
        auditLogService.log("REQUEST_BILL", "BILL", saved.getId(), null,
                String.format("Bill requested by staff for Table %s | Bill #%s | Net: ₹%s",
                        saved.getTableNumber(), saved.getBillNumber(), saved.getNetAmount()));
        return toDTO(saved);
    }

    @Transactional
    public BillResponseDTO approveAndPayBill(Long id, BillRequestDTO dto) {
        Bill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        if (bill.getStatus() == Bill.Status.PAID) {
            throw new IllegalStateException("Bill has already been paid and settled.");
        }
        if (bill.getStatus() == Bill.Status.CANCELLED) {
            throw new IllegalStateException("Cannot approve or pay a cancelled order.");
        }
        if (bill.getStatus() != Bill.Status.BILL_REQUESTED && bill.getStatus() != Bill.Status.BILL_PRINTED) {
            throw new IllegalStateException("Bill can only be paid and printed after bill request has been submitted.");
        }

        if (dto != null) {
            if (dto.getPaymentMode() != null) {
                try {
                    bill.setPaymentMode(Bill.PaymentMode.valueOf(dto.getPaymentMode().toUpperCase()));
                } catch (Exception ignored) {}
            }
            Double discPct = dto.getDiscountPercent();
            Double taxPct = dto.getTaxPercent();
            Boolean gstEn = dto.getGstEnabled();
            Double cgstPct = dto.getCgstPercent();
            Double sgstPct = dto.getSgstPercent();
            computeBillAmounts(bill, discPct, taxPct, gstEn, cgstPct, sgstPct, dto.getParcelCharge());
        } else {
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
        }

        bill.setStatus(Bill.Status.PAID);
        bill.setPrintedAt(LocalDateTime.now());
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> bill.setPrintedBy(u.getId()));
            }
        } catch (Exception ignored) {}

        Bill saved = billRepository.save(bill);
        cashierShiftService.recordBillPayment(saved);
        recipeService.deductIngredientsForOrder(saved);
        saved = billRepository.save(saved);

        BillResponseDTO res = toDTO(saved);
        auditLogService.log("APPROVE_AND_PRINT_BILL", "BILL", saved.getId(), null,
                String.format("Manager approved, printed & settled Bill #%s | Table: %s | Net: ₹%s | Mode: %s | Order Staff: %s | Cashier: %s",
                        saved.getBillNumber(), saved.getTableNumber(), saved.getNetAmount(), saved.getPaymentMode(), res.getCreatedByName(), res.getPrintedByName() != null ? res.getPrintedByName() : "Cashier"));
        return res;
    }

    @Transactional
    public BillResponseDTO printBill(Long id, BillRequestDTO dto) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        if (bill.getStatus() == Bill.Status.PAID) {
            throw new IllegalStateException("Bill has already been paid and settled.");
        }
        if (bill.getStatus() == Bill.Status.CANCELLED) {
            throw new IllegalStateException("Cannot print a cancelled order.");
        }
        if (bill.getStatus() != Bill.Status.BILL_REQUESTED && bill.getStatus() != Bill.Status.BILL_PRINTED) {
            throw new IllegalStateException("Bill can only be printed after bill request has been submitted.");
        }

        if (dto != null) {
            if (dto.getPaymentMode() != null) {
                try {
                    bill.setPaymentMode(Bill.PaymentMode.valueOf(dto.getPaymentMode().toUpperCase()));
                } catch (Exception ignored) {}
            }
            Double discPct = dto.getDiscountPercent();
            Double taxPct = dto.getTaxPercent();
            Boolean gstEn = dto.getGstEnabled();
            Double cgstPct = dto.getCgstPercent();
            Double sgstPct = dto.getSgstPercent();
            computeBillAmounts(bill, discPct, taxPct, gstEn, cgstPct, sgstPct, dto.getParcelCharge());
        } else {
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
        }

        bill.setStatus(Bill.Status.BILL_PRINTED);
        bill.setPrintedAt(LocalDateTime.now());
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> bill.setPrintedBy(u.getId()));
            }
        } catch (Exception ignored) {}

        Bill saved = billRepository.save(bill);

        BillResponseDTO res = toDTO(saved);
        auditLogService.log("PRINT_BILL", "BILL", saved.getId(), null,
                String.format("Manager printed Bill #%s | %s | Net: ₹%s | Payment Status: PENDING",
                        saved.getBillNumber(), getLocationDisplay(saved), saved.getNetAmount()));
        return res;
    }

    @Transactional
    public BillResponseDTO confirmPayment(Long id, BillRequestDTO dto) {
        // Row lock: a concurrent confirmation from another terminal waits here, then sees PAID and is rejected below,
        // so payment recording, shift totals and ingredient deduction can only ever run once per bill.
        Bill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        if (bill.getStatus() == Bill.Status.PAID) {
            throw new IllegalStateException("Bill has already been paid and settled.");
        }
        if (bill.getStatus() == Bill.Status.CANCELLED) {
            throw new IllegalStateException("Cannot confirm payment for a cancelled order.");
        }
        if (bill.getStatus() != Bill.Status.BILL_PRINTED && bill.getStatus() != Bill.Status.BILL_REQUESTED) {
            throw new IllegalStateException("Payment can only be confirmed after bill has been printed or requested.");
        }

        if (dto != null && dto.getPaymentMode() != null) {
            try {
                bill.setPaymentMode(Bill.PaymentMode.valueOf(dto.getPaymentMode().toUpperCase()));
            } catch (Exception ignored) {}
        } else if (bill.getPaymentMode() == null) {
            bill.setPaymentMode(Bill.PaymentMode.CASH);
        }

        if (dto != null && (dto.getDiscountPercent() != null || dto.getTaxPercent() != null || dto.getParcelCharge() != null)) {
            computeBillAmounts(bill, dto.getDiscountPercent(), dto.getTaxPercent(), dto.getGstEnabled(), dto.getCgstPercent(), dto.getSgstPercent(), dto.getParcelCharge());
        } else {
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
        }

        bill.setStatus(Bill.Status.PAID);
        if (bill.getPrintedAt() == null) {
            bill.setPrintedAt(LocalDateTime.now());
        }

        Bill saved = billRepository.save(bill);
        cashierShiftService.recordBillPayment(saved);
        recipeService.deductIngredientsForOrder(saved);
        saved = billRepository.save(saved);

        BillResponseDTO res = toDTO(saved);
        auditLogService.log("CONFIRM_PAYMENT", "BILL", saved.getId(), null,
                String.format("Manager confirmed payment for Bill #%s | %s | Net: ₹%s | Mode: %s",
                        saved.getBillNumber(), getLocationDisplay(saved), saved.getNetAmount(), saved.getPaymentMode()));
        return res;
    }

    private String generateBillNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = billRepository.countTotalBills() + 1;
        String candidate = String.format("NRM-%s-%04d", datePart, count);
        while (billRepository.findByBillNumber(candidate).isPresent()) {
            count++;
            candidate = String.format("NRM-%s-%04d", datePart, count);
        }
        return candidate;
    }

    public BillResponseDTO toDTO(Bill b) {
        String creatorName = "Staff";
        if (b.getCreatedBy() != null) {
            creatorName = userRepository.findById(b.getCreatedBy()).map(User::getUsername).orElse("Staff");
        }

        String printerName = null;
        if (b.getPrintedBy() != null) {
            printerName = userRepository.findById(b.getPrintedBy()).map(User::getUsername).orElse(null);
        }

        List<BillItemResponseDTO> itemDTOs = b.getItems().stream()
                .map(it -> BillItemResponseDTO.builder()
                        .id(it.getId())
                        .menuItemId(it.getMenuItemId())
                        .itemName(it.getItemName())
                        .quantity(it.getQuantity())
                        .printedQuantity(it.getPrintedQuantity() != null ? it.getPrintedQuantity() : 0)
                        .price(it.getPrice())
                        .subtotal(it.getSubtotal())
                        .customization(it.getCustomization())
                        .isTakeaway(Boolean.TRUE.equals(it.getIsTakeaway()))
                        .build())
                .collect(Collectors.toList());

        String oType = b.getOrderType() != null ? b.getOrderType().name() : (b.getTakeawayToken() != null || (b.getTableNumber() != null && b.getTableNumber().equalsIgnoreCase("Takeaway")) ? "TAKEAWAY" : (b.getOutdoorToken() != null ? "OUTDOOR" : "TABLE"));

        boolean isGstActive = b.getGstEnabled() != null ? b.getGstEnabled() : (b.getTaxAmount() != null && b.getTaxAmount().compareTo(BigDecimal.ZERO) > 0);
        double cgstPct = b.getCgstPercent() != null ? b.getCgstPercent() : (isGstActive && b.getTaxPercent() != null ? b.getTaxPercent() / 2.0 : 0.0);
        double sgstPct = b.getSgstPercent() != null ? b.getSgstPercent() : (isGstActive && b.getTaxPercent() != null ? b.getTaxPercent() / 2.0 : 0.0);
        BigDecimal cgstAmt = b.getCgstAmount() != null ? b.getCgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sgstAmt = b.getSgstAmount() != null ? b.getSgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundOff = b.getRoundOff() != null ? b.getRoundOff().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmt = b.getTaxAmount() != null ? b.getTaxAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal refunded = billRefundRepository.sumSuccessfulRefundsByBillId(b.getId());
        if (refunded == null) refunded = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = b.getNetAmount() != null ? b.getNetAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingRefundable = net.subtract(refunded).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        String kotNoticeType = null;
        if (b.getKotCancelledAt() != null) {
            kotNoticeType = "CANCELLED";
        } else if ((b.getKotUpdateCount() != null && b.getKotUpdateCount() > 0) || b.getKotUpdatedAt() != null) {
            kotNoticeType = "UPDATED";
        } else if (b.getKotPrintedAt() != null) {
            kotNoticeType = "ORIGINAL";
        }

        List<String> kotChangesSummary = null;
        List<BillItemResponseDTO> latestKotItems = null;
        if (b.getKotLastChanges() != null && !b.getKotLastChanges().isBlank()) {
            kotChangesSummary = Arrays.asList(b.getKotLastChanges().split("\r?\n"));
            if (b.getKotUpdateCount() != null && b.getKotUpdateCount() > 0) {
                latestKotItems = parseKotChanges(b.getKotLastChanges(), b.getItems());
            }
        }

        boolean hasParcel = hasParcelItemsOrTakeaway(b);
        BigDecimal parcelCharge = b.getParcelCharge() != null ? b.getParcelCharge().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return BillResponseDTO.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .orderType(oType)
                .kotNumber(b.getKotNumber() != null ? b.getKotNumber() : b.getBillNumber())
                .tableNumber(b.getTableNumber())
                .customerName(b.getCustomerName())
                .outdoorToken(b.getOutdoorToken())
                .takeawayToken(b.getTakeawayToken())
                .subtotal(b.getSubtotal())
                .discountPercent(b.getDiscountPercent())
                .discountAmount(b.getDiscountAmount())
                .taxPercent(b.getTaxPercent() != null ? b.getTaxPercent() : 0.0)
                .taxAmount(taxAmt)
                .gstEnabled(isGstActive)
                .gstin(b.getGstin())
                .cgstPercent(cgstPct)
                .sgstPercent(sgstPct)
                .cgstAmount(cgstAmt)
                .sgstAmount(sgstAmt)
                .parcelCharge(parcelCharge)
                .hasParcel(hasParcel)
                .roundOff(roundOff)
                .promotionId(b.getPromotionId())
                .promotionName(b.getPromotionName())
                .promotionPercentage(b.getPromotionPercentage())
                .promotionDiscountAmount(b.getPromotionDiscountAmount() != null ? b.getPromotionDiscountAmount() : BigDecimal.ZERO)
                .netAmount(b.getNetAmount())
                .paymentMode(b.getPaymentMode().name())
                .status(b.getStatus().name())
                .notes(b.getNotes())
                .createdBy(b.getCreatedBy())
                .createdByName(creatorName)
                .printedBy(b.getPrintedBy())
                .printedByName(printerName)
                .createdAt(b.getCreatedAt())
                .printedAt(b.getPrintedAt())
                .kotPrintedAt(b.getKotPrintedAt())
                .kotUpdateCount(b.getKotUpdateCount() != null ? b.getKotUpdateCount() : 0)
                .kotUpdatedAt(b.getKotUpdatedAt())
                .kotCancelledAt(b.getKotCancelledAt())
                .kotNoticeType(kotNoticeType)
                .kotChangesSummary(kotChangesSummary)
                .latestKotItems(latestKotItems)
                .shiftId(b.getShiftId())
                .voidedBy(b.getVoidedBy())
                .voidedByName(b.getVoidedByName())
                .voidedAt(b.getVoidedAt())
                .voidReason(b.getVoidReason())
                .refundedAmount(refunded)
                .totalRefunded(refunded)
                .remainingRefundableAmount(remainingRefundable)
                .items(itemDTOs)
                .build();
    }

    public boolean hasParcelItemsOrTakeaway(Bill bill) {
        if (bill == null) return false;
        if (bill.getOrderType() == Bill.OrderType.TAKEAWAY) return true;
        if (bill.getItems() != null) {
            return bill.getItems().stream().anyMatch(it -> Boolean.TRUE.equals(it.getIsTakeaway()));
        }
        return false;
    }

    private static final int MAX_ITEM_QUANTITY = 999;

    /**
     * Validates a line quantity and resolves the unit price server-side.
     * Staff always get the current menu price (a client-supplied price is never trusted);
     * managers/admins may override with a non-negative price. Items without a menuItemId
     * (custom lines) are manager/admin only. New lines must be available on the menu.
     */
    private BigDecimal resolveLinePrice(Long menuItemId, Integer quantity, BigDecimal clientPrice, boolean requireAvailable) {
        if (quantity == null || quantity < 1 || quantity > MAX_ITEM_QUANTITY) {
            throw new IllegalArgumentException("Item quantity must be between 1 and " + MAX_ITEM_QUANTITY);
        }
        if (clientPrice != null && clientPrice.signum() < 0) {
            throw new IllegalArgumentException("Item price cannot be negative");
        }
        boolean privileged = isManagerOrAdmin();
        if (menuItemId == null) {
            if (!privileged) {
                throw new IllegalArgumentException("Only manager or admin can add custom items that are not on the menu");
            }
            return (clientPrice != null ? clientPrice : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        com.neram.pos.entity.MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found: " + menuItemId));
        if (requireAvailable && (!Boolean.TRUE.equals(menuItem.getIsAvailable()) || Boolean.FALSE.equals(menuItem.getActive()))) {
            throw new IllegalArgumentException("'" + menuItem.getName() + "' is currently unavailable");
        }
        BigDecimal price = (privileged && clientPrice != null) ? clientPrice : menuItem.getPrice();
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private static void assertPercent(String label, double value) {
        if (Double.isNaN(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(label + " must be between 0 and 100");
        }
    }

    private void computeBillAmounts(Bill bill, Double reqDiscountPct, Double reqTaxPct, Boolean reqGstEnabled, Double reqCgstPct, Double reqSgstPct) {
        computeBillAmounts(bill, reqDiscountPct, reqTaxPct, reqGstEnabled, reqCgstPct, reqSgstPct, null);
    }

    private void computeBillAmounts(Bill bill, Double reqDiscountPct, Double reqTaxPct, Boolean reqGstEnabled, Double reqCgstPct, Double reqSgstPct, BigDecimal reqParcelCharge) {
        BigDecimal subtotal = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;

        double discPct = reqDiscountPct != null ? reqDiscountPct : (bill.getDiscountPercent() != null ? bill.getDiscountPercent() : 0.0);
        assertPercent("Discount", discPct);
        BigDecimal discPctBd = BigDecimal.valueOf(discPct);
        BigDecimal discAmt = subtotal.multiply(discPctBd).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        bill.setDiscountPercent(discPct);
        bill.setDiscountAmount(discAmt);

        BigDecimal taxable = subtotal.subtract(discAmt).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        com.neram.pos.entity.GstSetting gstCfg = gstSettingService.getSettings();
        boolean isGstActive;
        double cgstRate;
        double sgstRate;

        if (reqGstEnabled != null) {
            isGstActive = reqGstEnabled;
            if (isGstActive) {
                cgstRate = reqCgstPct != null ? reqCgstPct : (gstCfg.getCgstPercent() != null ? gstCfg.getCgstPercent() : 2.5);
                sgstRate = reqSgstPct != null ? reqSgstPct : (gstCfg.getSgstPercent() != null ? gstCfg.getSgstPercent() : 2.5);
            } else {
                cgstRate = 0.0;
                sgstRate = 0.0;
            }
        } else if (reqTaxPct != null) {
            if (reqTaxPct > 0) {
                isGstActive = true;
                cgstRate = reqCgstPct != null ? reqCgstPct : (reqTaxPct / 2.0);
                sgstRate = reqSgstPct != null ? reqSgstPct : (reqTaxPct / 2.0);
            } else {
                isGstActive = false;
                cgstRate = 0.0;
                sgstRate = 0.0;
            }
        } else if (bill.getId() != null && bill.getGstEnabled() != null && (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.REFUNDED || bill.getStatus() == Bill.Status.PARTIALLY_REFUNDED)) {
            isGstActive = bill.getGstEnabled();
            cgstRate = isGstActive ? (bill.getCgstPercent() != null ? bill.getCgstPercent() : (gstCfg.getCgstPercent() != null ? gstCfg.getCgstPercent() : 2.5)) : 0.0;
            sgstRate = isGstActive ? (bill.getSgstPercent() != null ? bill.getSgstPercent() : (gstCfg.getSgstPercent() != null ? gstCfg.getSgstPercent() : 2.5)) : 0.0;
        } else {
            isGstActive = Boolean.TRUE.equals(gstCfg.getEnabled());
            cgstRate = isGstActive ? (gstCfg.getCgstPercent() != null ? gstCfg.getCgstPercent() : 2.5) : 0.0;
            sgstRate = isGstActive ? (gstCfg.getSgstPercent() != null ? gstCfg.getSgstPercent() : 2.5) : 0.0;
        }

        assertPercent("CGST rate", cgstRate);
        assertPercent("SGST rate", sgstRate);
        assertPercent("Total GST rate", cgstRate + sgstRate);
        bill.setGstEnabled(isGstActive);
        bill.setCgstPercent(cgstRate);
        bill.setSgstPercent(sgstRate);
        double totalTaxPct = Math.round((cgstRate + sgstRate) * 100.0) / 100.0;
        bill.setTaxPercent(totalTaxPct);

        BigDecimal cgstAmt = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sgstAmt = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmt = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (isGstActive && totalTaxPct > 0) {
            bill.setGstin(gstCfg.getGstin());
            BigDecimal totalTaxPctBd = BigDecimal.valueOf(totalTaxPct);
            taxAmt = taxable.multiply(totalTaxPctBd).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal cgstRateBd = BigDecimal.valueOf(cgstRate);
            cgstAmt = taxable.multiply(cgstRateBd).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            sgstAmt = taxAmt.subtract(cgstAmt);
        } else {
            bill.setGstin(null);
        }
        bill.setCgstAmount(cgstAmt);
        bill.setSgstAmount(sgstAmt);
        bill.setTaxAmount(taxAmt);

        // Parcel charge calculation
        BigDecimal parcelCharge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean hasParcel = hasParcelItemsOrTakeaway(bill);
        if (hasParcel) {
            if (reqParcelCharge != null) {
                parcelCharge = reqParcelCharge.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            } else if (bill.getParcelCharge() != null && bill.getParcelCharge().compareTo(BigDecimal.ZERO) > 0) {
                parcelCharge = bill.getParcelCharge().setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal defaultParcel = appSettingService != null ? appSettingService.getDefaultParcelCharge() : new BigDecimal("10.00");
                parcelCharge = defaultParcel.setScale(2, RoundingMode.HALF_UP);
            }
        }
        bill.setParcelCharge(parcelCharge);

        BigDecimal exactTotal = taxable.add(taxAmt).add(parcelCharge);
        BigDecimal totalAfterGstAndRoundOff;
        if (Boolean.TRUE.equals(gstCfg.getRoundOffEnabled())) {
            BigDecimal rounded = exactTotal.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
            BigDecimal roundOff = rounded.subtract(exactTotal);
            bill.setRoundOff(roundOff);
            totalAfterGstAndRoundOff = rounded;
        } else {
            bill.setRoundOff(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            totalAfterGstAndRoundOff = exactTotal.setScale(2, RoundingMode.HALF_UP);
        }

        // Apply Promotion Discount on bill total after GST and round-off
        applyPromotionDiscount(bill, totalAfterGstAndRoundOff);
    }

    private void applyPromotionDiscount(Bill bill, BigDecimal totalAfterGstAndRoundOff) {
        Long promoId = null;
        String promoName = null;
        Double promoPct = null;

        // If bill is already settled (PAID, REFUNDED, PARTIALLY_REFUNDED, VOIDED, CANCELLED), preserve existing snapshot only
        if (bill.getId() != null && (bill.getStatus() == Bill.Status.PAID || bill.getStatus() == Bill.Status.REFUNDED || bill.getStatus() == Bill.Status.PARTIALLY_REFUNDED || bill.getStatus() == Bill.Status.VOIDED || bill.getStatus() == Bill.Status.CANCELLED)) {
            if (bill.getPromotionPercentage() != null && bill.getPromotionPercentage() > 0) {
                promoId = bill.getPromotionId();
                promoName = bill.getPromotionName();
                promoPct = bill.getPromotionPercentage();
            }
        } else {
            // For new or active bills:
            // 1. If already has an applied promotion snapshot, preserve its details & percentage
            if (bill.getPromotionPercentage() != null && bill.getPromotionPercentage() > 0) {
                promoId = bill.getPromotionId();
                promoName = bill.getPromotionName();
                promoPct = bill.getPromotionPercentage();
            } else {
                // 2. Otherwise check for currently active promotion
                java.util.Optional<com.neram.pos.entity.Promotion> activePromoOpt = promotionService.getActivePromotion();
                if (activePromoOpt.isPresent()) {
                    com.neram.pos.entity.Promotion activePromo = activePromoOpt.get();
                    promoId = activePromo.getId();
                    promoName = activePromo.getName();
                    promoPct = activePromo.getDiscountPercentage();
                }
            }
        }

        if (promoPct != null && promoPct > 0) {
            BigDecimal promoPctBd = BigDecimal.valueOf(promoPct);
            BigDecimal promoDiscAmt = totalAfterGstAndRoundOff.multiply(promoPctBd)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            bill.setPromotionId(promoId);
            bill.setPromotionName(promoName);
            bill.setPromotionPercentage(promoPct);
            bill.setPromotionDiscountAmount(promoDiscAmt);

            BigDecimal finalNet = totalAfterGstAndRoundOff.subtract(promoDiscAmt).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            bill.setNetAmount(finalNet);
        } else {
            bill.setPromotionId(null);
            bill.setPromotionName(null);
            bill.setPromotionPercentage(null);
            bill.setPromotionDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setNetAmount(totalAfterGstAndRoundOff);
        }
    }

    public boolean isManagerOrAdmin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                return auth.getAuthorities().stream().anyMatch(a -> {
                    String authority = a.getAuthority();
                    return "ROLE_ADMIN".equals(authority) ||
                           "ROLE_MANAGER".equals(authority) ||
                           "ADMIN".equals(authority) ||
                           "MANAGER".equals(authority);
                });
            }
        } catch (Exception ignored) {}
        return false;
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
}
