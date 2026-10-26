package com.neram.pos.service;

import com.neram.pos.dto.RecipeDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.Recipe;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.InventoryTransactionRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final MenuItemRepository menuItemRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final AuditLogService auditLogService;

    public List<RecipeDTO.RecipeResponse> getRecipesForMenuItem(Long menuItemId) {
        return recipeRepository.findByMenuItemId(menuItemId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<RecipeDTO.RecipeResponse> getAllRecipes() {
        return recipeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public RecipeDTO.RecipeResponse createRecipe(RecipeDTO.RecipeRequest req) {
        if (req.getMenuItemId() == null || req.getInventoryItemId() == null) {
            throw new IllegalArgumentException("Menu item ID and Inventory item ID are required.");
        }
        if (req.getQuantityPerServing() == null || req.getQuantityPerServing().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity per serving must be greater than zero.");
        }

        MenuItem menuItem = menuItemRepository.findById(req.getMenuItemId())
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found: " + req.getMenuItemId()));
        InventoryItem invItem = inventoryItemRepository.findById(req.getInventoryItemId())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + req.getInventoryItemId()));

        String recipeUnit = req.getUnit() != null ? req.getUnit().trim().toLowerCase() : invItem.getUnit().toLowerCase();
        validateUnitCompatibility(recipeUnit, invItem.getUnit());

        recipeRepository.findByMenuItemIdAndInventoryItemId(req.getMenuItemId(), req.getInventoryItemId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Recipe linkage already exists for this dish and ingredient.");
                });

        Recipe recipe = Recipe.builder()
                .menuItemId(menuItem.getId())
                .inventoryItemId(invItem.getId())
                .quantityPerServing(req.getQuantityPerServing())
                .unit(recipeUnit)
                .active(req.getActive() != null ? req.getActive() : true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Recipe saved = recipeRepository.save(recipe);
        auditLogService.log("CREATE_RECIPE", "RECIPE", saved.getId(), null,
                String.format("Linked %s -> %s (%.3f %s)", menuItem.getName(), invItem.getName(), saved.getQuantityPerServing(), saved.getUnit()));
        return toDTO(saved);
    }

    @Transactional
    public RecipeDTO.RecipeResponse updateRecipe(Long id, RecipeDTO.RecipeRequest req) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found with id: " + id));

        if (req.getQuantityPerServing() != null) {
            if (req.getQuantityPerServing().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Quantity per serving must be greater than zero.");
            }
            recipe.setQuantityPerServing(req.getQuantityPerServing());
        }
        if (req.getUnit() != null) {
            InventoryItem invItem = inventoryItemRepository.findById(recipe.getInventoryItemId()).orElse(null);
            if (invItem != null) {
                validateUnitCompatibility(req.getUnit().trim().toLowerCase(), invItem.getUnit());
            }
            recipe.setUnit(req.getUnit().trim().toLowerCase());
        }
        if (req.getActive() != null) {
            recipe.setActive(req.getActive());
        }
        recipe.setUpdatedAt(LocalDateTime.now());
        Recipe saved = recipeRepository.save(recipe);
        return toDTO(saved);
    }

    @Transactional
    public void deleteRecipe(Long id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found with id: " + id));
        recipeRepository.delete(recipe);
        auditLogService.log("DELETE_RECIPE", "RECIPE", id, null, "Deleted recipe linkage #" + id);
    }

    /**
     * Stock follows the SETTLED bill, not the kitchen ticket. This brings the stock taken for one bill in line with what the bill
     * holds now: on payment it takes what the recipes need, and when a paid bill is edited (Bill Log) it takes or returns only the
     * difference. Running it twice changes nothing. Items issued by hand (auto-deduct off) are left alone, and a recipe whose unit
     * cannot be converted is skipped with a warning so a payment is never held up by a stock mapping.
     */
    @Transactional
    public void settleBill(Bill bill) {
        if (bill == null || bill.getId() == null) return;
        applyDifference(bill, desiredConsumption(bill), "settled");
    }

    /** Puts back everything taken for this bill (cancelled, or voided with the food not made). Safe to call again. */
    @Transactional
    public void restoreBill(Bill bill, String reason) {
        if (bill == null || bill.getId() == null) return;
        applyDifference(bill, java.util.Collections.emptyMap(), reason == null || reason.isBlank() ? "cancelled" : reason);
    }

    /**
     * The food was made and thrown away (void before payment): the stock it needed is taken now and the loss is recorded as waste,
     * once per bill. Stock a bill printed under the old rule already took is not taken twice.
     */
    @Transactional
    public void writeOffBill(Bill bill, String reason) {
        if (bill == null || bill.getId() == null) return;
        List<InventoryTransaction> txns = inventoryTransactionRepository.findByBillId(bill.getId());
        if (txns.stream().anyMatch(t -> "WASTE".equals(t.getTransactionType()))) return;
        java.util.Map<Long, Double> recorded = recordedConsumption(txns);
        java.util.Map<Long, Double> needed = new java.util.LinkedHashMap<>(desiredConsumption(bill));
        recorded.forEach((id, qty) -> needed.merge(id, qty, Math::max));
        for (java.util.Map.Entry<Long, Double> e : needed.entrySet()) {
            double wasted = StockUnits.round3(e.getValue());
            if (wasted <= 0.0005) continue;
            InventoryItem inv = inventoryItemRepository.findById(e.getKey()).orElse(null);
            if (inv == null) continue;
            double takeNow = Math.max(0.0, StockUnits.round3(wasted - recorded.getOrDefault(e.getKey(), 0.0)));
            double prev = inv.getQuantity() != null ? inv.getQuantity() : 0.0;
            double next = StockUnits.round3(prev - takeNow);
            if (takeNow > 0) {
                inv.setQuantity(next);
                double unitVol = inv.getUnitVolume() != null ? inv.getUnitVolume() : 1.0;
                inv.setTotalVolume(next * unitVol);
                inv.setLastUpdated(LocalDateTime.now());
                inventoryItemRepository.save(inv);
            }
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .itemId(inv.getId()).transactionType("WASTE").quantity(wasted)
                    .previousStock(prev).newStock(next)
                    .billId(bill.getId()).unit(inv.getUnit()).unitPrice(inv.getCostPerUnit()).reasonType("SPOILED")
                    .notes(String.format("Food made and discarded for voided bill #%s: %s", bill.getBillNumber(), reason))
                    .createdAt(LocalDateTime.now()).build());
        }
    }

    /** Stock units the recipes need for the bill as it is now, per inventory item. */
    java.util.Map<Long, Double> desiredConsumption(Bill bill) {
        java.util.Map<Long, Double> out = new java.util.LinkedHashMap<>();
        if (bill.getItems() == null) return out;
        for (BillItem item : bill.getItems()) {
            Long dishId = menuItemIdOf(item);
            if (dishId == null || item.getQuantity() == null || item.getQuantity() <= 0) continue;
            for (Recipe recipe : recipeRepository.findByMenuItemIdAndActiveTrue(dishId)) {
                InventoryItem inv = inventoryItemRepository.findById(recipe.getInventoryItemId()).orElse(null);
                if (inv == null) continue;
                // Items issued by hand (milk, oil...) are not reduced by sales: the recipe stays for costing only
                if (Boolean.FALSE.equals(inv.getAutoDeduct())) continue;
                try {
                    BigDecimal qty = convertQuantity(recipe.getQuantityPerServing().multiply(BigDecimal.valueOf(item.getQuantity())), recipe.getUnit(), inv.getUnit());
                    out.merge(inv.getId(), qty.doubleValue(), Double::sum);
                } catch (IllegalArgumentException e) {
                    log.warn("Recipe {} skipped for bill {}: {}", recipe.getId(), bill.getBillNumber(), e.getMessage());
                }
            }
        }
        return out;
    }

    /** The dish of a bill line. A line without an id (an old bill) is matched by its name when exactly one dish has that name. */
    private Long menuItemIdOf(BillItem item) {
        if (item.getMenuItemId() != null) return item.getMenuItemId();
        if (item.getItemName() == null || item.getItemName().isBlank()) return null;
        List<MenuItem> same = menuItemRepository.findByNameIgnoreCase(item.getItemName().trim());
        return same.size() == 1 ? same.get(0).getId() : null;
    }

    /** Stock units already taken for a bill, per inventory item: sales minus what was put back. */
    private java.util.Map<Long, Double> recordedConsumption(List<InventoryTransaction> txns) {
        java.util.Map<Long, Double> out = new java.util.LinkedHashMap<>();
        for (InventoryTransaction t : txns) {
            if (t.getItemId() == null || t.getQuantity() == null) continue;
            if ("SALE_CONSUMPTION".equals(t.getTransactionType())) out.merge(t.getItemId(), t.getQuantity(), Double::sum);
            else if ("REFUND_REVERSAL".equals(t.getTransactionType())) out.merge(t.getItemId(), -t.getQuantity(), Double::sum);
        }
        return out;
    }

    private void applyDifference(Bill bill, java.util.Map<Long, Double> desired, String why) {
        java.util.Map<Long, Double> recorded = recordedConsumption(inventoryTransactionRepository.findByBillId(bill.getId()));
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>(desired.keySet());
        ids.addAll(recorded.keySet());
        for (Long invId : ids) {
            double delta = StockUnits.round3(desired.getOrDefault(invId, 0.0) - recorded.getOrDefault(invId, 0.0));
            if (Math.abs(delta) < 0.0005) continue;
            InventoryItem inv = inventoryItemRepository.findById(invId).orElse(null);
            if (inv == null) continue;
            double prev = inv.getQuantity() != null ? inv.getQuantity() : 0.0;
            double next = StockUnits.round3(prev - delta);
            inv.setQuantity(next);
            double unitVol = inv.getUnitVolume() != null ? inv.getUnitVolume() : 1.0;
            inv.setTotalVolume(next * unitVol);
            inv.setLastUpdated(LocalDateTime.now());
            inventoryItemRepository.save(inv);
            boolean taken = delta > 0;
            String dishes = bill.getItems() == null ? "" : bill.getItems().stream().filter(i -> i.getQuantity() != null && i.getQuantity() > 0)
                    .map(i -> i.getItemName() + " x" + i.getQuantity()).collect(Collectors.joining(", "));
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .itemId(inv.getId()).transactionType(taken ? "SALE_CONSUMPTION" : "REFUND_REVERSAL").quantity(Math.abs(delta))
                    .previousStock(prev).newStock(next).unitVolume(unitVol).volumeUnit(inv.getVolumeUnit())
                    .totalVolume(Math.abs(delta) * unitVol).costPerVolume(inv.getCostPerVolume())
                    .billId(bill.getId()).unit(inv.getUnit()).unitPrice(inv.getCostPerUnit())
                    .notes(String.format("%s | Bill #%s (%s): %s", taken ? "Stock used by sale" : "Stock returned", bill.getBillNumber(), why, dishes))
                    .createdAt(LocalDateTime.now()).build());
        }
    }

    /**
     * Re-attributes the stock consumption already recorded for one bill to another. Used when two orders are merged so that a
     * later cancellation/void of the surviving bill reverses (or wastes) the stock of the items that were moved into it.
     */
    @Transactional
    public void transferConsumption(Long fromBillId, Long toBillId) {
        if (fromBillId == null || toBillId == null || fromBillId.equals(toBillId)) return;
        List<InventoryTransaction> txns = inventoryTransactionRepository.findByBillId(fromBillId);
        for (InventoryTransaction t : txns) {
            t.setBillId(toBillId);
        }
        inventoryTransactionRepository.saveAll(txns);
    }

    /**
     * Cancel / void happen before payment (a paid bill goes through a refund). If the food was NOT made nothing is taken (older bills
     * printed under the previous rule are put back from their records); if it was made and thrown away its stock is taken now and
     * the loss is recorded as waste.
     */
    @Transactional
    public void handleOrderCancellation(Bill bill, boolean prepared, String reason) {
        if (prepared) writeOffBill(bill, reason); else restoreBill(bill, reason);
    }

    public static BigDecimal convertQuantity(BigDecimal qty, String fromUnit, String toUnit) {
        String from = fromUnit.trim().toLowerCase();
        String to = toUnit.trim().toLowerCase();
        if (from.equals(to)) return qty;

        // Weight: kg <-> g
        if ("g".equals(from) && "kg".equals(to)) {
            return qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        }
        if ("kg".equals(from) && "g".equals(to)) {
            return qty.multiply(BigDecimal.valueOf(1000));
        }

        // Volume: l <-> ml
        if ("ml".equals(from) && ("l".equals(to) || "litre".equals(to) || "liter".equals(to))) {
            return qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        }
        if (("l".equals(from) || "litre".equals(from) || "liter".equals(from)) && "ml".equals(to)) {
            return qty.multiply(BigDecimal.valueOf(1000));
        }

        throw new IllegalArgumentException(String.format("Incompatible unit conversion from '%s' to '%s'", fromUnit, toUnit));
    }

    public static void validateUnitCompatibility(String unitA, String unitB) {
        String a = unitA.trim().toLowerCase();
        String b = unitB.trim().toLowerCase();
        if (a.equals(b)) return;

        boolean aWeight = "g".equals(a) || "kg".equals(a);
        boolean bWeight = "g".equals(b) || "kg".equals(b);
        if (aWeight && bWeight) return;

        boolean aVol = "ml".equals(a) || "l".equals(a) || "litre".equals(a) || "liter".equals(a);
        boolean bVol = "ml".equals(b) || "l".equals(b) || "litre".equals(b) || "liter".equals(b);
        if (aVol && bVol) return;

        throw new IllegalArgumentException(String.format("Incompatible units: '%s' and '%s'", unitA, unitB));
    }

    private RecipeDTO.RecipeResponse toDTO(Recipe r) {
        MenuItem menuItem = menuItemRepository.findById(r.getMenuItemId()).orElse(null);
        InventoryItem invItem = inventoryItemRepository.findById(r.getInventoryItemId()).orElse(null);

        BigDecimal costPerServing = BigDecimal.ZERO;
        if (invItem != null && invItem.getCostPerUnit() != null && r.getQuantityPerServing() != null) {
            try {
                BigDecimal convertedQty = convertQuantity(r.getQuantityPerServing(), r.getUnit(), invItem.getUnit());
                costPerServing = convertedQty.multiply(invItem.getCostPerUnit()).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }

        return RecipeDTO.RecipeResponse.builder()
                .id(r.getId())
                .menuItemId(r.getMenuItemId())
                .menuItemName(menuItem != null ? menuItem.getName() : null)
                .inventoryItemId(r.getInventoryItemId())
                .inventoryItemName(invItem != null ? invItem.getName() : null)
                .inventoryItemUnit(invItem != null ? invItem.getUnit() : null)
                .inventoryCostPerUnit(invItem != null ? invItem.getCostPerUnit() : BigDecimal.ZERO)
                .inventoryAutoDeduct(invItem == null || !Boolean.FALSE.equals(invItem.getAutoDeduct()))
                .quantityPerServing(r.getQuantityPerServing())
                .unit(r.getUnit())
                .active(r.getActive())
                .costPerServing(costPerServing)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
