package com.neram.pos.service;

import com.neram.pos.dto.*;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepo;
    private final SupplierInvoiceRepository invoiceRepo;
    private final SupplierPaymentRepository paymentRepo;
    private final SupplierInvoiceItemRepository invoiceItemRepo;
    private final InventoryItemRepository inventoryItemRepo;
    private final InventoryTransactionRepository transactionRepo;
    private final UserRepository userRepo;
    private final AuditLogService auditLog;

    // ─── Supplier CRUD ──────────────────────────────────────────────────────────

    public List<SupplierDTO> getAllSuppliers(boolean activeOnly) {
        List<Supplier> suppliers = activeOnly
                ? supplierRepo.findByActiveTrue()
                : supplierRepo.findAll();
        return suppliers.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public SupplierDTO getSupplier(Long id) {
        Supplier s = supplierRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
        return toDTO(s);
    }

    public List<SupplierDTO> searchSuppliers(String query, String phone, String category, Boolean activeOnly) {
        List<Supplier> suppliers = (activeOnly != null && activeOnly)
                ? supplierRepo.findByActiveTrue()
                : supplierRepo.findAll();

        return suppliers.stream()
                .filter(s -> {
                    if (query != null && !query.trim().isEmpty()) {
                        String q = query.trim().toLowerCase();
                        boolean match = (s.getName() != null && s.getName().toLowerCase().contains(q)) ||
                                        (s.getContactPerson() != null && s.getContactPerson().toLowerCase().contains(q)) ||
                                        (s.getEmail() != null && s.getEmail().toLowerCase().contains(q)) ||
                                        (s.getGstNumber() != null && s.getGstNumber().toLowerCase().contains(q)) ||
                                        (s.getAddress() != null && s.getAddress().toLowerCase().contains(q)) ||
                                        (s.getUpiId() != null && s.getUpiId().toLowerCase().contains(q)) ||
                                        (s.getCategory() != null && s.getCategory().toLowerCase().contains(q)) ||
                                        (s.getNotes() != null && s.getNotes().toLowerCase().contains(q));
                        if (!match) return false;
                    }
                    if (phone != null && !phone.trim().isEmpty()) {
                        String p = phone.trim().replaceAll("[^0-9+]", "");
                        String rawPhone = s.getPhone() != null ? s.getPhone().replaceAll("[^0-9+]", "") : "";
                        String rawBankAcc = s.getBankAccount() != null ? s.getBankAccount() : "";
                        String rawGst = s.getGstNumber() != null ? s.getGstNumber() : "";
                        boolean phoneMatch = rawPhone.contains(p) || rawBankAcc.contains(p) || rawGst.toLowerCase().contains(phone.trim().toLowerCase());
                        if (!phoneMatch) return false;
                    }
                    if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
                        if (s.getCategory() == null || !s.getCategory().equalsIgnoreCase(category.trim())) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SupplierDetailsDTO getSupplierDetails(Long id) {
        SupplierDTO supplier = getSupplier(id);
        List<SupplierInvoiceDTO> invoices = getInvoicesBySupplier(id);
        List<SupplierPaymentDTO> payments = getPaymentsBySupplier(id);
        return new SupplierDetailsDTO(supplier, invoices, payments);
    }

    @Transactional
    public SupplierDTO createSupplier(SupplierRequestDTO req, String performedBy) {
        Supplier s = new Supplier();
        mapRequest(req, s);
        s = supplierRepo.save(s);
        auditLog.log("CREATE", "SUPPLIER", s.getId(), null, s.getName());
        return toDTO(s);
    }

    @Transactional
    public SupplierDTO updateSupplier(Long id, SupplierRequestDTO req, String performedBy) {
        Supplier s = supplierRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
        String old = s.getName();
        mapRequest(req, s);
        s.setUpdatedAt(LocalDateTime.now());
        s = supplierRepo.save(s);
        auditLog.log("UPDATE", "SUPPLIER", s.getId(), old, s.getName());
        return toDTO(s);
    }

    @Transactional
    public void deleteSupplier(Long id, String performedBy) {
        Supplier s = supplierRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
        s.setActive(false);
        s.setUpdatedAt(LocalDateTime.now());
        supplierRepo.save(s);
        auditLog.log("DELETE", "SUPPLIER", id, s.getName(), "DEACTIVATED");
    }

    // ─── Invoice CRUD ────────────────────────────────────────────────────────────

    public List<SupplierInvoiceDTO> getAllInvoices() {
        return invoiceRepo.findAll(org.springframework.data.domain.Sort.by("createdAt").descending())
                .stream().map(this::toInvoiceDTO).collect(Collectors.toList());
    }

    public List<SupplierInvoiceDTO> getInvoicesBySupplier(Long supplierId) {
        return invoiceRepo.findBySupplierId(supplierId)
                .stream().map(this::toInvoiceDTO).collect(Collectors.toList());
    }

    public List<SupplierInvoiceDTO> getOutstandingInvoices() {
        return invoiceRepo.findAllOutstanding()
                .stream().map(this::toInvoiceDTO).collect(Collectors.toList());
    }

    private static final org.springframework.data.domain.Sort NEWEST_INVOICE_FIRST = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("id"));
    private static final org.springframework.data.domain.Sort NEWEST_PAYMENT_FIRST = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("paymentDate"), org.springframework.data.domain.Sort.Order.desc("id"));

    /** One page of invoices, newest first. supplierId and status (UNPAID / PARTIAL / PAID / OVERDUE) are optional filters. */
    public org.springframework.data.domain.Page<SupplierInvoiceDTO> getInvoices(Long supplierId, String status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Pageable sorted = com.neram.pos.util.PagedResponse.withSort(pageable, NEWEST_INVOICE_FIRST);
        SupplierInvoice.InvoiceStatus st = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) ? null
                : SupplierInvoice.InvoiceStatus.valueOf(status.trim().toUpperCase());
        org.springframework.data.domain.Page<SupplierInvoice> page;
        if (supplierId != null && st != null) page = invoiceRepo.findBySupplierIdAndStatus(supplierId, st, sorted);
        else if (supplierId != null) page = invoiceRepo.findBySupplierId(supplierId, sorted);
        else if (st != null) page = invoiceRepo.findByStatus(st, sorted);
        else page = invoiceRepo.findAll(sorted);
        return page.map(this::toInvoiceDTO);
    }

    /** One page of payments, newest first, optionally for one supplier. */
    public org.springframework.data.domain.Page<SupplierPaymentDTO> getPayments(Long supplierId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Pageable sorted = com.neram.pos.util.PagedResponse.withSort(pageable, NEWEST_PAYMENT_FIRST);
        return (supplierId != null ? paymentRepo.findBySupplierId(supplierId, sorted) : paymentRepo.findAll(sorted)).map(this::toPaymentDTO);
    }

    /**
     * One page of suppliers (ordered by id) for the Suppliers screens. status: ACTIVE / INACTIVE (anything else = all).
     * scope "overview" searches name, contact, phone, email and category; any other scope is the directory search over
     * name, contact, email, GST, UPI, address, notes and bank. phone matches phone / bank account / GST ignoring punctuation.
     */
    public org.springframework.data.domain.Page<SupplierDTO> getSuppliers(String status, String category, String text, String phone,
                                                                          boolean overviewScope, org.springframework.data.domain.Pageable pageable) {
        String q = text == null ? "" : text.trim().toLowerCase();
        String p = phone == null ? "" : phone.trim().toLowerCase().replaceAll("[^0-9+a-z]", "");
        String cat = category == null ? "" : category.trim().toLowerCase();
        List<Supplier> matches = supplierRepo.findAll(org.springframework.data.domain.Sort.by("id")).stream()
                .filter(s -> !"ACTIVE".equalsIgnoreCase(status) || !Boolean.FALSE.equals(s.getActive()))
                .filter(s -> !"INACTIVE".equalsIgnoreCase(status) || Boolean.FALSE.equals(s.getActive()))
                .filter(s -> cat.isEmpty() || (s.getCategory() != null && s.getCategory().toLowerCase().equals(cat)))
                .filter(s -> q.isEmpty() || (overviewScope
                        ? containsAny(q, s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(), s.getCategory())
                        : containsAny(q, s.getName(), s.getContactPerson(), s.getEmail(), s.getGstNumber(), s.getUpiId(), s.getAddress(), s.getNotes(), s.getBankName())))
                .filter(s -> p.isEmpty() || containsAny(p, digitsAndLetters(s.getPhone()), digitsAndLetters(s.getBankAccount()), digitsAndLetters(s.getGstNumber())))
                .collect(Collectors.toList());
        // Only the suppliers on the page are converted (each carries a ledger summary query).
        org.springframework.data.domain.Page<Supplier> page = com.neram.pos.util.PagedResponse.slice(matches, pageable);
        return page.map(this::toDTO);
    }

    private static boolean containsAny(String needle, String... haystacks) {
        for (String h : haystacks) {
            if (h != null && h.toLowerCase().contains(needle)) return true;
        }
        return false;
    }

    private static String digitsAndLetters(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^0-9+a-z]", "");
    }

    public SupplierInvoiceDTO getInvoice(Long id) {
        SupplierInvoice inv = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + id));
        return toInvoiceDTO(inv);
    }

    @Transactional
    public SupplierInvoiceDTO createInvoice(SupplierInvoiceRequestDTO req, Long userId, String performedBy) {
        Supplier s = supplierRepo.findById(req.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + req.getSupplierId()));

        User user = null;
        if (userId != null) {
            user = userRepo.findById(userId).orElse(null);
        } else if (performedBy != null) {
            user = userRepo.findByUsername(performedBy).orElse(null);
            if (user != null) userId = user.getId();
        }
        String userRole = (user != null && user.getRole() != null) ? user.getRole().name() : "MANAGER";
        String userName = performedBy != null ? performedBy : (user != null ? user.getUsername() : "SYSTEM");

        SupplierInvoice inv = new SupplierInvoice();
        inv.setSupplierId(req.getSupplierId());
        inv.setInvoiceNumber(req.getInvoiceNumber());
        inv.setInvoiceDate(req.getInvoiceDate() != null ? req.getInvoiceDate() : LocalDate.now());
        inv.setDueDate(req.getDueDate() != null ? req.getDueDate()
                : inv.getInvoiceDate().plusDays(s.getPaymentTerms() != null ? s.getPaymentTerms() : 30));
        inv.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        inv.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        inv.setOutstandingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        inv.setStatus(SupplierInvoice.InvoiceStatus.UNPAID);
        inv.setDescription(req.getDescription());
        inv.setItemsReceived(req.getItemsReceived());
        inv.setCreatedBy(userId);
        inv = invoiceRepo.save(inv);

        // Process line items if provided
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        BigDecimal calculatedTotalItemGst = BigDecimal.ZERO;
        boolean hasExplicitItemGst = false;
        StringBuilder itemSummary = new StringBuilder();
        Set<Long> seenInventoryItemIds = new HashSet<>();
        Set<String> seenProductNames = new HashSet<>();

        if (req.getItems() != null && !req.getItems().isEmpty()) {
            for (SupplierInvoiceItemDTO itemReq : req.getItems()) {
                if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Quantity must be greater than zero for all invoice items");
                }
                if (itemReq.getUnitPrice() == null || itemReq.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Price / unit cannot be negative");
                }

                InventoryItem invItem;
                if (itemReq.getInventoryItemId() != null && itemReq.getInventoryItemId() > 0) {
                    if (seenInventoryItemIds.contains(itemReq.getInventoryItemId())) {
                        throw new IllegalArgumentException("Duplicate product detected in invoice: Item ID " + itemReq.getInventoryItemId());
                    }
                    seenInventoryItemIds.add(itemReq.getInventoryItemId());
                    invItem = inventoryItemRepo.findById(itemReq.getInventoryItemId())
                            .orElseThrow(() -> new RuntimeException("Inventory item not found: " + itemReq.getInventoryItemId()));
                } else if (itemReq.getProductName() != null && !itemReq.getProductName().trim().isEmpty()) {
                    String pName = itemReq.getProductName().trim();
                    String pNameLower = pName.toLowerCase();
                    if (seenProductNames.contains(pNameLower)) {
                        throw new IllegalArgumentException("Duplicate product detected in invoice: " + pName);
                    }
                    seenProductNames.add(pNameLower);

                    invItem = inventoryItemRepo.findByNameIgnoreCaseAndActiveTrue(pName).orElse(null);
                    if (invItem == null) {
                        invItem = new InventoryItem();
                        invItem.setName(pName);
                        invItem.setUnit(itemReq.getUnit() != null && !itemReq.getUnit().trim().isEmpty() ? itemReq.getUnit().trim() : "pcs");
                        invItem.setItemCategory(InventoryService.normalizeCategory(itemReq.getItemCategory()));
                        Double newUnitVol = itemReq.getEffectiveUnitVolume();
                        String newVolUnit = itemReq.getVolumeUnit() != null && !itemReq.getVolumeUnit().trim().isEmpty()
                                ? itemReq.getVolumeUnit().trim()
                                : invItem.getUnit();
                        invItem.setUnitVolume(newUnitVol);
                        invItem.setVolumeUnit(newVolUnit);
                        invItem.setQuantity(0.0);
                        invItem.setTotalVolume(0.0);
                        invItem.setMinQuantity(5.0);
                        invItem.setCostPerUnit(itemReq.getUnitPrice());
                        if (newUnitVol > 0 && itemReq.getUnitPrice() != null && itemReq.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                            invItem.setCostPerVolume(itemReq.getUnitPrice().divide(BigDecimal.valueOf(newUnitVol), 2, RoundingMode.HALF_UP));
                        }
                        invItem.setSupplier(s.getName());
                        invItem.setActive(true);
                        invItem.setCreatedAt(LocalDateTime.now());
                        invItem.setLastUpdated(LocalDateTime.now());
                        invItem = inventoryItemRepo.save(invItem);
                        auditLog.log("CREATE_INVENTORY", "INVENTORY", invItem.getId(), null, invItem.getName() + " via Supplier Invoice " + inv.getInvoiceNumber());
                    }
                } else {
                    continue;
                }

                String itemCat = (itemReq.getItemCategory() != null && !itemReq.getItemCategory().trim().isEmpty())
                        ? InventoryService.normalizeCategory(itemReq.getItemCategory())
                        : (invItem.getItemCategory() != null ? invItem.getItemCategory() : "Stock");
                invItem.setItemCategory(itemCat);

                double addQty = itemReq.getQuantity();
                double packFactor = resolvePurchaseFactor(itemReq, invItem);
                double stockAdd = StockUnits.round3(addQty * packFactor);
                BigDecimal unitPrice = itemReq.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
                // price of ONE stock unit (e.g. per bottle when bought per case of 12)
                BigDecimal stockUnitPrice = packFactor > 1.0
                        ? unitPrice.divide(BigDecimal.valueOf(packFactor), 2, RoundingMode.HALF_UP)
                        : unitPrice;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(addQty)).setScale(2, RoundingMode.HALF_UP);
                calculatedSubtotal = calculatedSubtotal.add(lineTotal);

                Double unitVol = itemReq.getEffectiveUnitVolume();
                if (unitVol == null || unitVol <= 0) {
                    unitVol = (invItem.getUnitVolume() != null && invItem.getUnitVolume() > 0) ? invItem.getUnitVolume() : 1.0;
                }
                String volUnit = itemReq.getVolumeUnit() != null && !itemReq.getVolumeUnit().trim().isEmpty()
                        ? itemReq.getVolumeUnit().trim()
                        : (invItem.getVolumeUnit() != null ? invItem.getVolumeUnit() : invItem.getUnit());

                double lineTotalVol = stockAdd * unitVol;
                BigDecimal costPerVol = (unitVol > 0 && stockUnitPrice.compareTo(BigDecimal.ZERO) > 0)
                        ? stockUnitPrice.divide(BigDecimal.valueOf(unitVol), 2, RoundingMode.HALF_UP)
                        : stockUnitPrice;

                // Determine item-level GST
                Double itemGstRate = itemReq.getGstRate();
                BigDecimal itemGstAmount = BigDecimal.ZERO;
                if (itemGstRate != null) {
                    hasExplicitItemGst = true;
                    if (itemReq.getGstAmount() != null && itemReq.getGstAmount().compareTo(BigDecimal.ZERO) > 0) {
                        itemGstAmount = itemReq.getGstAmount().setScale(2, RoundingMode.HALF_UP);
                    } else if (itemGstRate > 0) {
                        itemGstAmount = lineTotal.multiply(BigDecimal.valueOf(itemGstRate))
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }
                } else if (req.getGstRate() != null && req.getGstRate() > 0) {
                    itemGstRate = req.getGstRate();
                    itemGstAmount = lineTotal.multiply(BigDecimal.valueOf(itemGstRate))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else {
                    itemGstRate = 0.0;
                }
                calculatedTotalItemGst = calculatedTotalItemGst.add(itemGstAmount);

                double prevStock = invItem.getQuantity() != null ? invItem.getQuantity() : 0.0;
                double newStock = StockUnits.round3(prevStock + stockAdd);
                invItem.setQuantity(newStock);
                invItem.setUnitVolume(unitVol);
                invItem.setVolumeUnit(volUnit);
                invItem.setTotalVolume(newStock * unitVol);

                // Weighted Average Cost (WAC) formula:
                if (unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                    if (prevStock > 0 && invItem.getCostPerUnit() != null && invItem.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal oldVal = invItem.getCostPerUnit().multiply(BigDecimal.valueOf(prevStock));
                        BigDecimal addedVal = unitPrice.multiply(BigDecimal.valueOf(addQty));
                        BigDecimal newAvg = oldVal.add(addedVal).divide(BigDecimal.valueOf(newStock), 2, RoundingMode.HALF_UP);
                        invItem.setCostPerUnit(newAvg);
                    } else {
                        invItem.setCostPerUnit(stockUnitPrice);
                    }
                }
                if (unitVol > 0 && invItem.getCostPerUnit() != null) {
                    invItem.setCostPerVolume(invItem.getCostPerUnit().divide(BigDecimal.valueOf(unitVol), 2, RoundingMode.HALF_UP));
                }
                invItem.setSupplier(s.getName());
                invItem.setLastUpdated(LocalDateTime.now());
                inventoryItemRepo.save(invItem);

                // Save line item
                SupplierInvoiceItem invoiceItem = SupplierInvoiceItem.builder()
                        .invoiceId(inv.getId())
                        .inventoryItemId(invItem.getId())
                        .productName(invItem.getName())
                        .quantity(addQty)
                        .unitPrice(unitPrice)
                        .totalPrice(lineTotal)
                        .unit(invItem.getUnit())
                        .purchaseMode(packFactor > 1.0 ? "PACK" : "UNIT")
                        .packName(packFactor > 1.0 ? invItem.getPackName() : null)
                        .packSize(packFactor > 1.0 ? packFactor : null)
                        .stockQuantity(stockAdd)
                        .expiryDate(expiryOf(itemReq, inv.getInvoiceDate()))
                        .itemCategory(itemCat)
                        .unitVolume(unitVol)
                        .volumeUnit(volUnit)
                        .totalVolume(lineTotalVol)
                        .costPerVolume(costPerVol)
                        .gstRate(itemGstRate)
                        .gstAmount(itemGstAmount)
                        .createdAt(LocalDateTime.now())
                        .build();
                invoiceItemRepo.save(invoiceItem);

                // Save inventory transaction
                InventoryTransaction txn = InventoryTransaction.builder()
                        .itemId(invItem.getId())
                        .transactionType("STOCK_IN")
                        .quantity(stockAdd)
                        .previousStock(prevStock)
                        .newStock(newStock)
                        .unitVolume(unitVol)
                        .volumeUnit(volUnit)
                        .totalVolume(lineTotalVol)
                        .costPerVolume(costPerVol)
                        .userId(userId)
                        .userName(userName)
                        .userRole(userRole)
                        .itemCategory(itemCat)
                        .supplierId(s.getId())
                        .supplierName(s.getName())
                        .invoiceId(inv.getId())
                        .invoiceReference(inv.getInvoiceNumber())
                        .unitPrice(stockUnitPrice)
                        .notes("Supplier Purchase: " + inv.getInvoiceNumber()
                                + (packFactor > 1.0 ? " (" + addQty + " " + invItem.getPackName() + " x " + packFactor + " " + invItem.getUnit() + ")" : ""))
                        .createdAt(LocalDateTime.now())
                        .build();
                transactionRepo.save(txn);

                auditLog.log("STOCK_IN", "INVENTORY", invItem.getId(), String.valueOf(prevStock), String.valueOf(newStock));

                if (itemSummary.length() > 0) itemSummary.append(", ");
                itemSummary.append(invItem.getName()).append(" x").append(addQty).append(" ")
                        .append(packFactor > 1.0 ? invItem.getPackName() : invItem.getUnit());
                if (unitVol > 0 && volUnit != null) {
                    itemSummary.append(" (").append(unitVol).append(" ").append(volUnit).append("/").append(invItem.getUnit()).append(")");
                }
            }
        }

        BigDecimal subtotal = calculatedSubtotal.compareTo(BigDecimal.ZERO) > 0 ? calculatedSubtotal
                : (req.getSubtotal() != null ? req.getSubtotal() : (req.getTotalAmount() != null ? req.getTotalAmount() : BigDecimal.ZERO));
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        double gstRate = req.getGstRate() != null ? req.getGstRate() : 0.0;
        BigDecimal gstAmount = BigDecimal.ZERO;
        if (hasExplicitItemGst) {
            gstAmount = calculatedTotalItemGst.setScale(2, RoundingMode.HALF_UP);
            if (req.getGstAmount() != null && req.getGstAmount().compareTo(BigDecimal.ZERO) > 0
                    && req.getGstAmount().subtract(gstAmount).abs().compareTo(BigDecimal.valueOf(0.10)) <= 0) {
                gstAmount = req.getGstAmount().setScale(2, RoundingMode.HALF_UP);
            }
            if (gstRate <= 0 && subtotal.compareTo(BigDecimal.ZERO) > 0 && gstAmount.compareTo(BigDecimal.ZERO) > 0) {
                gstRate = Math.round(gstAmount.divide(subtotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() * 100.0) / 100.0;
            }
        } else if (req.getGstAmount() != null && req.getGstAmount().compareTo(BigDecimal.ZERO) > 0) {
            gstAmount = req.getGstAmount().setScale(2, RoundingMode.HALF_UP);
            if (gstRate <= 0 && subtotal.compareTo(BigDecimal.ZERO) > 0) {
                gstRate = Math.round(gstAmount.divide(subtotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() * 100.0) / 100.0;
            }
        } else if (gstRate > 0) {
            gstAmount = subtotal.multiply(BigDecimal.valueOf(gstRate)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        BigDecimal otherCharges = req.getOtherCharges() != null ? req.getOtherCharges().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal discount = req.getDiscount() != null ? req.getDiscount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal calculatedTotal = subtotal.add(gstAmount).add(otherCharges).subtract(discount).setScale(2, RoundingMode.HALF_UP);

        // If user provided a specific totalAmount, validate consistency
        if (req.getTotalAmount() != null && req.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal reqTotal = req.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            if (reqTotal.subtract(calculatedTotal).abs().compareTo(BigDecimal.valueOf(0.10)) > 0) {
                throw new IllegalArgumentException(String.format(
                        "Invoice total mismatch: Entered ₹%.2f, but calculated total is ₹%.2f (Subtotal: ₹%.2f, GST: ₹%.2f, Charges: ₹%.2f, Discount: -₹%.2f)",
                        reqTotal, calculatedTotal, subtotal, gstAmount, otherCharges, discount));
            }
        }

        BigDecimal finalTotal = (req.getTotalAmount() != null && req.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) ? req.getTotalAmount().setScale(2, RoundingMode.HALF_UP) : calculatedTotal;
        inv.setSubtotal(subtotal);
        inv.setGstRate(gstRate);
        inv.setGstAmount(gstAmount);
        inv.setOtherCharges(otherCharges);
        inv.setDiscount(discount);
        inv.setTotalAmount(finalTotal);
        inv.setOutstandingAmount(finalTotal);

        if (itemSummary.length() > 0 && (inv.getItemsReceived() == null || inv.getItemsReceived().isBlank())) {
            inv.setItemsReceived(itemSummary.toString());
        }
        inv = invoiceRepo.save(inv);

        auditLog.log("CREATE", "SUPPLIER_INVOICE", inv.getId(),
                null, s.getName() + " / " + inv.getInvoiceNumber());
        return toInvoiceDTO(inv);
    }

    @Transactional
    public SupplierInvoiceDTO updateInvoice(Long id, SupplierInvoiceRequestDTO req, String performedBy) {
        SupplierInvoice inv = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + id));
        String old = inv.getInvoiceNumber();

        if (req.getSupplierId() != null) {
            supplierRepo.findById(req.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found: " + req.getSupplierId()));
            inv.setSupplierId(req.getSupplierId());
        }
        Supplier s = supplierRepo.findById(inv.getSupplierId()).orElse(null);

        if (req.getInvoiceNumber() != null && !req.getInvoiceNumber().isBlank()) {
            inv.setInvoiceNumber(req.getInvoiceNumber().trim());
        }
        if (req.getInvoiceDate() != null) inv.setInvoiceDate(req.getInvoiceDate());
        if (req.getDueDate() != null) inv.setDueDate(req.getDueDate());
        if (req.getDescription() != null) inv.setDescription(req.getDescription());

        User user = null;
        if (performedBy != null) {
            user = userRepo.findByUsername(performedBy).orElse(null);
        }
        Long userId = user != null ? user.getId() : null;
        String userName = performedBy != null ? performedBy : (user != null ? user.getUsername() : "SYSTEM");
        String userRole = (user != null && user.getRole() != null) ? user.getRole().name() : "MANAGER";

        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        BigDecimal calculatedTotalItemGst = BigDecimal.ZERO;
        boolean hasExplicitItemGst = false;
        StringBuilder itemSummary = new StringBuilder();

        if (req.getItems() != null && !req.getItems().isEmpty()) {
            List<SupplierInvoiceItem> oldItems = invoiceItemRepo.findByInvoiceId(id);

            // Reversal pre-validation: check that reduction won't cause negative stock
            Map<Long, Double> oldStockMap = new HashMap<>();
            for (SupplierInvoiceItem oldItem : oldItems) {
                if (oldItem.getInventoryItemId() != null) {
                    oldStockMap.merge(oldItem.getInventoryItemId(), oldItem.stockQty(), Double::sum);
                }
            }

            // real stock before this edit starts (the history entry should show the true before/after)
            Map<Long, Double> stockBeforeEdit = new HashMap<>();
            for (Long oldId : oldStockMap.keySet()) {
                inventoryItemRepo.findById(oldId).ifPresent(i -> stockBeforeEdit.put(oldId, i.getQuantity() != null ? i.getQuantity() : 0.0));
            }

            Map<Long, Double> newStockMap = new HashMap<>();
            for (SupplierInvoiceItemDTO itemReq : req.getItems()) {
                if (itemReq.getInventoryItemId() != null && itemReq.getInventoryItemId() > 0) {
                    InventoryItem byId = inventoryItemRepo.findById(itemReq.getInventoryItemId()).orElse(null);
                    newStockMap.merge(itemReq.getInventoryItemId(), requestStockQty(itemReq, byId), Double::sum);
                } else if (itemReq.getProductName() != null && !itemReq.getProductName().trim().isEmpty()) {
                    InventoryItem existing = inventoryItemRepo.findByNameIgnoreCaseAndActiveTrue(itemReq.getProductName().trim()).orElse(null);
                    if (existing != null) {
                        newStockMap.merge(existing.getId(), requestStockQty(itemReq, existing), Double::sum);
                    }
                }
            }

            for (Map.Entry<Long, Double> entry : oldStockMap.entrySet()) {
                Long itemId = entry.getKey();
                Double oldQty = entry.getValue();
                Double newQty = newStockMap.getOrDefault(itemId, 0.0);
                Double netDelta = newQty - oldQty;
                if (netDelta < 0) {
                    InventoryItem invItem = inventoryItemRepo.findById(itemId).orElse(null);
                    if (invItem != null && (invItem.getQuantity() + netDelta) < -0.001) {
                        throw new RuntimeException("Cannot update invoice: updating items would result in negative stock for "
                                + invItem.getName() + " (current: " + invItem.getQuantity() + ", reduction: " + Math.abs(netDelta) + ").");
                    }
                }
            }

            // Reverse old stock
            for (SupplierInvoiceItem oldItem : oldItems) {
                if (oldItem.getInventoryItemId() != null) {
                    InventoryItem invItem = inventoryItemRepo.findById(oldItem.getInventoryItemId()).orElse(null);
                    if (invItem != null) {
                        double prevStock = invItem.getQuantity() != null ? invItem.getQuantity() : 0.0;
                        // no clamp at 0: stock already issued must stay issued (the check above guarantees the final stock is not negative)
                        invItem.setQuantity(StockUnits.round3(prevStock - oldItem.stockQty()));
                        invItem.setLastUpdated(LocalDateTime.now());
                        inventoryItemRepo.save(invItem);
                    }
                }
            }
            invoiceItemRepo.deleteByInvoiceId(id);
            invoiceItemRepo.flush();

            // Apply new items
            Set<Long> seenInventoryItemIds = new HashSet<>();
            Set<String> seenProductNames = new HashSet<>();

            for (SupplierInvoiceItemDTO itemReq : req.getItems()) {
                if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Quantity must be greater than zero for all invoice items");
                }
                if (itemReq.getUnitPrice() == null || itemReq.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Price / unit cannot be negative");
                }

                InventoryItem invItem;
                if (itemReq.getInventoryItemId() != null && itemReq.getInventoryItemId() > 0) {
                    if (seenInventoryItemIds.contains(itemReq.getInventoryItemId())) {
                        throw new IllegalArgumentException("Duplicate product detected in invoice: Item ID " + itemReq.getInventoryItemId());
                    }
                    seenInventoryItemIds.add(itemReq.getInventoryItemId());
                    invItem = inventoryItemRepo.findById(itemReq.getInventoryItemId())
                            .orElseThrow(() -> new RuntimeException("Inventory item not found: " + itemReq.getInventoryItemId()));
                } else if (itemReq.getProductName() != null && !itemReq.getProductName().trim().isEmpty()) {
                    String pName = itemReq.getProductName().trim();
                    String pNameLower = pName.toLowerCase();
                    if (seenProductNames.contains(pNameLower)) {
                        throw new IllegalArgumentException("Duplicate product detected in invoice: " + pName);
                    }
                    seenProductNames.add(pNameLower);

                    invItem = inventoryItemRepo.findByNameIgnoreCaseAndActiveTrue(pName).orElse(null);
                    if (invItem == null) {
                        invItem = new InventoryItem();
                        invItem.setName(pName);
                        invItem.setUnit(itemReq.getUnit() != null && !itemReq.getUnit().trim().isEmpty() ? itemReq.getUnit().trim() : "pcs");
                        invItem.setItemCategory(InventoryService.normalizeCategory(itemReq.getItemCategory()));
                        Double newUnitVol = itemReq.getEffectiveUnitVolume();
                        String newVolUnit = itemReq.getVolumeUnit() != null && !itemReq.getVolumeUnit().trim().isEmpty()
                                ? itemReq.getVolumeUnit().trim()
                                : invItem.getUnit();
                        invItem.setUnitVolume(newUnitVol);
                        invItem.setVolumeUnit(newVolUnit);
                        invItem.setQuantity(0.0);
                        invItem.setTotalVolume(0.0);
                        invItem.setMinQuantity(5.0);
                        invItem.setCostPerUnit(itemReq.getUnitPrice());
                        if (newUnitVol > 0 && itemReq.getUnitPrice() != null && itemReq.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                            invItem.setCostPerVolume(itemReq.getUnitPrice().divide(BigDecimal.valueOf(newUnitVol), 2, RoundingMode.HALF_UP));
                        }
                        if (s != null) invItem.setSupplier(s.getName());
                        invItem.setActive(true);
                        invItem.setCreatedAt(LocalDateTime.now());
                        invItem.setLastUpdated(LocalDateTime.now());
                        invItem = inventoryItemRepo.save(invItem);
                        auditLog.log("CREATE_INVENTORY", "INVENTORY", invItem.getId(), null, invItem.getName() + " via Supplier Invoice " + inv.getInvoiceNumber());
                    }
                } else {
                    continue;
                }

                String itemCat = (itemReq.getItemCategory() != null && !itemReq.getItemCategory().trim().isEmpty())
                        ? InventoryService.normalizeCategory(itemReq.getItemCategory())
                        : (invItem.getItemCategory() != null ? invItem.getItemCategory() : "Stock");
                invItem.setItemCategory(itemCat);

                double addQty = itemReq.getQuantity();
                double packFactor = resolvePurchaseFactor(itemReq, invItem);
                double stockAdd = StockUnits.round3(addQty * packFactor);
                BigDecimal unitPrice = itemReq.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
                // price of ONE stock unit (e.g. per bottle when bought per case of 12)
                BigDecimal stockUnitPrice = packFactor > 1.0
                        ? unitPrice.divide(BigDecimal.valueOf(packFactor), 2, RoundingMode.HALF_UP)
                        : unitPrice;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(addQty)).setScale(2, RoundingMode.HALF_UP);
                calculatedSubtotal = calculatedSubtotal.add(lineTotal);

                Double unitVol = itemReq.getEffectiveUnitVolume();
                if (unitVol == null || unitVol <= 0) {
                    unitVol = (invItem.getUnitVolume() != null && invItem.getUnitVolume() > 0) ? invItem.getUnitVolume() : 1.0;
                }
                String volUnit = itemReq.getVolumeUnit() != null && !itemReq.getVolumeUnit().trim().isEmpty()
                        ? itemReq.getVolumeUnit().trim()
                        : (invItem.getVolumeUnit() != null ? invItem.getVolumeUnit() : invItem.getUnit());

                double lineTotalVol = stockAdd * unitVol;
                BigDecimal costPerVol = (unitVol > 0 && stockUnitPrice.compareTo(BigDecimal.ZERO) > 0)
                        ? stockUnitPrice.divide(BigDecimal.valueOf(unitVol), 2, RoundingMode.HALF_UP)
                        : stockUnitPrice;

                // Determine item-level GST
                Double itemGstRate = itemReq.getGstRate();
                BigDecimal itemGstAmount = BigDecimal.ZERO;
                if (itemGstRate != null) {
                    hasExplicitItemGst = true;
                    if (itemReq.getGstAmount() != null && itemReq.getGstAmount().compareTo(BigDecimal.ZERO) > 0) {
                        itemGstAmount = itemReq.getGstAmount().setScale(2, RoundingMode.HALF_UP);
                    } else if (itemGstRate > 0) {
                        itemGstAmount = lineTotal.multiply(BigDecimal.valueOf(itemGstRate))
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }
                } else if (req.getGstRate() != null && req.getGstRate() > 0) {
                    itemGstRate = req.getGstRate();
                    itemGstAmount = lineTotal.multiply(BigDecimal.valueOf(itemGstRate))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else {
                    itemGstRate = 0.0;
                }
                calculatedTotalItemGst = calculatedTotalItemGst.add(itemGstAmount);

                double prevStock = invItem.getQuantity() != null ? invItem.getQuantity() : 0.0;
                double newStock = StockUnits.round3(prevStock + stockAdd);
                invItem.setQuantity(newStock);
                invItem.setUnitVolume(unitVol);
                invItem.setVolumeUnit(volUnit);
                invItem.setTotalVolume(newStock * unitVol);

                if (unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                    if (prevStock > 0 && invItem.getCostPerUnit() != null && invItem.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal oldVal = invItem.getCostPerUnit().multiply(BigDecimal.valueOf(prevStock));
                        BigDecimal addedVal = unitPrice.multiply(BigDecimal.valueOf(addQty));
                        BigDecimal newAvg = oldVal.add(addedVal).divide(BigDecimal.valueOf(newStock), 2, RoundingMode.HALF_UP);
                        invItem.setCostPerUnit(newAvg);
                    } else {
                        invItem.setCostPerUnit(stockUnitPrice);
                    }
                }
                if (unitVol > 0 && invItem.getCostPerUnit() != null) {
                    invItem.setCostPerVolume(invItem.getCostPerUnit().divide(BigDecimal.valueOf(unitVol), 2, RoundingMode.HALF_UP));
                }
                if (s != null) invItem.setSupplier(s.getName());
                invItem.setLastUpdated(LocalDateTime.now());
                inventoryItemRepo.save(invItem);

                // Save line item
                SupplierInvoiceItem invoiceItem = SupplierInvoiceItem.builder()
                        .invoiceId(inv.getId())
                        .inventoryItemId(invItem.getId())
                        .productName(invItem.getName())
                        .quantity(addQty)
                        .unitPrice(unitPrice)
                        .totalPrice(lineTotal)
                        .unit(invItem.getUnit())
                        .purchaseMode(packFactor > 1.0 ? "PACK" : "UNIT")
                        .packName(packFactor > 1.0 ? invItem.getPackName() : null)
                        .packSize(packFactor > 1.0 ? packFactor : null)
                        .stockQuantity(stockAdd)
                        .expiryDate(expiryOf(itemReq, inv.getInvoiceDate()))
                        .itemCategory(itemCat)
                        .unitVolume(unitVol)
                        .volumeUnit(volUnit)
                        .totalVolume(lineTotalVol)
                        .costPerVolume(costPerVol)
                        .gstRate(itemGstRate)
                        .gstAmount(itemGstAmount)
                        .createdAt(LocalDateTime.now())
                        .build();
                invoiceItemRepo.save(invoiceItem);

                // Save inventory transaction
                double histPrev = stockBeforeEdit.getOrDefault(invItem.getId(), prevStock);
                double netChange = StockUnits.round3(newStock - histPrev);
                InventoryTransaction txn = InventoryTransaction.builder()
                        .itemId(invItem.getId())
                        .transactionType(netChange >= 0 ? "STOCK_IN" : "STOCK_OUT")
                        .quantity(Math.abs(netChange))
                        .previousStock(histPrev)
                        .newStock(newStock)
                        .unitVolume(unitVol)
                        .volumeUnit(volUnit)
                        .totalVolume(lineTotalVol)
                        .costPerVolume(costPerVol)
                        .userId(userId)
                        .userName(userName)
                        .userRole(userRole)
                        .itemCategory(itemCat)
                        .supplierId(s != null ? s.getId() : inv.getSupplierId())
                        .supplierName(s != null ? s.getName() : null)
                        .invoiceId(inv.getId())
                        .invoiceReference(inv.getInvoiceNumber())
                        .unitPrice(stockUnitPrice)
                        .notes("Supplier Purchase Update: " + inv.getInvoiceNumber()
                                + (packFactor > 1.0 ? " (" + addQty + " " + invItem.getPackName() + " x " + packFactor + " " + invItem.getUnit() + ")" : ""))
                        .createdAt(LocalDateTime.now())
                        .build();
                if (Math.abs(netChange) > 0.0004) transactionRepo.save(txn);

                auditLog.log("STOCK_IN", "INVENTORY", invItem.getId(), String.valueOf(prevStock), String.valueOf(newStock));

                if (itemSummary.length() > 0) itemSummary.append(", ");
                itemSummary.append(invItem.getName()).append(" x").append(addQty).append(" ")
                        .append(packFactor > 1.0 ? invItem.getPackName() : invItem.getUnit());
                if (unitVol > 0 && volUnit != null) {
                    itemSummary.append(" (").append(unitVol).append(" ").append(volUnit).append("/").append(invItem.getUnit()).append(")");
                }
            }

            if (req.getItemsReceived() != null && !req.getItemsReceived().isBlank()) {
                inv.setItemsReceived(req.getItemsReceived());
            } else if (itemSummary.length() > 0) {
                inv.setItemsReceived(itemSummary.toString());
            }
        } else if (req.getItemsReceived() != null) {
            inv.setItemsReceived(req.getItemsReceived());
        }

        BigDecimal subtotal = (calculatedSubtotal.compareTo(BigDecimal.ZERO) > 0) ? calculatedSubtotal
                : (req.getSubtotal() != null ? req.getSubtotal() : inv.getSubtotal());
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        inv.setSubtotal(subtotal);

        double gstRate = req.getGstRate() != null ? req.getGstRate() : (inv.getGstRate() != null ? inv.getGstRate() : 0.0);
        BigDecimal gstAmount = BigDecimal.ZERO;
        if (hasExplicitItemGst) {
            gstAmount = calculatedTotalItemGst.setScale(2, RoundingMode.HALF_UP);
            if (req.getGstAmount() != null && req.getGstAmount().compareTo(BigDecimal.ZERO) > 0
                    && req.getGstAmount().subtract(gstAmount).abs().compareTo(BigDecimal.valueOf(0.10)) <= 0) {
                gstAmount = req.getGstAmount().setScale(2, RoundingMode.HALF_UP);
            }
            if (gstRate <= 0 && subtotal.compareTo(BigDecimal.ZERO) > 0 && gstAmount.compareTo(BigDecimal.ZERO) > 0) {
                gstRate = Math.round(gstAmount.divide(subtotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() * 100.0) / 100.0;
            }
        } else if (req.getGstAmount() != null && req.getGstAmount().compareTo(BigDecimal.ZERO) > 0) {
            gstAmount = req.getGstAmount().setScale(2, RoundingMode.HALF_UP);
            if (gstRate <= 0 && subtotal.compareTo(BigDecimal.ZERO) > 0) {
                gstRate = Math.round(gstAmount.divide(subtotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() * 100.0) / 100.0;
            }
        } else if (gstRate > 0) {
            gstAmount = subtotal.multiply(BigDecimal.valueOf(gstRate)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (req.getGstRate() != null || req.getGstAmount() != null) {
            gstAmount = BigDecimal.ZERO;
        } else if (inv.getGstAmount() != null) {
            gstAmount = inv.getGstAmount().setScale(2, RoundingMode.HALF_UP);
        }
        inv.setGstRate(gstRate);
        inv.setGstAmount(gstAmount);

        BigDecimal otherCharges = req.getOtherCharges() != null ? req.getOtherCharges().setScale(2, RoundingMode.HALF_UP)
                : (inv.getOtherCharges() != null ? inv.getOtherCharges() : BigDecimal.ZERO);
        BigDecimal discount = req.getDiscount() != null ? req.getDiscount().setScale(2, RoundingMode.HALF_UP)
                : (inv.getDiscount() != null ? inv.getDiscount() : BigDecimal.ZERO);
        inv.setOtherCharges(otherCharges);
        inv.setDiscount(discount);

        BigDecimal calculatedTotal = subtotal.add(gstAmount).add(otherCharges).subtract(discount).setScale(2, RoundingMode.HALF_UP);

        if (req.getTotalAmount() != null && req.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal reqTotal = req.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            if (reqTotal.subtract(calculatedTotal).abs().compareTo(BigDecimal.valueOf(0.10)) > 0) {
                throw new IllegalArgumentException(String.format(
                        "Invoice total mismatch: Entered ₹%.2f, but calculated total is ₹%.2f (Subtotal: ₹%.2f, GST: ₹%.2f, Charges: ₹%.2f, Discount: -₹%.2f)",
                        reqTotal, calculatedTotal, subtotal, gstAmount, otherCharges, discount));
            }
        }

        BigDecimal finalTotal = (req.getTotalAmount() != null && req.getTotalAmount().compareTo(BigDecimal.ZERO) > 0)
                ? req.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                : calculatedTotal;
        inv.setTotalAmount(finalTotal);

        BigDecimal paidAmount = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
        inv.setOutstandingAmount(finalTotal.subtract(paidAmount).max(BigDecimal.ZERO));
        recalcInvoiceStatus(inv);

        inv.setUpdatedAt(LocalDateTime.now());
        inv = invoiceRepo.save(inv);
        auditLog.log("UPDATE", "SUPPLIER_INVOICE", id, old, inv.getInvoiceNumber());
        return toInvoiceDTO(inv);
    }

    @Transactional
    public void deleteInvoice(Long id, String performedBy) {
        SupplierInvoice inv = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + id));
        if (inv.getPaidAmount() != null && inv.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Cannot delete invoice with payments. Delete payments first.");
        }

        // Fetch line items to reverse inventory
        List<SupplierInvoiceItem> items = invoiceItemRepo.findByInvoiceId(id);

        // 1. Verify that reversal won't cause negative stock
        for (SupplierInvoiceItem item : items) {
            if (item.getInventoryItemId() != null) {
                InventoryItem invItem = inventoryItemRepo.findById(item.getInventoryItemId()).orElse(null);
                if (invItem != null && invItem.getQuantity() - item.stockQty() < -0.0005) {
                    throw new RuntimeException("Cannot delete invoice: reversing " + item.stockQty() + " " + item.getUnit()
                            + " would result in negative stock for " + invItem.getName() + " (current: " + invItem.getQuantity() + ").");
                }
            }
        }

        User user = null;
        if (performedBy != null) {
            user = userRepo.findByUsername(performedBy).orElse(null);
        }
        Long userId = user != null ? user.getId() : null;
        String userRole = (user != null && user.getRole() != null) ? user.getRole().name() : "MANAGER";

        // 2. Perform stock reversal and record transaction
        for (SupplierInvoiceItem item : items) {
            if (item.getInventoryItemId() != null) {
                InventoryItem invItem = inventoryItemRepo.findById(item.getInventoryItemId()).orElse(null);
                if (invItem != null) {
                    double prevStock = invItem.getQuantity();
                    double newStock = Math.max(0.0, StockUnits.round3(prevStock - item.stockQty()));
                    invItem.setQuantity(newStock);
                    invItem.setLastUpdated(LocalDateTime.now());
                    inventoryItemRepo.save(invItem);

                    InventoryTransaction txn = InventoryTransaction.builder()
                            .itemId(invItem.getId())
                            .transactionType("ADJUSTMENT")
                            .quantity(item.stockQty())
                            .previousStock(prevStock)
                            .newStock(newStock)
                            .userId(userId)
                            .userName(performedBy != null ? performedBy : "SYSTEM")
                            .userRole(userRole)
                            .supplierId(inv.getSupplierId())
                            .invoiceId(inv.getId())
                            .invoiceReference(inv.getInvoiceNumber())
                            .notes("Stock reversal from deleted invoice: " + inv.getInvoiceNumber())
                            .createdAt(LocalDateTime.now())
                            .build();
                    transactionRepo.save(txn);

                    auditLog.log("STOCK_REVERSAL", "INVENTORY", invItem.getId(), String.valueOf(prevStock), String.valueOf(newStock));
                }
            }
        }

        invoiceRepo.deleteById(id);
        auditLog.log("DELETE", "SUPPLIER_INVOICE", id, inv.getInvoiceNumber(), "DELETED");
    }

    // ─── Payment CRUD ────────────────────────────────────────────────────────────

    public List<SupplierPaymentDTO> getAllPayments() {
        return paymentRepo.findAll(org.springframework.data.domain.Sort.by("paymentDate").descending())
                .stream().map(this::toPaymentDTO).collect(Collectors.toList());
    }

    public List<SupplierPaymentDTO> getPaymentsByInvoice(Long invoiceId) {
        return paymentRepo.findByInvoiceId(invoiceId)
                .stream().map(this::toPaymentDTO).collect(Collectors.toList());
    }

    public List<SupplierPaymentDTO> getPaymentsBySupplier(Long supplierId) {
        return paymentRepo.findBySupplierIdOrderByDateDesc(supplierId)
                .stream().map(this::toPaymentDTO).collect(Collectors.toList());
    }

    @Transactional
    public SupplierPaymentDTO addPayment(SupplierPaymentRequestDTO req, Long userId, String performedBy) {
        SupplierInvoice inv = invoiceRepo.findById(req.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + req.getInvoiceId()));

        if (inv.getStatus() == SupplierInvoice.InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice is already fully paid.");
        }
        BigDecimal payAmt = req.getAmount() != null ? req.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (payAmt.compareTo(BigDecimal.ZERO) <= 0) throw new RuntimeException("Payment amount must be positive.");
        if (payAmt.compareTo(inv.getOutstandingAmount()) > 0) {
            throw new RuntimeException("Payment amount (₹" + payAmt + ") exceeds outstanding (₹" + inv.getOutstandingAmount() + ").");
        }

        // Persist payment
        SupplierPayment pay = new SupplierPayment();
        pay.setInvoiceId(inv.getId());
        pay.setSupplierId(inv.getSupplierId());
        pay.setPaymentDate(req.getPaymentDate() != null ? req.getPaymentDate() : LocalDate.now());
        pay.setAmount(payAmt);
        pay.setPaymentMode(parsePaymentMode(req.getPaymentMode()));
        pay.setReferenceNumber(req.getReferenceNumber());
        pay.setNotes(req.getNotes());
        pay.setCreatedBy(userId);
        pay = paymentRepo.save(pay);

        // Update invoice totals
        BigDecimal newPaid = inv.getPaidAmount().add(payAmt).setScale(2, RoundingMode.HALF_UP);
        inv.setPaidAmount(newPaid);
        inv.setOutstandingAmount(inv.getTotalAmount().subtract(newPaid).max(BigDecimal.ZERO));
        recalcInvoiceStatus(inv);
        inv.setUpdatedAt(LocalDateTime.now());
        invoiceRepo.save(inv);

        auditLog.log("PAYMENT", "SUPPLIER_PAYMENT", pay.getId(),
                null, "₹" + payAmt + " for invoice " + inv.getInvoiceNumber());
        return toPaymentDTO(pay);
    }

    @Transactional
    public void deletePayment(Long paymentId, String performedBy) {
        SupplierPayment pay = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        SupplierInvoice inv = invoiceRepo.findById(pay.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Invoice not found."));

        // Reverse totals
        BigDecimal newPaid = inv.getPaidAmount().subtract(pay.getAmount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        inv.setPaidAmount(newPaid);
        inv.setOutstandingAmount(inv.getTotalAmount().subtract(newPaid).max(BigDecimal.ZERO));
        recalcInvoiceStatus(inv);
        inv.setUpdatedAt(LocalDateTime.now());
        invoiceRepo.save(inv);

        paymentRepo.deleteById(paymentId);
        auditLog.log("DELETE", "SUPPLIER_PAYMENT", paymentId,
                "₹" + pay.getAmount(), "REVERSED");
    }

    // ─── Analytics ───────────────────────────────────────────────────────────────

    public SupplierAnalyticsDTO getAnalytics() {
        SupplierAnalyticsDTO dto = new SupplierAnalyticsDTO();

        // Total outstanding across all suppliers
        BigDecimal totalOut = invoiceRepo.totalOutstandingAll();
        dto.setTotalOutstanding(totalOut != null ? totalOut.doubleValue() : 0.0);

        // Overdue count
        List<SupplierInvoice> overdue = invoiceRepo.findOverdue(LocalDate.now());
        dto.setOverdueInvoiceCount(overdue.size());

        // Active supplier count
        dto.setActiveSupplierCount((int) supplierRepo.findByActiveTrue().size());

        // Paid this month
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        BigDecimal paidThisMonth = paymentRepo.totalPaidBetween(monthStart, LocalDate.now());
        dto.setTotalPaidThisMonth(paidThisMonth != null ? paidThisMonth.doubleValue() : 0.0);

        // Invoiced this month (sum of all invoice totals created this month)
        YearMonth thisMonth = YearMonth.now();
        List<SupplierInvoice> allInvoices = invoiceRepo.findAll();
        double invoicedThisMonth = allInvoices.stream()
                .filter(i -> i.getInvoiceDate() != null
                        && YearMonth.from(i.getInvoiceDate()).equals(thisMonth))
                .mapToDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0).sum();
        dto.setTotalInvoicedThisMonth(invoicedThisMonth);

        // Outstanding by supplier
        List<Supplier> activeSuppliers = supplierRepo.findByActiveTrue();
        List<Map<String, Object>> outBySupplier = new ArrayList<>();
        for (Supplier s : activeSuppliers) {
            BigDecimal out = invoiceRepo.totalOutstandingBySupplier(s.getId());
            if (out != null && out.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("supplierId", s.getId());
                row.put("supplierName", s.getName());
                row.put("outstanding", out.doubleValue());
                outBySupplier.add(row);
            }
        }
        outBySupplier.sort((a, b) -> Double.compare(
                (Double) b.get("outstanding"), (Double) a.get("outstanding")));
        dto.setOutstandingBySupplier(outBySupplier);

        // Monthly payment trend (last 6 months)
        List<Map<String, Object>> monthlyTrend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            BigDecimal paid = paymentRepo.totalPaidBetween(from, to);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", ym.format(fmt));
            m.put("paid", paid != null ? paid.doubleValue() : 0.0);
            monthlyTrend.add(m);
        }
        dto.setMonthlyPaymentTrend(monthlyTrend);

        // Payment mode breakdown
        List<SupplierPayment> allPayments = paymentRepo.findAll();
        Map<String, Double> modeBreakdown = new LinkedHashMap<>();
        for (SupplierPayment p : allPayments) {
            String mode = p.getPaymentMode().name();
            modeBreakdown.merge(mode, p.getAmount() != null ? p.getAmount().doubleValue() : 0.0, Double::sum);
        }
        dto.setPaymentModeBreakdown(modeBreakdown);

        // Top suppliers by invoice value
        Map<Long, Double> supplierTotals = new HashMap<>();
        for (SupplierInvoice inv : allInvoices) {
            supplierTotals.merge(inv.getSupplierId(), inv.getTotalAmount() != null ? inv.getTotalAmount().doubleValue() : 0.0, Double::sum);
        }
        Map<Long, Supplier> supplierMap = activeSuppliers.stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s));
        List<Map<String, Object>> topSuppliers = supplierTotals.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Supplier s = supplierMap.get(e.getKey());
                    row.put("supplierId", e.getKey());
                    row.put("supplierName", s != null ? s.getName() : "Unknown");
                    row.put("totalInvoiced", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
        dto.setTopSuppliersByInvoice(topSuppliers);

        return dto;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void mapRequest(SupplierRequestDTO req, Supplier s) {
        if (req.getName() != null) s.setName(req.getName());
        if (req.getContactPerson() != null) s.setContactPerson(req.getContactPerson());
        if (req.getPhone() != null) s.setPhone(req.getPhone());
        if (req.getEmail() != null) s.setEmail(req.getEmail());
        if (req.getAddress() != null) s.setAddress(req.getAddress());
        if (req.getGstNumber() != null) s.setGstNumber(req.getGstNumber());
        if (req.getPaymentTerms() != null) s.setPaymentTerms(req.getPaymentTerms());
        if (req.getCategory() != null) s.setCategory(req.getCategory());
        if (req.getBankName() != null) s.setBankName(req.getBankName());
        if (req.getBankAccount() != null) s.setBankAccount(req.getBankAccount());
        if (req.getBankIfsc() != null) s.setBankIfsc(req.getBankIfsc());
        if (req.getUpiId() != null) s.setUpiId(req.getUpiId());
        if (req.getNotes() != null) s.setNotes(req.getNotes());
    }

    private void recalcInvoiceStatus(SupplierInvoice inv) {
        BigDecimal outstanding = inv.getOutstandingAmount() != null ? inv.getOutstandingAmount() : BigDecimal.ZERO;
        BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            inv.setStatus(SupplierInvoice.InvoiceStatus.PAID);
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            inv.setStatus(SupplierInvoice.InvoiceStatus.PARTIAL);
        } else if (inv.getDueDate() != null && inv.getDueDate().isBefore(LocalDate.now())) {
            inv.setStatus(SupplierInvoice.InvoiceStatus.OVERDUE);
        } else {
            inv.setStatus(SupplierInvoice.InvoiceStatus.UNPAID);
        }
    }

    private SupplierPayment.PaymentMode parsePaymentMode(String mode) {
        if (mode == null) return SupplierPayment.PaymentMode.CASH;
        try {
            return SupplierPayment.PaymentMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SupplierPayment.PaymentMode.CASH;
        }
    }

    private SupplierDTO toDTO(Supplier s) {
        SupplierDTO dto = new SupplierDTO();
        dto.setId(s.getId());
        dto.setName(s.getName());
        dto.setContactPerson(s.getContactPerson());
        dto.setPhone(s.getPhone());
        dto.setEmail(s.getEmail());
        dto.setAddress(s.getAddress());
        dto.setGstNumber(s.getGstNumber());
        dto.setPaymentTerms(s.getPaymentTerms());
        dto.setCategory(s.getCategory());
        dto.setBankName(s.getBankName());
        dto.setBankAccount(s.getBankAccount());
        dto.setBankIfsc(s.getBankIfsc());
        dto.setUpiId(s.getUpiId());
        dto.setNotes(s.getNotes());
        dto.setActive(s.getActive());
        dto.setCreatedAt(s.getCreatedAt());
        // Ledger summary
        List<SupplierInvoice> invs = invoiceRepo.findBySupplierId(s.getId());
        double totalInvoiced = invs.stream().mapToDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0).sum();
        double totalPaid = invs.stream().mapToDouble(i -> i.getPaidAmount() != null ? i.getPaidAmount().doubleValue() : 0.0).sum();
        dto.setTotalInvoiced(totalInvoiced);
        dto.setTotalPaid(totalPaid);
        dto.setTotalOutstanding(totalInvoiced - totalPaid);
        dto.setInvoiceCount(invs.size());
        return dto;
    }

    private SupplierInvoiceDTO toInvoiceDTO(SupplierInvoice inv) {
        SupplierInvoiceDTO dto = new SupplierInvoiceDTO();
        dto.setId(inv.getId());
        dto.setSupplierId(inv.getSupplierId());
        dto.setInvoiceNumber(inv.getInvoiceNumber());
        dto.setInvoiceDate(inv.getInvoiceDate());
        dto.setDueDate(inv.getDueDate());
        dto.setSubtotal(inv.getSubtotal());
        dto.setGstRate(inv.getGstRate());
        dto.setGstAmount(inv.getGstAmount());
        dto.setOtherCharges(inv.getOtherCharges());
        dto.setDiscount(inv.getDiscount());
        dto.setTotalAmount(inv.getTotalAmount());
        dto.setPaidAmount(inv.getPaidAmount());
        dto.setOutstandingAmount(inv.getOutstandingAmount());
        dto.setStatus(inv.getStatus().name());
        dto.setDescription(inv.getDescription());
        dto.setItemsReceived(inv.getItemsReceived());
        dto.setCreatedBy(inv.getCreatedBy());
        dto.setCreatedAt(inv.getCreatedAt());
        dto.setIsOverdue(inv.getDueDate() != null
                && inv.getDueDate().isBefore(LocalDate.now())
                && inv.getStatus() != SupplierInvoice.InvoiceStatus.PAID);
        // Supplier name
        supplierRepo.findById(inv.getSupplierId())
                .ifPresent(s -> dto.setSupplierName(s.getName()));
        // Payments
        dto.setPayments(paymentRepo.findByInvoiceId(inv.getId())
                .stream().map(this::toPaymentDTO).collect(Collectors.toList()));
        // Line Items
        dto.setItems(invoiceItemRepo.findByInvoiceId(inv.getId())
                .stream().map(this::toItemDTO).collect(Collectors.toList()));
        return dto;
    }

    /**
     * Stock units that ONE purchased unit stands for: the pack size when the line is entered
     * by pack (e.g. cases of 12 bottles), otherwise 1. A pack definition sent on the line
     * (pack name + size) is saved on the item so the next purchase remembers it.
     */
    /** The expiry date typed on a purchase line, if any. It cannot be earlier than the invoice date. */
    private LocalDate expiryOf(SupplierInvoiceItemDTO req, LocalDate invoiceDate) {
        LocalDate expiry = req.getExpiryDate();
        if (expiry != null && invoiceDate != null && expiry.isBefore(invoiceDate)) {
            throw new IllegalArgumentException("Expiry date of " + (req.getProductName() != null ? req.getProductName() : "an item") + " cannot be before the invoice date.");
        }
        return expiry;
    }

    private double resolvePurchaseFactor(SupplierInvoiceItemDTO req, InventoryItem inv) {
        if (!"PACK".equalsIgnoreCase(req.getPurchaseMode())) return 1.0;
        Double size = StockUnits.normalizePackSize(req.getPackSize() != null ? req.getPackSize() : inv.getPackSize());
        if (size == null) {
            throw new IllegalArgumentException("Enter how many " + inv.getUnit() + " are in one pack for \""
                    + inv.getName() + "\" (or buy it by " + inv.getUnit() + ").");
        }
        inv.setPackSize(size);
        String name = StockUnits.normalizePackName(req.getPackName());
        if (name != null) inv.setPackName(name);
        else if (inv.getPackName() == null) inv.setPackName("pack");
        return size;
    }

    /** Stock units a request line adds, without changing anything (used to validate edits). */
    private double requestStockQty(SupplierInvoiceItemDTO req, InventoryItem inv) {
        double qty = req.getQuantity() != null ? req.getQuantity() : 0.0;
        if (!"PACK".equalsIgnoreCase(req.getPurchaseMode()) || inv == null) return qty;
        Double size = StockUnits.normalizePackSize(req.getPackSize() != null ? req.getPackSize() : inv.getPackSize());
        return size == null ? qty : StockUnits.round3(qty * size);
    }

    private SupplierInvoiceItemDTO toItemDTO(SupplierInvoiceItem item) {
        Double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;
        Double totalVol = item.getTotalVolume() != null ? item.getTotalVolume() : (item.stockQty() * unitVol);
        String volUnit = item.getVolumeUnit() != null ? item.getVolumeUnit() : item.getUnit();
        BigDecimal costPerVol = item.getCostPerVolume();
        if (costPerVol == null && unitVol > 0 && item.getUnitPrice() != null && item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            costPerVol = item.getUnitPrice().divide(BigDecimal.valueOf(unitVol), 2, RoundingMode.HALF_UP);
        }

        return SupplierInvoiceItemDTO.builder()
                .id(item.getId())
                .invoiceId(item.getInvoiceId())
                .inventoryItemId(item.getInventoryItemId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .unit(item.getUnit())
                .purchaseMode(item.getPurchaseMode())
                .packName(item.getPackName())
                .packSize(item.getPackSize())
                .stockQuantity(item.stockQty())
                .expiryDate(item.getExpiryDate())
                .itemCategory(item.getItemCategory() != null ? item.getItemCategory() : "Stock")
                .unitVolume(unitVol)
                .volume(unitVol)
                .volumeUnit(volUnit)
                .totalVolume(totalVol)
                .costPerVolume(costPerVol)
                .gstRate(item.getGstRate() != null ? item.getGstRate() : 0.0)
                .gstAmount(item.getGstAmount() != null ? item.getGstAmount() : BigDecimal.ZERO)
                .createdAt(item.getCreatedAt())
                .build();
    }

    private SupplierPaymentDTO toPaymentDTO(SupplierPayment p) {
        SupplierPaymentDTO dto = new SupplierPaymentDTO();
        dto.setId(p.getId());
        dto.setInvoiceId(p.getInvoiceId());
        dto.setSupplierId(p.getSupplierId());
        dto.setPaymentDate(p.getPaymentDate());
        dto.setAmount(p.getAmount());
        dto.setPaymentMode(p.getPaymentMode().name());
        dto.setReferenceNumber(p.getReferenceNumber());
        dto.setNotes(p.getNotes());
        dto.setCreatedBy(p.getCreatedBy());
        dto.setCreatedAt(p.getCreatedAt());
        supplierRepo.findById(p.getSupplierId())
                .ifPresent(s -> dto.setSupplierName(s.getName()));
        invoiceRepo.findById(p.getInvoiceId())
                .ifPresent(i -> dto.setInvoiceNumber(i.getInvoiceNumber()));
        return dto;
    }
}
