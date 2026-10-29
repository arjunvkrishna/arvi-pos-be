package com.neram.pos.service;

import com.neram.pos.dto.BillItemResponseDTO;
import com.neram.pos.dto.BillRequestDTO;
import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.BillRefund;
import com.neram.pos.entity.Customer;
import com.neram.pos.entity.SystemNotification;
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
    private final MenuBranchService menuBranchService;
    private final com.neram.pos.plan.PlanService planService;
    private final AuditLogService auditLogService;
    private final GstSettingService gstSettingService;
    private final CashierShiftService cashierShiftService;
    private final RecipeService recipeService;
    private final BillRefundRepository billRefundRepository;
    private final PromotionService promotionService;
    private final com.neram.pos.repository.BillPaymentRepository billPaymentRepository;
    private final AppSettingService appSettingService;
    private final com.neram.pos.repository.SystemNotificationRepository notificationRepository;
    private final NotificationStreamService notificationStream;
    private final com.neram.pos.repository.CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final GiftCardService giftCardService;
    private final CouponService couponService;
    private final com.neram.pos.repository.ModifierRepository modifierRepository;
    private final com.neram.pos.repository.ComboGroupItemRepository comboGroupItemRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
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

    /** Start and end of the Bill Log period: one day, or a whole month when {@code monthly}. */
    private static LocalDateTime[] logPeriod(boolean monthly, LocalDate date, Integer year, Integer month) {
        if (monthly) {
            LocalDate first = LocalDate.of(year != null ? year : LocalDate.now().getYear(), month != null ? month : LocalDate.now().getMonthValue(), 1);
            return new LocalDateTime[]{first.atStartOfDay(), first.withDayOfMonth(first.lengthOfMonth()).atTime(LocalTime.MAX)};
        }
        LocalDate day = date != null ? date : LocalDate.now();
        return new LocalDateTime[]{day.atStartOfDay(), day.atTime(LocalTime.MAX)};
    }

    /** The Bill Log's filters as the native query takes them (see BillRepository.LOG_FILTER). */
    private record LogFilter(String loc, long staffId, boolean staffUnknown, String q) {
        static LogFilter of(UserRepository users, String orderType, String staff, String text) {
            String loc = orderType == null ? "" : orderType.trim().toUpperCase();
            if (!loc.equals("TABLE") && !loc.equals("OUTDOOR") && !loc.equals("TAKEAWAY")) loc = "";
            long staffId = 0;
            boolean unknown = false;
            if (staff != null && !staff.isBlank()) {
                var user = users.findByUsername(staff.trim());
                if (user.isPresent()) staffId = user.get().getId();
                else if ("Staff".equals(staff.trim())) unknown = true;   // the label shown for bills with no known creator
                else staffId = -1;                                        // a name nobody has: no bill matches
            }
            return new LogFilter(loc, staffId, unknown, com.neram.pos.util.LikeSearch.nativePattern(text));
        }
    }

    /**
     * One page of the Bill Log (live and soft-deleted bills, newest first) for a day or a month, narrowed by the log's filters:
     * order type (TABLE / OUTDOOR / TAKEAWAY), staff username, and a search over bill number, customer, tokens, table and staff.
     */
    public org.springframework.data.domain.Page<com.neram.pos.dto.BillLogRowDTO> getBillLog(boolean monthly, LocalDate date, Integer year, Integer month,
                                                                            String orderType, String staff, String q,
                                                                            org.springframework.data.domain.Pageable pageable) {
        LocalDateTime[] p = logPeriod(monthly, date, year, month);
        LogFilter f = LogFilter.of(userRepository, orderType, staff, q);
        // The query sorts itself; the request's page and size are all that is taken from the pageable.
        org.springframework.data.domain.Pageable plain = pageable == null || pageable.isUnpaged()
                ? org.springframework.data.domain.Pageable.unpaged()
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        // Only the bills on the page are converted (each conversion looks up names and refunds).
        return billRepository.searchBillLog(p[0], p[1], f.loc(), f.staffId(), f.staffUnknown(), f.q(), plain).map(this::toLogRow);
    }

    /** Count and revenue shown above the Bill Log for the same filters (soft-deleted bills excluded). */
    public com.neram.pos.dto.BillLogSummaryDTO getBillLogSummary(boolean monthly, LocalDate date, Integer year, Integer month,
                                                                 String orderType, String staff, String q) {
        LocalDateTime[] p = logPeriod(monthly, date, year, month);
        LogFilter f = LogFilter.of(userRepository, orderType, staff, q);
        BillRepository.BillLogTotals totals = billRepository.summarizeBillLog(p[0], p[1], f.loc(), f.staffId(), f.staffUnknown(), f.q());
        long count = totals.getBillCount() != null ? totals.getBillCount() : 0L;
        BigDecimal revenue = totals.getRevenue() != null ? totals.getRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal average = count > 0 ? revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        return com.neram.pos.dto.BillLogSummaryDTO.builder().billCount(count).revenue(revenue).average(average).build();
    }

    /** Usernames for the Bill Log's staff filter: everyone who created or printed a bill in the period. */
    public List<String> getBillLogStaff(boolean monthly, LocalDate date, Integer year, Integer month) {
        LocalDateTime[] p = logPeriod(monthly, date, year, month);
        List<String> names = new java.util.ArrayList<>(billRepository.findBillLogStaffNames(p[0], p[1]));
        if (billRepository.existsBillLogUnknownCreator(p[0], p[1])) names.add("Staff");
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
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

        if (dto.getCustomerId() != null) {
            bill.setCustomerId(dto.getCustomerId());
        } else if (dto.getCustomerPhone() != null && !dto.getCustomerPhone().isBlank()) {
            customerService.findByPhone(dto.getCustomerPhone()).ifPresent(c -> bill.setCustomerId(c.getId()));
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
                String modSnap = sanitizeModifiersSnapshot(itemDto.getModifiersSnapshot());
                String comboSnap = sanitizeComboSnapshot(itemDto.getMenuItemId(), itemDto.getComboSnapshot());
                item.setModifiersSnapshot(modSnap);
                item.setComboSnapshot(comboSnap);
                BigDecimal price = resolveLinePrice(itemDto.getMenuItemId(), item.getQuantity(), itemDto.getPrice(), true, modSnap, comboSnap);
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
            recipeService.settleBill(saved);
            handlePaymentTendersAndLoyalty(saved, dto);
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
        List<SplitPayments.Part> oldParts = partsOf(bill);

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
                bill.setPaymentSummary(null);   // choosing one mode again turns a split bill back into a single-mode bill
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
                String modSnap = sanitizeModifiersSnapshot(itemDto.getModifiersSnapshot());
                String comboSnap = sanitizeComboSnapshot(itemDto.getMenuItemId(), itemDto.getComboSnapshot());
                item.setModifiersSnapshot(modSnap);
                item.setComboSnapshot(comboSnap);
                BigDecimal price = resolveLinePrice(itemDto.getMenuItemId(), item.getQuantity(), itemDto.getPrice(), false, modSnap, comboSnap);
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

        // A split bill whose amount changed keeps its parts in proportion; one switched to a single mode drops them
        List<SplitPayments.Part> newParts = List.of();
        if (!oldParts.isEmpty() && bill.getPaymentSummary() != null) {
            newParts = SplitPayments.rescale(oldParts, bill.getNetAmount());
            bill.setPaymentSummary(SplitPayments.summary(newParts));
            bill.setPaymentMode(SplitPayments.primary(newParts).mode());
        }
        Bill saved = billRepository.save(bill);
        if (!oldParts.isEmpty()) saveParts(saved.getId(), newParts);

        // A paid bill edited from the Bill Log: the stock follows the new items (only the difference is taken or returned)
        if (saved.getStatus() == Bill.Status.PAID || saved.getStatus() == Bill.Status.PARTIALLY_REFUNDED) {
            recipeService.settleBill(saved);
        }

        if (saved.getStatus() == Bill.Status.PAID && saved.getShiftId() != null) {
            Bill.PaymentMode oldPaymentMode = null;
            try {
                oldPaymentMode = Bill.PaymentMode.valueOf(oldMode);
            } catch (Exception ignored) {}
            cashierShiftService.recordBillAdjustment(saved, oldPaymentMode, oldNet, oldParts, newParts);
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

    private static final List<Bill.Status> CLOSED_STATUSES = List.of(
            Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);

    /** Ids of the 5 newest BILL_REQUESTED or BILL_PRINTED bills: the only ones the Bill Log lets an admin/manager delete. */
    public List<Long> getDeletableBillIds() {
        return billRepository.findTop5ByStatusInOrderByCreatedAtDescIdDesc(CLOSED_STATUSES).stream().map(Bill::getId).collect(Collectors.toList());
    }

    /** Soft delete: the row stays (deleted_at set) but disappears from every list, report and total; recorded in the audit log. */
    @Transactional
    public void softDeleteBill(Long id, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason is required to delete a bill.");
        }
        Bill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));
        if (!getDeletableBillIds().contains(id)) {
            throw new IllegalStateException("Only the last 5 bills in the Bill Log can be deleted.");
        }
        String username = "SYSTEM";
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            username = auth.getName();
            userId = userRepository.findByUsername(username).map(User::getId).orElse(null);
        }
        String cleanReason = reason.trim();
        String billInfo = String.format("Bill #%s | %s | Net: ₹%s | Status: %s | Payment: %s",
                bill.getBillNumber(),
                bill.getTableNumber() != null ? "Table: " + bill.getTableNumber() : String.valueOf(bill.getOrderType()),
                bill.getNetAmount(), bill.getStatus(), bill.getPaymentMode());
        bill.setDeletedAt(LocalDateTime.now());
        bill.setDeletedBy(userId);
        bill.setDeletedByName(username);
        bill.setDeleteReason(cleanReason);
        billRepository.save(bill);
        auditLogService.log("DELETE_BILL", "BILL", id, billInfo, "Soft-deleted bill #" + bill.getBillNumber() + " | Reason: " + cleanReason);
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
        recipeService.restoreBill(bill, "bill deleted");
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
        boolean canSetDiscount = isManagerOrAdmin || hasPrintBillAccess();
        if (!isManagerOrAdmin) {
            if (!canSetDiscount && dto.getDiscountPercent() != null) {
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

        if (dto.getCustomerId() != null) {
            bill.setCustomerId(dto.getCustomerId());
        }

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
                String modSnap = sanitizeModifiersSnapshot(itemDto.getModifiersSnapshot());
                String comboSnap = sanitizeComboSnapshot(itemDto.getMenuItemId(), itemDto.getComboSnapshot());
                item.setModifiersSnapshot(modSnap);
                item.setComboSnapshot(comboSnap);

                String key = (itemDto.getMenuItemId() != null ? "ID_" + itemDto.getMenuItemId() : "NAME_" + itemDto.getItemName()) + "_" + Boolean.TRUE.equals(item.getIsTakeaway());
                int prevPrinted = printedQtyMap.getOrDefault(key, 0);
                // The printed quantity cannot exceed current quantity
                item.setPrintedQuantity(Math.min(prevPrinted, qty));

                // Lines already on the bill may be re-sent after the dish went unavailable; only new lines must be available.
                BigDecimal price = resolveLinePrice(itemDto.getMenuItemId(), qty, itemDto.getPrice(), !printedQtyMap.containsKey(key), modSnap, comboSnap);
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
        Double discToApply = canSetDiscount ? (dto.getDiscountPercent() != null ? dto.getDiscountPercent() : bill.getDiscountPercent()) : bill.getDiscountPercent();
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

    /** Statuses in which an order can still be moved: once the bill is requested/printed the paper copy would carry the old location. */
    private static final List<Bill.Status> MOVABLE_STATUSES = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED);

    /**
     * Moves a live order to another seat: table to table, table to outdoor/takeaway, outdoor/takeaway to a table.
     * If the target table already has an open order the caller may ask for a merge, which folds this order into that one.
     */
    @Transactional
    public BillResponseDTO changeOrderLocation(Long id, com.neram.pos.dto.ChangeLocationRequestDTO dto) {
        Bill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        if (bill.getStatus() == Bill.Status.BILL_REQUESTED || bill.getStatus() == Bill.Status.BILL_PRINTED) {
            throw new IllegalStateException("The bill has already been requested/printed for this order, so it can no longer be moved.");
        }
        if (!MOVABLE_STATUSES.contains(bill.getStatus())) {
            throw new IllegalStateException("Cannot change location of completed or cancelled order.");
        }

        String targetType = dto.getOrderType() != null ? dto.getOrderType().trim().toUpperCase() : "TABLE";
        String fromLocation = getLocationDisplay(bill);
        boolean kotAlreadySent = bill.getKotPrintedAt() != null;
        String reason = dto.getReason() != null && !dto.getReason().isBlank() ? dto.getReason().trim() : null;
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);
        Bill result;
        String action = "CHANGE_ORDER_LOCATION";

        if ("OUTDOOR".equals(targetType)) {
            if (bill.getOrderType() == Bill.OrderType.OUTDOOR) {
                throw new IllegalArgumentException("This order is already an outdoor order.");
            }
            String cName = dto.getCustomerName() != null && !dto.getCustomerName().trim().isBlank() ? dto.getCustomerName().trim() : (bill.getCustomerName() != null ? bill.getCustomerName().trim() : "");
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
            result = billRepository.save(bill);
        } else if ("TAKEAWAY".equals(targetType)) {
            if (bill.getOrderType() == Bill.OrderType.TAKEAWAY) {
                throw new IllegalArgumentException("This order is already a takeaway order.");
            }
            String cName = dto.getCustomerName() != null && !dto.getCustomerName().trim().isBlank() ? dto.getCustomerName().trim() : (bill.getCustomerName() != null && !bill.getCustomerName().isBlank() ? bill.getCustomerName().trim() : "Takeaway Guest");
            bill.setCustomerName(cName);
            if (bill.getTakeawayToken() == null) {
                bill.setTakeawayToken(dto.getTakeawayToken() != null && !dto.getTakeawayToken().isBlank() ? dto.getTakeawayToken().trim() : generateTakeawayToken());
            }
            bill.setOrderType(Bill.OrderType.TAKEAWAY);
            bill.setTableNumber(null);
            bill.setOutdoorToken(null);
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
            result = billRepository.save(bill);
        } else {
            if (dto.getTableNumber() == null || dto.getTableNumber().trim().isBlank()) {
                throw new IllegalArgumentException("Please select a table.");
            }
            String targetTable = dto.getTableNumber().trim();
            if (bill.getOrderType() == Bill.OrderType.TABLE && targetTable.equalsIgnoreCase(bill.getTableNumber())) {
                throw new IllegalArgumentException("This order is already on table " + bill.getTableNumber() + ".");
            }
            if (!diningTableRepository.existsByNameIgnoreCaseAndActiveTrue(targetTable)) {
                throw new IllegalArgumentException("Invalid table selected: " + targetTable);
            }
            // Same per-table lock createOrder takes, so a move and a new order cannot both claim the table.
            diningTableRepository.lockActiveByNameIgnoreCase(targetTable);

            Optional<Bill> existingActive = billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(targetTable, activeStatuses);
            if (existingActive.isPresent() && !existingActive.get().getId().equals(bill.getId())) {
                Bill target = existingActive.get();
                if (!Boolean.TRUE.equals(dto.getMergeIfOccupied())) {
                    throw new IllegalStateException("Table " + target.getTableNumber() + " already has an active order (Ref #" + target.getBillNumber()
                            + "). Choose merge to combine both orders.");
                }
                result = mergeOrders(bill, target);
                action = "MERGE_ORDERS";
            } else {
                bill.setTableNumber(targetTable);
                bill.setOrderType(Bill.OrderType.TABLE);
                bill.setOutdoorToken(null);
                bill.setTakeawayToken(null);
                if (dto.getCustomerName() != null && !dto.getCustomerName().trim().isBlank()) {
                    bill.setCustomerName(dto.getCustomerName().trim());
                }
                computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
                result = billRepository.save(bill);
            }
        }

        String toLocation = getLocationDisplay(result);
        String detail = "MERGE_ORDERS".equals(action)
                ? String.format("Merged order #%s (%s) into order #%s (%s)", bill.getBillNumber(), fromLocation, result.getBillNumber(), toLocation)
                : String.format("Moved order #%s from %s to %s", result.getBillNumber(), fromLocation, toLocation);
        if (reason != null) detail += " | Reason: " + reason;
        auditLogService.log(action, "BILL", result.getId(), fromLocation, detail);

        if (kotAlreadySent) {
            notifyKitchenOfMove(result, fromLocation, toLocation);
        }
        return toDTO(result);
    }

    /**
     * Folds {@code source} into {@code target} (two parties joining tables). Items with the same dish/customisation are
     * summed, already-printed quantities are carried over so nothing is re-sent to the kitchen, stock consumption is
     * re-attributed to the surviving bill, and the source order is closed as CANCELLED with a pointer to the survivor.
     */
    private Bill mergeOrders(Bill source, Bill target) {
        Bill lockedTarget = billRepository.findByIdForUpdate(target.getId()).orElse(target);
        if (!MOVABLE_STATUSES.contains(lockedTarget.getStatus())) {
            throw new IllegalStateException("Table " + lockedTarget.getTableNumber() + " has a bill that is already requested/printed, so orders cannot be merged into it.");
        }
        if (source.getStatus() == Bill.Status.KOT_REQUESTED || lockedTarget.getStatus() == Bill.Status.KOT_REQUESTED) {
            throw new IllegalStateException("A kitchen ticket is waiting to print for one of these orders. Wait for it to print, then merge.");
        }

        for (BillItem si : source.getItems()) {
            BillItem match = null;
            for (BillItem ti : lockedTarget.getItems()) {
                if (si.getMenuItemId() != null
                        && Objects.equals(ti.getMenuItemId(), si.getMenuItemId())
                        && Objects.equals(normalizeCustomization(ti.getCustomization()), normalizeCustomization(si.getCustomization()))
                        && Boolean.TRUE.equals(ti.getIsTakeaway()) == Boolean.TRUE.equals(si.getIsTakeaway())
                        && ti.getPrice() != null && si.getPrice() != null && ti.getPrice().compareTo(si.getPrice()) == 0) {
                    match = ti;
                    break;
                }
            }
            int sPrinted = si.getPrintedQuantity() != null ? si.getPrintedQuantity() : 0;
            if (match != null) {
                match.setQuantity(match.getQuantity() + si.getQuantity());
                match.setPrintedQuantity((match.getPrintedQuantity() != null ? match.getPrintedQuantity() : 0) + sPrinted);
                match.setSubtotal(match.getPrice().multiply(BigDecimal.valueOf(match.getQuantity())).setScale(2, RoundingMode.HALF_UP));
            } else {
                BillItem copy = new BillItem();
                copy.setBill(lockedTarget);
                copy.setMenuItemId(si.getMenuItemId());
                copy.setItemName(si.getItemName());
                copy.setQuantity(si.getQuantity());
                copy.setPrintedQuantity(sPrinted);
                copy.setPrice(si.getPrice());
                copy.setSubtotal(si.getSubtotal());
                copy.setCustomization(si.getCustomization());
                copy.setIsTakeaway(si.getIsTakeaway() != null ? si.getIsTakeaway() : false);
                lockedTarget.getItems().add(copy);
            }
        }

        BigDecimal subtotal = lockedTarget.getItems().stream()
                .map(i -> i.getSubtotal() != null ? i.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lockedTarget.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));

        // Items the kitchen already received on the source order count as sent on the survivor too.
        if (lockedTarget.getKotPrintedAt() == null && source.getKotPrintedAt() != null) {
            lockedTarget.setKotPrintedAt(source.getKotPrintedAt());
            if (lockedTarget.getKotNumber() == null) lockedTarget.setKotNumber(source.getKotNumber());
            if (lockedTarget.getStatus() == Bill.Status.ACTIVE) lockedTarget.setStatus(Bill.Status.KOT_PRINTED);
        }
        if ((lockedTarget.getCustomerName() == null || lockedTarget.getCustomerName().isBlank())
                && source.getCustomerName() != null && !source.getCustomerName().isBlank()) {
            lockedTarget.setCustomerName(source.getCustomerName());
        }
        computeBillAmounts(lockedTarget, lockedTarget.getDiscountPercent(), lockedTarget.getTaxPercent(), lockedTarget.getGstEnabled(),
                lockedTarget.getCgstPercent(), lockedTarget.getSgstPercent(), lockedTarget.getParcelCharge());

        recipeService.transferConsumption(source.getId(), lockedTarget.getId());

        User currentUser = getCurrentUser();
        source.setStatus(Bill.Status.CANCELLED);
        source.setVoidedAt(LocalDateTime.now());
        if (currentUser != null) {
            source.setVoidedBy(currentUser.getId());
            source.setVoidedByName(currentUser.getUsername());
        }
        source.setVoidReason("Merged into order #" + lockedTarget.getBillNumber());
        billRepository.save(source);
        return billRepository.save(lockedTarget);
    }

    private String normalizeCustomization(String c) {
        return c == null ? "" : c.trim();
    }

    /** Tells staff to inform the kitchen: a KOT with the old location has already been printed and is on the pass. */
    private void notifyKitchenOfMove(Bill bill, String from, String to) {
        if (notificationRepository == null) return;
        try {
            String msg = String.format("Order #%s moved from %s to %s after its kitchen ticket was printed. Let the kitchen know.",
                    bill.getBillNumber(), from, to);
            notificationRepository.save(SystemNotification.builder()
                    .type(SystemNotification.NotificationType.GENERAL)
                    .title("Order moved")
                    .message(msg.length() > 1000 ? msg.substring(0, 1000) : msg)
                    .referenceId(bill.getId())
                    .createdBy("System")
                    .status(SystemNotification.Status.UNREAD)
                    .createdAt(LocalDateTime.now())
                    .build());
            if (notificationStream != null) notificationStream.publishChangedAfterCommit();
        } catch (Exception e) {
            log.warn("Could not raise order-moved notification for bill #{}: {}", bill.getId(), e.getMessage());
        }
    }

    public List<BillResponseDTO> getActiveOrders() {
        List<Bill.Status> activeStatuses = List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED);
        return billRepository.findByStatusInOrderByCreatedAtAsc(activeStatuses)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Open orders that have been sent to the kitchen, oldest first, as slim tickets for the kitchen display. The branch limit of
     * the caller applies as for any other bill query. No prices, totals or customer details leave here.
     */
    public List<com.neram.pos.dto.KitchenTicketDTO> getKitchenTickets() {
        List<Bill.Status> sentToKitchen = List.of(Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED);
        return billRepository.findByStatusInOrderByCreatedAtAsc(sentToKitchen).stream()
                .map(this::toDTO)
                .filter(b -> b.getItems() != null && !b.getItems().isEmpty())
                .map(b -> com.neram.pos.dto.KitchenTicketDTO.builder()
                        .id(b.getId())
                        .billNumber(b.getBillNumber())
                        .orderType(b.getOrderType())
                        .tableNumber(b.getTableNumber())
                        .outdoorToken(b.getOutdoorToken())
                        .takeawayToken(b.getTakeawayToken())
                        .status(b.getStatus())
                        .createdAt(b.getCreatedAt())
                        .kotPrintedAt(b.getKotPrintedAt())
                        .items(b.getItems().stream().map(i -> com.neram.pos.dto.KitchenTicketDTO.Line.builder()
                                .name(i.getItemName())
                                .quantity(i.getQuantity())
                                .printedQuantity(i.getPrintedQuantity())
                                .note(i.getCustomization())
                                .extras(extraNames(i.getModifiersSnapshot(), i.getComboSnapshot()))
                                .takeaway(i.getIsTakeaway())
                                .build()).toList())
                        .build())
                .toList();
    }

    /** The names of the add-ons and combo choices on a line (their prices stay out of the kitchen's view). */
    private List<String> extraNames(String modifiersSnapshot, String comboSnapshot) {
        List<String> names = new java.util.ArrayList<>();
        for (String snapshot : new String[]{modifiersSnapshot, comboSnapshot}) {
            if (snapshot == null || snapshot.isBlank()) continue;
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(snapshot);
                if (!root.isArray()) continue;
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    for (String field : new String[]{"name", "itemName", "item_name", "menuItemName"}) {
                        if (node.hasNonNull(field) && !node.get(field).asText().isBlank()) {
                            names.add(node.get(field).asText());
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
                // a snapshot that cannot be read just shows no extras
            }
        }
        return names;
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

        // Stock is taken when the bill is settled (payment), not when the kitchen ticket prints.
        Bill saved = billRepository.save(bill);

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
            if (dto.getCustomerId() != null) {
                bill.setCustomerId(dto.getCustomerId());
            } else if (dto.getCustomerPhone() != null && !dto.getCustomerPhone().isBlank()) {
                customerService.findByPhone(dto.getCustomerPhone()).ifPresent(c -> bill.setCustomerId(c.getId()));
            }
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
        recipeService.settleBill(saved);
        handlePaymentTendersAndLoyalty(saved, dto);
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

        // An employee who was given the print-bill access may set the discount; tax, payment mode and parcel charge stay as the order holds them
        if (dto != null && isManagerOrAdmin()) {
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
            Double discount = dto != null && dto.getDiscountPercent() != null && hasPrintBillAccess() ? Math.max(0.0, dto.getDiscountPercent()) : bill.getDiscountPercent();
            computeBillAmounts(bill, discount, bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
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
                String.format("%s printed Bill #%s | %s | Net: ₹%s | Payment Status: PENDING", isManagerOrAdmin() ? "Manager" : "Employee",
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

        if (dto != null) {
            if (dto.getCustomerId() != null) {
                bill.setCustomerId(dto.getCustomerId());
            } else if (dto.getCustomerPhone() != null && !dto.getCustomerPhone().isBlank()) {
                customerService.findByPhone(dto.getCustomerPhone()).ifPresent(c -> bill.setCustomerId(c.getId()));
            }
            if (dto.getPaymentMode() != null) {
                try {
                    bill.setPaymentMode(Bill.PaymentMode.valueOf(dto.getPaymentMode().toUpperCase()));
                } catch (Exception ignored) {}
            }
        } else if (bill.getPaymentMode() == null) {
            bill.setPaymentMode(Bill.PaymentMode.CASH);
        }

        if (dto != null && dto.getCouponCode() != null && !dto.getCouponCode().isBlank()) {
            planService.require(com.neram.pos.plan.Feature.CRM);
            BigDecimal couponDisc = couponService.applyCouponSafely(dto.getCouponCode(), bill.getCustomerId(), bill.getId(), bill.getSubtotal());
            bill.setCouponDiscount(couponDisc);
        }

        if (dto != null && (dto.getDiscountPercent() != null || dto.getTaxPercent() != null || dto.getParcelCharge() != null)) {
            computeBillAmounts(bill, dto.getDiscountPercent(), dto.getTaxPercent(), dto.getGstEnabled(), dto.getCgstPercent(), dto.getSgstPercent(), dto.getParcelCharge());
        } else {
            computeBillAmounts(bill, bill.getDiscountPercent(), bill.getTaxPercent(), bill.getGstEnabled(), bill.getCgstPercent(), bill.getSgstPercent(), bill.getParcelCharge());
        }

        bill.setTipAmount(validTip(dto != null ? dto.getTipAmount() : null));

        // Paid in more than one way (cash + UPI...): the parts must add up to the bill. One part is just a normal payment.
        List<SplitPayments.Part> split = SplitPayments.validate(dto != null ? dto.getPayments() : null, bill.getNetAmount());
        if (split.size() == 1) {
            bill.setPaymentMode(split.get(0).mode());
            split = List.of();
        } else if (split.size() > 1) {
            bill.setPaymentMode(SplitPayments.primary(split).mode());
        }
        bill.setPaymentSummary(split.size() > 1 ? SplitPayments.summary(split) : null);

        bill.setStatus(Bill.Status.PAID);
        if (bill.getPrintedAt() == null) {
            bill.setPrintedAt(LocalDateTime.now());
        }

        Bill saved = billRepository.save(bill);
        if (split.size() > 1) saveParts(saved.getId(), split);
        cashierShiftService.recordBillPayment(saved);
        recipeService.settleBill(saved);
        handlePaymentTendersAndLoyalty(saved, dto);
        saved = billRepository.save(saved);

        BillResponseDTO res = toDTO(saved);
        auditLogService.log("CONFIRM_PAYMENT", "BILL", saved.getId(), null,
                String.format("Manager confirmed payment for Bill #%s | %s | Net: ₹%s | Mode: %s",
                        saved.getBillNumber(), getLocationDisplay(saved), saved.getNetAmount(), saved.getPaymentMode()));
        return res;
    }

    private void handlePaymentTendersAndLoyalty(Bill bill, BillRequestDTO dto) {
        if (bill == null || bill.getId() == null) return;
        List<BillRequestDTO.PaymentPart> parts = dto != null ? dto.getPayments() : null;

        // 1. Gift card redemption
        if (bill.getPaymentMode() == Bill.PaymentMode.GIFT_CARD) {
            planService.require(com.neram.pos.plan.Feature.CRM);
            String gcCode = dto != null && dto.getGiftCardCode() != null ? dto.getGiftCardCode().trim() : null;
            if (gcCode == null || gcCode.isBlank()) {
                throw new IllegalArgumentException("Gift card code is required for GIFT_CARD payment.");
            }
            giftCardService.redeem(gcCode, bill.getNetAmount(), bill.getId());
        } else if (parts != null) {
            for (BillRequestDTO.PaymentPart p : parts) {
                if ("GIFT_CARD".equalsIgnoreCase(p.getMode()) && p.getAmount() != null && p.getAmount().signum() > 0) {
                    planService.require(com.neram.pos.plan.Feature.CRM);
                    String code = p.getReference() != null && !p.getReference().isBlank() ? p.getReference().trim() : (dto != null && dto.getGiftCardCode() != null ? dto.getGiftCardCode().trim() : null);
                    if (code == null || code.isBlank()) {
                        throw new IllegalArgumentException("Gift card code is required for GIFT_CARD payment.");
                    }
                    giftCardService.redeem(code, p.getAmount(), bill.getId());
                }
            }
        }

        // 2. Loyalty points redemption
        if (bill.getPaymentMode() == Bill.PaymentMode.LOYALTY_POINTS) {
            planService.require(com.neram.pos.plan.Feature.CRM);
            if (bill.getCustomerId() == null) {
                throw new IllegalArgumentException("Customer profile is required for LOYALTY_POINTS payment.");
            }
            int pts = bill.getNetAmount().intValue();
            customerService.redeemPoints(bill.getCustomerId(), bill.getId(), pts);
        } else if (parts != null) {
            for (BillRequestDTO.PaymentPart p : parts) {
                if ("LOYALTY_POINTS".equalsIgnoreCase(p.getMode()) && p.getAmount() != null && p.getAmount().signum() > 0) {
                    planService.require(com.neram.pos.plan.Feature.CRM);
                    if (bill.getCustomerId() == null) {
                        throw new IllegalArgumentException("Customer profile is required for LOYALTY_POINTS payment.");
                    }
                    customerService.redeemPoints(bill.getCustomerId(), bill.getId(), p.getAmount().intValue());
                }
            }
        } else if (dto != null && dto.getRedeemLoyaltyPoints() != null && dto.getRedeemLoyaltyPoints() > 0) {
            planService.require(com.neram.pos.plan.Feature.CRM);
            if (bill.getCustomerId() == null) {
                throw new IllegalArgumentException("Customer profile is required for LOYALTY_POINTS redemption.");
            }
            customerService.redeemPoints(bill.getCustomerId(), bill.getId(), dto.getRedeemLoyaltyPoints());
        }

        // 3. Loyalty points earning strictly on net taxable merchandise paid via monetary tenders
        if (bill.getCustomerId() != null) {
            BigDecimal subtotal = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;
            BigDecimal discAmt = bill.getDiscountAmount() != null ? bill.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal promoDiscAmt = bill.getPromotionDiscountAmount() != null ? bill.getPromotionDiscountAmount() : BigDecimal.ZERO;
            BigDecimal taxableMerchandise = subtotal.subtract(discAmt).subtract(promoDiscAmt).max(BigDecimal.ZERO);

            BigDecimal monetarySpend = BigDecimal.ZERO;
            if (parts != null && !parts.isEmpty()) {
                for (BillRequestDTO.PaymentPart p : parts) {
                    String m = p.getMode() != null ? p.getMode().toUpperCase() : "";
                    if ("CASH".equals(m) || "UPI".equals(m) || "CARD".equals(m)) {
                        monetarySpend = monetarySpend.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                    }
                }
            } else {
                Bill.PaymentMode m = bill.getPaymentMode();
                if (m == Bill.PaymentMode.CASH || m == Bill.PaymentMode.UPI || m == Bill.PaymentMode.CARD) {
                    monetarySpend = bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO;
                }
            }
            BigDecimal eligibleMerchandiseSpend = taxableMerchandise.min(monetarySpend);
            // Also called when nothing was paid in money (a bill paid with points): it is still a purchase, so the validity period restarts.
            customerService.earnPoints(bill.getCustomerId(), bill.getId(), eligibleMerchandiseSpend);
        }

        // 4. Coupon redemption
        if (dto != null && dto.getCouponCode() != null && !dto.getCouponCode().isBlank() && (bill.getCouponDiscount() == null || bill.getCouponDiscount().compareTo(BigDecimal.ZERO) == 0)) {
            BigDecimal couponDisc = couponService.applyCouponSafely(dto.getCouponCode(), bill.getCustomerId(), bill.getId(), bill.getSubtotal());
            bill.setCouponDiscount(couponDisc);
            computeBillAmounts(bill, dto.getDiscountPercent(), dto.getTaxPercent(), dto.getGstEnabled(), dto.getCgstPercent(), dto.getSgstPercent(), dto.getParcelCharge());
        }
    }

    private void saveParts(Long billId, List<SplitPayments.Part> parts) {
        billPaymentRepository.deleteByBillId(billId);
        if (parts.size() < 2) return;
        billPaymentRepository.saveAll(parts.stream().map(p -> new com.neram.pos.entity.BillPayment(null, billId, p.mode().name(), p.amount(), p.reference(), LocalDateTime.now())).collect(Collectors.toList()));
    }

    private List<SplitPayments.Part> partsOf(Bill b) {
        if (b == null || b.getId() == null || b.getPaymentSummary() == null) return List.of();
        return billPaymentRepository.findByBillIdOrderByIdAsc(b.getId()).stream()
                .map(p -> new SplitPayments.Part(Bill.PaymentMode.valueOf(p.getMode()), p.getAmount(), p.getReference())).collect(Collectors.toList());
    }

    /** A tip must be zero or more and, as a guard against typing mistakes, at most 100000. */
    static BigDecimal validTip(BigDecimal tip) {
        if (tip == null) return BigDecimal.ZERO.setScale(2);
        if (tip.signum() < 0) throw new IllegalArgumentException("Tip cannot be negative.");
        if (tip.compareTo(new BigDecimal("100000")) > 0) throw new IllegalArgumentException("Tip looks too large. Check the amount.");
        return tip.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Bill numbers are unique across the whole cafe, so the next number is worked out over all branches (not just the
     * caller's) and including soft-deleted bills. The prefix comes from the cafe's own code, never another brand's.
     */
    private String generateBillNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = billPrefix();
        return com.neram.pos.access.OutletContext.callUnrestricted(() -> {
            long count = billRepository.countAllBillsIncludingDeleted() + 1;
            String candidate = String.format("%s-%s-%04d", prefix, datePart, count);
            while (billRepository.billNumberTaken(candidate)) {
                count++;
                candidate = String.format("%s-%s-%04d", prefix, datePart, count);
            }
            return candidate;
        });
    }

    static String billPrefix() {
        String code = com.neram.pos.tenancy.TenantContext.current().map(com.neram.pos.tenancy.TenantInfo::code).orElse("bill");
        String letters = code.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return letters.isEmpty() ? "BILL" : letters.substring(0, Math.min(3, letters.length()));
    }


    /** The slim row the Bill Log lists: no item lines, tax split or KOT detail (those come from GET /bills/{id}). */
    private com.neram.pos.dto.BillLogRowDTO toLogRow(Bill b) {
        String creatorName = b.getCreatedBy() == null ? "Staff" : userRepository.findById(b.getCreatedBy()).map(User::getUsername).orElse("Staff");
        String printerName = b.getPrintedBy() == null ? null : userRepository.findById(b.getPrintedBy()).map(User::getUsername).orElse(null);
        String oType = b.getOrderType() != null ? b.getOrderType().name() : (b.getTakeawayToken() != null || (b.getTableNumber() != null && b.getTableNumber().equalsIgnoreCase("Takeaway")) ? "TAKEAWAY" : (b.getOutdoorToken() != null ? "OUTDOOR" : "TABLE"));
        BigDecimal refunded = billRefundRepository.sumSuccessfulRefundsByBillId(b.getId());
        return com.neram.pos.dto.BillLogRowDTO.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .orderType(oType)
                .tableNumber(b.getTableNumber())
                .outdoorToken(b.getOutdoorToken())
                .takeawayToken(b.getTakeawayToken())
                .customerName(b.getCustomerName())
                .createdByName(creatorName)
                .printedByName(printerName)
                .createdAt(b.getCreatedAt())
                .itemCount(b.getItems() != null ? b.getItems().size() : 0)
                .paymentMode(b.getPaymentMode().name())
                .netAmount(b.getNetAmount())
                .status(b.getStatus().name())
                .totalRefunded(refunded != null ? refunded : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .deletedAt(b.getDeletedAt())
                .deletedByName(b.getDeletedByName())
                .deleteReason(b.getDeleteReason())
                .build();
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
                        .modifiersSnapshot(it.getModifiersSnapshot())
                        .comboSnapshot(it.getComboSnapshot())
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
                .customerId(b.getCustomerId())
                .customerPhone(b.getCustomerId() != null ? customerRepository.findById(b.getCustomerId()).map(Customer::getPhone).orElse(null) : null)
                .businessDate(b.getBusinessDate())
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
                .couponId(b.getCouponId())
                .couponDiscount(b.getCouponDiscount() != null ? b.getCouponDiscount() : BigDecimal.ZERO)
                .netAmount(b.getNetAmount())
                .tipAmount(b.getTipAmount() != null ? b.getTipAmount() : BigDecimal.ZERO)
                .paymentSummary(b.getPaymentSummary())
                .payments(partsOf(b).isEmpty() ? null : partsOf(b).stream().map(p -> new BillRequestDTO.PaymentPart(p.mode().name(), p.amount(), p.reference())).collect(Collectors.toList()))
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
                .deletedAt(b.getDeletedAt())
                .deletedByName(b.getDeletedByName())
                .deleteReason(b.getDeleteReason())
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
        return resolveLinePrice(menuItemId, quantity, clientPrice, requireAvailable, null, null);
    }

    private BigDecimal resolveLinePrice(Long menuItemId, Integer quantity, BigDecimal clientPrice, boolean requireAvailable, String modifiersSnapshot, String comboSnapshot) {
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
        // The branch the order is taken in decides the price and whether the item is sold out.
        MenuBranchService.Effective here = menuBranchService.effective(menuItem);
        if (requireAvailable && (!here.available() || Boolean.FALSE.equals(menuItem.getActive()))) {
            throw new IllegalArgumentException("'" + menuItem.getName() + "' is currently unavailable");
        }
        BigDecimal extra = calculateSnapshotAdditions(menuItemId, modifiersSnapshot, comboSnapshot);
        BigDecimal price = (privileged && clientPrice != null) ? clientPrice : here.price().add(extra);
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String sanitizeModifiersSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return snapshot;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(snapshot);
            if (root.isArray()) {
                com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    com.fasterxml.jackson.databind.node.ObjectNode obj = node.deepCopy();
                    Long modId = node.has("modifier_id") ? node.get("modifier_id").asLong() :
                            (node.has("id") ? node.get("id").asLong() : null);
                    if (modId != null) {
                        modifierRepository.findById(modId).ifPresent(mod -> {
                            obj.put("id", mod.getId());
                            obj.put("modifier_id", mod.getId());
                            obj.put("name", mod.getName());
                            obj.put("price", mod.getPriceAdjustment());
                            obj.put("priceAdjustment", mod.getPriceAdjustment());
                            if (mod.getInventoryItemId() != null) {
                                obj.put("inventoryItemId", mod.getInventoryItemId());
                                obj.put("inventory_item_id", mod.getInventoryItemId());
                            }
                            if (mod.getInventoryQuantity() != null) {
                                obj.put("inventoryQuantity", mod.getInventoryQuantity());
                                obj.put("inventory_quantity", mod.getInventoryQuantity());
                            }
                        });
                    }
                    arr.add(obj);
                }
                return objectMapper.writeValueAsString(arr);
            }
        } catch (Exception e) {
            log.warn("Failed to sanitize modifiers snapshot: {}", e.getMessage());
        }
        return snapshot;
    }

    private String sanitizeComboSnapshot(Long parentDishId, String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return snapshot;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(snapshot);
            if (root.isArray()) {
                com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    com.fasterxml.jackson.databind.node.ObjectNode obj = node.deepCopy();
                    Long childId = node.has("menu_item_id") ? node.get("menu_item_id").asLong() :
                            (node.has("menuItemId") ? node.get("menuItemId").asLong() : null);
                    if (childId != null) {
                        menuItemRepository.findById(childId).ifPresent(dish -> {
                            obj.put("dish_name", dish.getName());
                            obj.put("dishName", dish.getName());
                        });
                        if (parentDishId != null) {
                            List<com.neram.pos.entity.ComboGroupItem> items = comboGroupItemRepository.findByDishIdAndChildId(parentDishId, childId);
                            if (!items.isEmpty()) {
                                BigDecimal extra = items.get(0).getAdditionalPrice();
                                obj.put("extra_price", extra);
                                obj.put("extraPrice", extra);
                            }
                        }
                    }
                    arr.add(obj);
                }
                return objectMapper.writeValueAsString(arr);
            }
        } catch (Exception e) {
            log.warn("Failed to sanitize combo snapshot: {}", e.getMessage());
        }
        return snapshot;
    }

    private BigDecimal calculateSnapshotAdditions(Long parentDishId, String modSnapshot, String comboSnapshot) {
        BigDecimal extra = BigDecimal.ZERO;
        if (modSnapshot != null && !modSnapshot.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(modSnapshot);
                if (root.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : root) {
                        Long modId = node.has("modifier_id") ? node.get("modifier_id").asLong() :
                                (node.has("id") ? node.get("id").asLong() : null);
                        if (modId != null) {
                            com.neram.pos.entity.Modifier mod = modifierRepository.findById(modId).orElse(null);
                            if (mod != null && mod.getPriceAdjustment() != null) {
                                extra = extra.add(mod.getPriceAdjustment());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (comboSnapshot != null && !comboSnapshot.isBlank() && parentDishId != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(comboSnapshot);
                if (root.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : root) {
                        Long childId = node.has("menu_item_id") ? node.get("menu_item_id").asLong() :
                                (node.has("menuItemId") ? node.get("menuItemId").asLong() : null);
                        if (childId != null) {
                            List<com.neram.pos.entity.ComboGroupItem> items = comboGroupItemRepository.findByDishIdAndChildId(parentDishId, childId);
                            if (!items.isEmpty() && items.get(0).getAdditionalPrice() != null) {
                                extra = extra.add(items.get(0).getAdditionalPrice());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return extra;
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

        BigDecimal couponDisc = bill.getCouponDiscount() != null ? bill.getCouponDiscount() : BigDecimal.ZERO;
        if (promoPct != null && promoPct > 0) {
            BigDecimal promoPctBd = BigDecimal.valueOf(promoPct);
            BigDecimal promoDiscAmt = totalAfterGstAndRoundOff.multiply(promoPctBd)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            bill.setPromotionId(promoId);
            bill.setPromotionName(promoName);
            bill.setPromotionPercentage(promoPct);
            bill.setPromotionDiscountAmount(promoDiscAmt);

            BigDecimal finalNet = totalAfterGstAndRoundOff.subtract(promoDiscAmt).subtract(couponDisc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            bill.setNetAmount(finalNet);
        } else {
            bill.setPromotionId(null);
            bill.setPromotionName(null);
            bill.setPromotionPercentage(null);
            bill.setPromotionDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setNetAmount(totalAfterGstAndRoundOff.subtract(couponDisc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /** The caller holds the print-bill access (admins and managers always do; an employee only while it is switched on for them). */
    public boolean hasPrintBillAccess() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                return auth.getAuthorities().stream().anyMatch(a -> "PERM_PRINT_BILL".equals(a.getAuthority()));
            }
        } catch (Exception ignored) {}
        return false;
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
