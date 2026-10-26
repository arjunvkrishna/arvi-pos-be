package com.neram.pos.service;

import com.neram.pos.dto.ExpiryBatchDTO;
import com.neram.pos.dto.InventoryItemDTO;
import com.neram.pos.dto.InventoryItemRequestDTO;
import com.neram.pos.dto.InventoryTransactionDTO;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.SupplierInvoice;
import com.neram.pos.entity.SupplierInvoiceItem;
import com.neram.pos.entity.User;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.InventoryTransactionRepository;
import com.neram.pos.repository.SupplierInvoiceItemRepository;
import com.neram.pos.repository.SupplierInvoiceRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final SupplierInvoiceRepository invoiceRepository;

    public static String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "Stock";
        }
        String clean = category.trim();
        if (clean.equalsIgnoreCase("Fixed asset") || clean.equalsIgnoreCase("Fixed assets")
                || clean.equalsIgnoreCase("FIXED_ASSET") || clean.equalsIgnoreCase("fixed_asset")) {
            return "Fixed asset";
        }
        if (clean.equalsIgnoreCase("Consumable") || clean.equalsIgnoreCase("Consumables")
                || clean.equalsIgnoreCase("CONSUMABLE") || clean.equalsIgnoreCase("consumable")) {
            return "Consumable";
        }
        if (clean.equalsIgnoreCase("Small equipment") || clean.equalsIgnoreCase("Small equipments")
                || clean.equalsIgnoreCase("SMALL_EQUIPMENT") || clean.equalsIgnoreCase("small_equipment")) {
            return "Small equipment";
        }
        return "Stock";
    }

    public List<InventoryItemDTO> getAllItems() {
        return getAllItems(null);
    }

    public List<InventoryItemDTO> getAllItems(String category) {
        List<InventoryItem> items;
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
            items = inventoryItemRepository.findByItemCategoryIgnoreCaseAndActiveTrue(normalizeCategory(category));
        } else {
            items = inventoryItemRepository.findByActiveTrue();
        }
        Map<Long, List<StockExpiry.Receipt>> receipts = receiptsFor(items.stream().map(InventoryItem::getId).collect(Collectors.toList()));
        LocalDate today = LocalDate.now();
        return items.stream()
                .map(i -> toDTO(i, receipts.getOrDefault(i.getId(), Collections.emptyList()), today))
                .collect(Collectors.toList());
    }

    /** What was received for these items (stock units, expiry date, invoice date), oldest first. Two queries, however many items. */
    private Map<Long, List<StockExpiry.Receipt>> receiptsFor(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return Collections.emptyMap();
        List<SupplierInvoiceItem> lines = invoiceItemRepository.findByInventoryItemIdIn(itemIds);
        if (lines.isEmpty()) return Collections.emptyMap();
        Map<Long, SupplierInvoice> invoices = invoiceRepository.findAllById(lines.stream().map(SupplierInvoiceItem::getInvoiceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SupplierInvoice::getId, x -> x));
        Map<Long, List<StockExpiry.Receipt>> out = new HashMap<>();
        for (SupplierInvoiceItem l : lines) {
            SupplierInvoice inv = invoices.get(l.getInvoiceId());
            out.computeIfAbsent(l.getInventoryItemId(), k -> new ArrayList<>())
                    .add(new StockExpiry.Receipt(l.getInvoiceId(), inv != null ? inv.getInvoiceNumber() : null, inv != null ? inv.getInvoiceDate() : null, l.stockQty(), l.getExpiryDate()));
        }
        return out;
    }

    /** Stock on hand that is expired or about to expire (all batches with an expiry when all = true), earliest first. */
    public List<ExpiryBatchDTO> getExpiryBatches(boolean all) {
        List<InventoryItem> items = inventoryItemRepository.findByActiveTrue();
        Map<Long, List<StockExpiry.Receipt>> receipts = receiptsFor(items.stream().map(InventoryItem::getId).collect(Collectors.toList()));
        LocalDate today = LocalDate.now();
        List<ExpiryBatchDTO> out = new ArrayList<>();
        for (InventoryItem i : items) {
            for (StockExpiry.Batch b : StockExpiry.onHand(i.getQuantity() != null ? i.getQuantity() : 0.0, receipts.getOrDefault(i.getId(), Collections.emptyList()), i.getShelfLifeDays(), warnDaysOf(i), today)) {
                if (!all && StockExpiry.OK.equals(b.status())) continue;
                out.add(ExpiryBatchDTO.builder().itemId(i.getId()).itemName(i.getName()).unit(i.getUnit()).packName(i.getPackName()).packSize(i.getPackSize())
                        .expiryDate(b.expiry()).quantity(b.quantity()).daysLeft(b.daysLeft()).status(b.status())
                        .invoiceId(b.invoiceId()).invoiceNumber(b.invoiceNumber()).receivedOn(b.receivedOn()).build());
            }
        }
        out.sort(java.util.Comparator.comparing(ExpiryBatchDTO::getExpiryDate).thenComparing(ExpiryBatchDTO::getItemName));
        return out;
    }

    /** Switch for automatic deduction, shelf life and warning days; a value that is not sent leaves the item as it is. */
    private static void applyStockRules(InventoryItem item, InventoryItemRequestDTO dto) {
        if (dto.getAutoDeduct() != null) item.setAutoDeduct(dto.getAutoDeduct());
        if (dto.getShelfLifeDays() != null) {
            if (dto.getShelfLifeDays() < 0 || dto.getShelfLifeDays() > 3650) throw new IllegalArgumentException("Shelf life must be between 0 and 3650 days.");
            item.setShelfLifeDays(dto.getShelfLifeDays() == 0 ? null : dto.getShelfLifeDays());
        }
        if (dto.getExpiryWarnDays() != null) {
            if (dto.getExpiryWarnDays() < 0 || dto.getExpiryWarnDays() > 365) throw new IllegalArgumentException("Warning days must be between 0 and 365.");
            item.setExpiryWarnDays(dto.getExpiryWarnDays());
        }
    }

    private static String defaultNote(String type) {
        switch (type) {
            case "EXPIRED": return "Expired stock written off";
            case "SPOILED": return "Spoiled stock written off";
            case "BREAKAGE": return "Broken / damaged";
            case "STAFF_MEAL": return "Staff meal";
            default: return "Stock Issued / Used";
        }
    }

    private static int warnDaysOf(InventoryItem i) { return i.getExpiryWarnDays() != null ? i.getExpiryWarnDays() : 2; }

    /**
     * One page of the active inventory items (ordered by id), narrowed by the optional filters the inventory screen offers:
     * category, a text search over name / category / supplier / unit / volume unit / pack name, and lowOnly (at or below the minimum).
     */
    public org.springframework.data.domain.Page<InventoryItemDTO> searchItems(String category, String q, boolean lowOnly,
                                                                             org.springframework.data.domain.Pageable pageable) {
        String cat = (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) ? normalizeCategory(category) : null;
        String pattern = com.neram.pos.util.LikeSearch.pattern(q);

        org.springframework.data.jpa.domain.Specification<InventoryItem> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> p = new java.util.ArrayList<>();
            p.add(cb.isTrue(root.get("active")));
            // An item with no category counts as "Stock"
            jakarta.persistence.criteria.Expression<String> categoryOf = cb.coalesce(root.<String>get("itemCategory"), "Stock");
            if (cat != null) p.add(cb.equal(cb.lower(categoryOf), cat.toLowerCase()));
            if (lowOnly) p.add(cb.le(root.<Double>get("quantity"), root.<Double>get("minQuantity")));
            if (pattern != null) {
                p.add(cb.or(
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("name"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, categoryOf, pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("supplier"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("unit"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("volumeUnit"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("packName"), pattern)));
            }
            return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return inventoryItemRepository.findAll(spec, com.neram.pos.util.PagedResponse.withSort(pageable, org.springframework.data.domain.Sort.by("id")))
                .map(this::toDTO);
    }

    public List<InventoryItemDTO> getLowStockItems() {
        return inventoryItemRepository.findLowStockItems().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public InventoryItemDTO getItemById(Long id) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));
        return toDTO(item);
    }

    @Transactional
    public InventoryItemDTO createItem(InventoryItemRequestDTO dto) {
        InventoryItem item = new InventoryItem();
        item.setName(dto.getName());
        item.setUnit(dto.getUnit() != null ? dto.getUnit() : "pcs");
        double initialQty = dto.getQuantity() != null ? dto.getQuantity() : 0.0;
        item.setQuantity(initialQty);
        item.setMinQuantity(dto.getMinQuantity() != null ? dto.getMinQuantity() : 0.0);
        item.setCostPerUnit(dto.getCostPerUnit() != null ? dto.getCostPerUnit() : BigDecimal.ZERO);

        Double unitVol = dto.getEffectiveUnitVolume();
        String volUnit = dto.getVolumeUnit() != null && !dto.getVolumeUnit().trim().isEmpty()
                ? dto.getVolumeUnit().trim()
                : item.getUnit();
        double totalVol = initialQty * unitVol;
        BigDecimal costPerVol = BigDecimal.ZERO;
        if (unitVol > 0 && item.getCostPerUnit() != null && item.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
            costPerVol = item.getCostPerUnit().divide(BigDecimal.valueOf(unitVol), 2, java.math.RoundingMode.HALF_UP);
        }

        item.setUnitVolume(unitVol);
        item.setVolumeUnit(volUnit);
        item.setTotalVolume(totalVol);
        item.setCostPerVolume(costPerVol);
        applyPack(item, dto);
        applyStockRules(item, dto);
        item.setSupplier(dto.getSupplier());
        item.setItemCategory(normalizeCategory(dto.getItemCategory()));
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setLastUpdated(LocalDateTime.now());

        InventoryItem saved = inventoryItemRepository.save(item);

        if (initialQty > 0) {
            InventoryTransaction txn = InventoryTransaction.builder()
                    .itemId(saved.getId())
                    .transactionType("STOCK_IN")
                    .quantity(initialQty)
                    .previousStock(0.0)
                    .newStock(initialQty)
                    .unitVolume(unitVol)
                    .volumeUnit(volUnit)
                    .totalVolume(totalVol)
                    .costPerVolume(costPerVol)
                    .supplierName(saved.getSupplier())
                    .itemCategory(saved.getItemCategory())
                    .unitPrice(saved.getCostPerUnit())
                    .notes("Initial stock on item creation")
                    .createdAt(LocalDateTime.now())
                    .build();
            transactionRepository.save(txn);
        }

        auditLogService.log("CREATE_INVENTORY", "INVENTORY", saved.getId(), null, saved.getName() + " (" + saved.getQuantity() + " " + saved.getUnit() + ")");
        return toDTO(saved);
    }

    @Transactional
    public InventoryItemDTO updateItem(Long id, InventoryItemRequestDTO dto) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));

        double oldQty = item.getQuantity();
        String oldVal = item.getName() + ": " + oldQty + " " + item.getUnit();
        item.setName(dto.getName());
        if (dto.getUnit() != null) item.setUnit(dto.getUnit());

        if (dto.getUnitVolume() != null && dto.getUnitVolume() > 0) {
            item.setUnitVolume(dto.getUnitVolume());
        } else if (dto.getVolume() != null && dto.getVolume() > 0) {
            item.setUnitVolume(dto.getVolume());
        }
        if (dto.getVolumeUnit() != null && !dto.getVolumeUnit().trim().isEmpty()) {
            item.setVolumeUnit(dto.getVolumeUnit().trim());
        }

        double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;

        if (dto.getQuantity() != null && !dto.getQuantity().equals(oldQty)) {
            double newQty = dto.getQuantity();
            item.setQuantity(newQty);
            item.setTotalVolume(newQty * unitVol);
            // Record manual adjustment transaction
            InventoryTransaction txn = InventoryTransaction.builder()
                    .itemId(item.getId())
                    .transactionType(newQty >= oldQty ? "STOCK_IN" : "STOCK_OUT")
                    .quantity(Math.abs(newQty - oldQty))
                    .previousStock(oldQty)
                    .newStock(newQty)
                    .unitVolume(unitVol)
                    .volumeUnit(item.getVolumeUnit())
                    .totalVolume(Math.abs(newQty - oldQty) * unitVol)
                    .costPerVolume(item.getCostPerVolume())
                    .supplierName(item.getSupplier())
                    .itemCategory(item.getItemCategory())
                    .notes("Manual quantity update via Edit Item")
                    .createdAt(LocalDateTime.now())
                    .build();
            transactionRepository.save(txn);
        } else {
            item.setTotalVolume(item.getQuantity() * unitVol);
        }

        if (dto.getMinQuantity() != null) item.setMinQuantity(dto.getMinQuantity());
        if (dto.getCostPerUnit() != null) {
            item.setCostPerUnit(dto.getCostPerUnit());
            if (unitVol > 0 && dto.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
                item.setCostPerVolume(dto.getCostPerUnit().divide(BigDecimal.valueOf(unitVol), 2, java.math.RoundingMode.HALF_UP));
            }
        }
        if (dto.getPackSize() != null) applyPack(item, dto);
        applyStockRules(item, dto);
        item.setSupplier(dto.getSupplier());
        if (dto.getItemCategory() != null && !dto.getItemCategory().trim().isEmpty()) {
            item.setItemCategory(normalizeCategory(dto.getItemCategory()));
        }
        if (dto.getActive() != null) item.setActive(dto.getActive());
        item.setLastUpdated(LocalDateTime.now());

        InventoryItem updated = inventoryItemRepository.save(item);
        auditLogService.log("UPDATE_INVENTORY", "INVENTORY", updated.getId(), oldVal, updated.getName() + ": " + updated.getQuantity() + " " + updated.getUnit());
        return toDTO(updated);
    }

    @Transactional
    public InventoryItemDTO decreaseStock(Long id, Double quantity, String reason, String username) {
        return decreaseStock(id, quantity, reason, username, false);
    }

    /** inPacks=true means the quantity is a number of packs (e.g. cases); it is converted to stock units. */
    @Transactional
    public InventoryItemDTO decreaseStock(Long id, Double quantity, String reason, String username, boolean inPacks) {
        return decreaseStock(id, quantity, reason, username, inPacks, null);
    }

    @Transactional
    public InventoryItemDTO decreaseStock(Long id, Double quantity, String reason, String username, boolean inPacks, String reasonType) {
        return decreaseStock(id, quantity, reason, username, inPacks, reasonType, false);
    }

    /** Why stock may be issued. USAGE is the default; the others feed the waste view. */
    public static final Set<String> REASON_TYPES = Set.of("USAGE", "EXPIRED", "SPOILED", "BREAKAGE", "STAFF_MEAL", "OTHER");

    /**
     * allowNegative: the purchase was not entered yet, so the stock goes below zero. It is shown as a negative number and returns to
     * positive by itself when the purchase is added (what was used is already counted).
     */
    @Transactional
    public InventoryItemDTO decreaseStock(Long id, Double quantity, String reason, String username, boolean inPacks, String reasonType, boolean allowNegative) {
        String type = reasonType == null || reasonType.isBlank() ? "USAGE" : reasonType.trim().toUpperCase();
        if (!REASON_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown reason type '" + reasonType + "'. Use one of: USAGE, EXPIRED, SPOILED, BREAKAGE, STAFF_MEAL, OTHER.");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Decrease quantity must be greater than zero.");
        }

        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));

        double enteredQty = quantity;
        quantity = StockUnits.toStockQuantity(quantity, inPacks, item.getPackSize());
        if (quantity <= 0) {
            throw new IllegalArgumentException("Decrease quantity is too small.");
        }

        double oldQty = item.getQuantity();
        if (oldQty < quantity - 0.0005 && !allowNegative) {
            throw new IllegalArgumentException("Insufficient stock. Current stock is " + oldQty + " " + item.getUnit()
                    + ", cannot decrease by " + quantity + " " + item.getUnit() + ". If the purchase was not entered yet, record it anyway: the stock shows as negative until the purchase is added.");
        }

        double newQty = StockUnits.round3(oldQty - quantity);
        item.setQuantity(newQty);
        double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;
        item.setTotalVolume(newQty * unitVol);
        item.setLastUpdated(LocalDateTime.now());
        InventoryItem updated = inventoryItemRepository.save(item);

        User user = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        Long userId = user != null ? user.getId() : null;
        String userRole = user != null && user.getRole() != null ? user.getRole().name() : "STAFF";
        String shortNote = newQty < 0 ? " [stock was short: purchase not entered yet]" : "";
        String packNote = (inPacks ? " (" + trimNum(enteredQty) + " " + packLabel(item) + " = " + trimNum(quantity) + " " + item.getUnit() + ")" : "") + shortNote;

        InventoryTransaction txn = InventoryTransaction.builder()
                .itemId(item.getId())
                .transactionType("STOCK_OUT")
                .quantity(quantity)
                .previousStock(oldQty)
                .newStock(newQty)
                .unitVolume(unitVol)
                .volumeUnit(item.getVolumeUnit())
                .totalVolume(quantity * unitVol)
                .costPerVolume(item.getCostPerVolume())
                .userId(userId)
                .userName(username != null ? username : "SYSTEM")
                .userRole(userRole)
                .itemCategory(item.getItemCategory())
                .reasonType(type)
                .notes((reason != null && !reason.trim().isEmpty() ? reason.trim() : defaultNote(type)) + packNote)
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(txn);

        auditLogService.log("STOCK_OUT", "INVENTORY", updated.getId(), String.valueOf(oldQty), String.valueOf(newQty));
        return toDTO(updated);
    }

    @Transactional
    public InventoryItemDTO updateStock(Long id, Double quantityChange, String username) {
        if (quantityChange == null || quantityChange == 0.0) {
            InventoryItem item = inventoryItemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));
            return toDTO(item);
        }

        if (quantityChange < 0) {
            return decreaseStock(id, Math.abs(quantityChange), "Quick Stock Decrease (-" + Math.abs(quantityChange) + ")", username);
        }

        // Direct stock increase requested
        User user = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        boolean isManagerOrAdmin = user != null && (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.MANAGER);
        if (!isManagerOrAdmin) {
            throw new IllegalArgumentException("Direct stock increase is disabled for staff. Stock increases must be recorded via Supplier Purchases.");
        }

        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));

        double oldQty = item.getQuantity();
        double newQty = oldQty + quantityChange;
        item.setQuantity(newQty);
        double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;
        item.setTotalVolume(newQty * unitVol);
        item.setLastUpdated(LocalDateTime.now());
        InventoryItem updated = inventoryItemRepository.save(item);

        Long userId = user != null ? user.getId() : null;
        String userRole = user != null && user.getRole() != null ? user.getRole().name() : "MANAGER";

        InventoryTransaction txn = InventoryTransaction.builder()
                .itemId(item.getId())
                .transactionType("STOCK_IN")
                .quantity(quantityChange)
                .previousStock(oldQty)
                .newStock(newQty)
                .unitVolume(unitVol)
                .volumeUnit(item.getVolumeUnit())
                .totalVolume(quantityChange * unitVol)
                .costPerVolume(item.getCostPerVolume())
                .userId(userId)
                .userName(username)
                .userRole(userRole)
                .supplierName(item.getSupplier())
                .itemCategory(item.getItemCategory())
                .notes("Direct Stock Adjustment (+" + quantityChange + ")")
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(txn);

        auditLogService.log("STOCK_ADJUST", "INVENTORY", updated.getId(), String.valueOf(oldQty), String.valueOf(newQty));
        return toDTO(updated);
    }

    @Transactional
    public void deleteItem(Long id) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));
        item.setActive(false);
        inventoryItemRepository.save(item);
        auditLogService.log("DELETE_INVENTORY", "INVENTORY", id, item.getName(), "Deactivated");
    }

    public List<InventoryTransactionDTO> getTransactions(
            LocalDate startDate,
            LocalDate endDate,
            Long userId,
            Long itemId,
            String type,
            Long supplierId
    ) {
        // Bounded: newest 2000 matches only (the ledger grows forever; callers narrow with date/item filters).
        List<InventoryTransaction> txns = transactionRepository.findBy(transactionSpec(startDate, endDate, userId, itemId, type, supplierId),
                q -> q.sortBy(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).limit(2000).all());
        return toTransactionDTOs(txns);
    }

    /** One page of the stock movement ledger, newest first, with the same optional filters as {@link #getTransactions}. */
    public org.springframework.data.domain.Page<InventoryTransactionDTO> getTransactions(
            LocalDate startDate,
            LocalDate endDate,
            Long userId,
            Long itemId,
            String type,
            Long supplierId,
            org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.domain.Sort newestFirst = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("id"));
        // No page requested: keep the bounded behaviour of the list version.
        org.springframework.data.domain.Pageable bounded = (pageable == null || pageable.isUnpaged())
                ? org.springframework.data.domain.PageRequest.of(0, 2000, newestFirst)
                : com.neram.pos.util.PagedResponse.withSort(pageable, newestFirst);
        org.springframework.data.domain.Page<InventoryTransaction> page =
                transactionRepository.findAll(transactionSpec(startDate, endDate, userId, itemId, type, supplierId), bounded);
        return new org.springframework.data.domain.PageImpl<>(toTransactionDTOs(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    private org.springframework.data.jpa.domain.Specification<InventoryTransaction> transactionSpec(
            LocalDate startDate, LocalDate endDate, Long userId, Long itemId, String type, Long supplierId) {
        LocalDateTime fromTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime toTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        return (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (itemId != null) {
                predicates.add(cb.equal(root.get("itemId"), itemId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplierId"), supplierId));
            }
            if (type != null && !type.trim().isEmpty() && !type.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(cb.upper(root.get("transactionType")), type.trim().toUpperCase()));
            }
            if (fromTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toTime));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private List<InventoryTransactionDTO> toTransactionDTOs(List<InventoryTransaction> txns) {
        Map<Long, InventoryItem> itemCache = new ConcurrentHashMap<>();
        return txns.stream().map(t -> {
            InventoryItem inv = itemCache.computeIfAbsent(t.getItemId(), id ->
                    inventoryItemRepository.findById(id).orElse(null)
            );
            return InventoryTransactionDTO.builder()
                    .id(t.getId())
                    .itemId(t.getItemId())
                    .itemName(inv != null ? inv.getName() : "Item #" + t.getItemId())
                    .unit(inv != null ? inv.getUnit() : "pcs")
                    .itemCategory(t.getItemCategory() != null ? t.getItemCategory() : (inv != null && inv.getItemCategory() != null ? inv.getItemCategory() : "Stock"))
                    .transactionType(t.getTransactionType())
                    .quantity(t.getQuantity())
                    .previousStock(t.getPreviousStock())
                    .newStock(t.getNewStock())
                    .unitVolume(t.getUnitVolume())
                    .volumeUnit(t.getVolumeUnit())
                    .totalVolume(t.getTotalVolume())
                    .costPerVolume(t.getCostPerVolume())
                    .userId(t.getUserId())
                    .userName(t.getUserName() != null ? t.getUserName() : "SYSTEM")
                    .userRole(t.getUserRole())
                    .supplierId(t.getSupplierId())
                    .supplierName(t.getSupplierName())
                    .invoiceId(t.getInvoiceId())
                    .invoiceReference(t.getInvoiceReference())
                    .unitPrice(t.getUnitPrice())
                    .notes(t.getNotes())
                    .reasonType(t.getReasonType())
                    .createdAt(t.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    /** Applies the optional pack (e.g. case = 12 bottles) from the request. packSize <= 1 clears it. */
    private void applyPack(InventoryItem item, InventoryItemRequestDTO dto) {
        Double size = StockUnits.normalizePackSize(dto.getPackSize());
        item.setPackSize(size);
        if (size == null) {
            item.setPackName(null);
        } else {
            String name = StockUnits.normalizePackName(dto.getPackName());
            item.setPackName(name != null ? name : "pack");
        }
    }

    private static String packLabel(InventoryItem item) {
        return item.getPackName() != null ? item.getPackName() : "pack";
    }

    private static String trimNum(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    public InventoryItemDTO toDTO(InventoryItem item) {
        return toDTO(item, Collections.emptyList(), LocalDate.now());
    }

    public InventoryItemDTO toDTO(InventoryItem item, List<StockExpiry.Receipt> receipts, LocalDate today) {
        boolean lowStock = item.getQuantity() <= item.getMinQuantity();
        Double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;
        Double totalVol = item.getTotalVolume() != null ? item.getTotalVolume() : (item.getQuantity() * unitVol);
        String volUnit = item.getVolumeUnit() != null ? item.getVolumeUnit() : item.getUnit();
        BigDecimal costPerVol = item.getCostPerVolume() != null ? item.getCostPerVolume() : BigDecimal.ZERO;
        if (costPerVol.compareTo(BigDecimal.ZERO) == 0 && unitVol > 0 && item.getCostPerUnit() != null && item.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
            costPerVol = item.getCostPerUnit().divide(BigDecimal.valueOf(unitVol), 2, java.math.RoundingMode.HALF_UP);
        }

        StockExpiry.Summary summary = StockExpiry.summarise(StockExpiry.onHand(item.getQuantity() != null ? item.getQuantity() : 0.0, receipts, item.getShelfLifeDays(), warnDaysOf(item), today));

        return InventoryItemDTO.builder()
                .id(item.getId())
                .name(item.getName())
                .unit(item.getUnit())
                .quantity(item.getQuantity())
                .minQuantity(item.getMinQuantity())
                .costPerUnit(item.getCostPerUnit())
                .unitVolume(unitVol)
                .volume(unitVol)
                .volumeUnit(volUnit)
                .totalVolume(totalVol)
                .costPerVolume(costPerVol)
                .packName(item.getPackName())
                .packSize(item.getPackSize())
                .supplier(item.getSupplier())
                .itemCategory(item.getItemCategory() != null ? item.getItemCategory() : "Stock")
                .active(item.getActive())
                .lowStock(lowStock)
                .autoDeduct(!Boolean.FALSE.equals(item.getAutoDeduct()))
                .shelfLifeDays(item.getShelfLifeDays())
                .expiryWarnDays(warnDaysOf(item))
                .nextExpiryDate(summary.nextExpiry())
                .expiryStatus(summary.status())
                .expiringQuantity(summary.affectedQuantity())
                .lastUpdated(item.getLastUpdated())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
