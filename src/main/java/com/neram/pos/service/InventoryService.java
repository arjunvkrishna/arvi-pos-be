package com.neram.pos.service;

import com.neram.pos.dto.InventoryItemDTO;
import com.neram.pos.dto.InventoryItemRequestDTO;
import com.neram.pos.dto.InventoryTransactionDTO;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.User;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.InventoryTransactionRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
        return items.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
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
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Decrease quantity must be greater than zero.");
        }

        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));

        double oldQty = item.getQuantity();
        if (oldQty < quantity) {
            throw new IllegalArgumentException("Insufficient stock. Current stock is " + oldQty + " " + item.getUnit()
                    + ", cannot decrease by " + quantity + " " + item.getUnit() + ".");
        }

        double newQty = oldQty - quantity;
        item.setQuantity(newQty);
        double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;
        item.setTotalVolume(newQty * unitVol);
        item.setLastUpdated(LocalDateTime.now());
        InventoryItem updated = inventoryItemRepository.save(item);

        User user = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        Long userId = user != null ? user.getId() : null;
        String userRole = user != null && user.getRole() != null ? user.getRole().name() : "STAFF";

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
                .notes(reason != null && !reason.trim().isEmpty() ? reason.trim() : "Stock Issued / Used")
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
        LocalDateTime fromTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime toTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        org.springframework.data.jpa.domain.Specification<InventoryTransaction> spec = (root, query, cb) -> {
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
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        // Bounded: newest 2000 matches only (the ledger grows forever; callers narrow with date/item filters).
        List<InventoryTransaction> txns = transactionRepository.findBy(spec, q -> q.sortBy(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).limit(2000).all());

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
                    .createdAt(t.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    public InventoryItemDTO toDTO(InventoryItem item) {
        boolean lowStock = item.getQuantity() <= item.getMinQuantity();
        Double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;
        Double totalVol = item.getTotalVolume() != null ? item.getTotalVolume() : (item.getQuantity() * unitVol);
        String volUnit = item.getVolumeUnit() != null ? item.getVolumeUnit() : item.getUnit();
        BigDecimal costPerVol = item.getCostPerVolume() != null ? item.getCostPerVolume() : BigDecimal.ZERO;
        if (costPerVol.compareTo(BigDecimal.ZERO) == 0 && unitVol > 0 && item.getCostPerUnit() != null && item.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
            costPerVol = item.getCostPerUnit().divide(BigDecimal.valueOf(unitVol), 2, java.math.RoundingMode.HALF_UP);
        }

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
                .supplier(item.getSupplier())
                .itemCategory(item.getItemCategory() != null ? item.getItemCategory() : "Stock")
                .active(item.getActive())
                .lowStock(lowStock)
                .lastUpdated(item.getLastUpdated())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
