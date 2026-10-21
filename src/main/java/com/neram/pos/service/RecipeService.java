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
     * Deducts recipe ingredients when order is dispatched to kitchen / KOT printed.
     * Guaranteed idempotent: skips if SALE_CONSUMPTION already recorded for the bill_item_id.
     */
    @Transactional
    public void deductIngredientsForOrder(Bill bill) {
        if (bill == null || bill.getItems() == null) return;

        for (BillItem item : bill.getItems()) {
            if (item.getMenuItemId() == null || item.getQuantity() == null || item.getQuantity() <= 0) continue;

            List<Recipe> recipes = recipeRepository.findByMenuItemIdAndActiveTrue(item.getMenuItemId());
            if (recipes.isEmpty()) continue;

            for (Recipe recipe : recipes) {
                InventoryItem invItem = inventoryItemRepository.findById(recipe.getInventoryItemId()).orElse(null);
                if (invItem == null) continue;

                // Check idempotency: have we already recorded SALE_CONSUMPTION for this bill item and recipe?
                List<InventoryTransaction> existingTxns = inventoryTransactionRepository.findByItemIdOrderByCreatedAtDesc(invItem.getId());
                boolean alreadyDeducted = existingTxns.stream()
                        .anyMatch(t -> "SALE_CONSUMPTION".equals(t.getTransactionType())
                                && item.getId() != null && item.getId().equals(t.getBillItemId())
                                && recipe.getId().equals(t.getRecipeId()));
                if (alreadyDeducted) {
                    continue;
                }

                BigDecimal totalRecipeQty = recipe.getQuantityPerServing().multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal convertedQty = convertQuantity(totalRecipeQty, recipe.getUnit(), invItem.getUnit());
                double qtyToDeduct = convertedQty.doubleValue();

                double prevStock = invItem.getQuantity() != null ? invItem.getQuantity() : 0.0;
                double newStock = prevStock - qtyToDeduct;
                invItem.setQuantity(newStock);
                double unitVol = invItem.getUnitVolume() != null ? invItem.getUnitVolume() : 1.0;
                invItem.setTotalVolume(newStock * unitVol);
                invItem.setLastUpdated(LocalDateTime.now());
                inventoryItemRepository.save(invItem);

                InventoryTransaction txn = InventoryTransaction.builder()
                        .itemId(invItem.getId())
                        .transactionType("SALE_CONSUMPTION")
                        .quantity(qtyToDeduct)
                        .previousStock(prevStock)
                        .newStock(newStock)
                        .unitVolume(unitVol)
                        .volumeUnit(invItem.getVolumeUnit())
                        .totalVolume(qtyToDeduct * unitVol)
                        .costPerVolume(invItem.getCostPerVolume())
                        .billId(bill.getId())
                        .billItemId(item.getId())
                        .recipeId(recipe.getId())
                        .unit(invItem.getUnit())
                        .unitPrice(invItem.getCostPerUnit())
                        .notes(String.format("Auto recipe deduction | Order #%s: %s x%d (Deducted %.3f %s)",
                                bill.getBillNumber(), item.getItemName(), item.getQuantity(), qtyToDeduct, invItem.getUnit()))
                        .createdAt(LocalDateTime.now())
                        .build();
                inventoryTransactionRepository.save(txn);
            }
        }
    }

    /**
     * Handles ingredient stock adjustment on bill cancellation / void:
     * - If NOT prepared (cancelled before kitchen prep/print): restores stock via REFUND_REVERSAL.
     * - If prepared (cancelled after kitchen printed/prepped): records WASTE; usable stock is NOT restored.
     */
    @Transactional
    public void handleOrderCancellation(Bill bill, boolean prepared, String reason) {
        if (bill == null || bill.getId() == null) return;

        List<InventoryTransaction> allTxns = inventoryTransactionRepository.findAll();
        List<InventoryTransaction> consumptionTxns = allTxns.stream()
                .filter(t -> "SALE_CONSUMPTION".equals(t.getTransactionType()) && bill.getId().equals(t.getBillId()))
                .collect(Collectors.toList());

        for (InventoryTransaction origTxn : consumptionTxns) {
            InventoryItem invItem = inventoryItemRepository.findById(origTxn.getItemId()).orElse(null);
            if (invItem == null) continue;

            if (!prepared) {
                // Restore usable stock
                double prevStock = invItem.getQuantity() != null ? invItem.getQuantity() : 0.0;
                double newStock = prevStock + origTxn.getQuantity();
                invItem.setQuantity(newStock);
                double unitVol = invItem.getUnitVolume() != null ? invItem.getUnitVolume() : 1.0;
                invItem.setTotalVolume(newStock * unitVol);
                invItem.setLastUpdated(LocalDateTime.now());
                inventoryItemRepository.save(invItem);

                InventoryTransaction reverseTxn = InventoryTransaction.builder()
                        .itemId(invItem.getId())
                        .transactionType("REFUND_REVERSAL")
                        .quantity(origTxn.getQuantity())
                        .previousStock(prevStock)
                        .newStock(newStock)
                        .unitVolume(unitVol)
                        .volumeUnit(invItem.getVolumeUnit())
                        .totalVolume(origTxn.getQuantity() * unitVol)
                        .costPerVolume(invItem.getCostPerVolume())
                        .billId(bill.getId())
                        .billItemId(origTxn.getBillItemId())
                        .recipeId(origTxn.getRecipeId())
                        .unit(invItem.getUnit())
                        .unitPrice(invItem.getCostPerUnit())
                        .notes(String.format("Restored stock for cancelled order #%s: %s", bill.getBillNumber(), reason))
                        .createdAt(LocalDateTime.now())
                        .build();
                inventoryTransactionRepository.save(reverseTxn);
            } else {
                // Food was prepped and discarded: record waste, do not return to inventory
                InventoryTransaction wasteTxn = InventoryTransaction.builder()
                        .itemId(invItem.getId())
                        .transactionType("WASTE")
                        .quantity(origTxn.getQuantity())
                        .previousStock(invItem.getQuantity())
                        .newStock(invItem.getQuantity())
                        .billId(bill.getId())
                        .billItemId(origTxn.getBillItemId())
                        .recipeId(origTxn.getRecipeId())
                        .unit(invItem.getUnit())
                        .unitPrice(invItem.getCostPerUnit())
                        .notes(String.format("Food prepped & discarded for voided order #%s: %s", bill.getBillNumber(), reason))
                        .createdAt(LocalDateTime.now())
                        .build();
                inventoryTransactionRepository.save(wasteTxn);
            }
        }
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
                .quantityPerServing(r.getQuantityPerServing())
                .unit(r.getUnit())
                .active(r.getActive())
                .costPerServing(costPerServing)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
