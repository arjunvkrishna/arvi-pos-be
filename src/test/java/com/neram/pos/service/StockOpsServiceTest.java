package com.neram.pos.service;

import com.neram.pos.dto.RecipeDTO;
import com.neram.pos.dto.StockOpsDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockOpsServiceTest {
    @Mock private InventoryItemRepository items;
    @Mock private InventoryTransactionRepository txns;
    @Mock private SupplierInvoiceItemRepository invItems;
    @Mock private SupplierInvoiceRepository invoices;
    @Mock private SupplierRepository suppliers;
    @Mock private MenuItemRepository menu;
    @Mock private RecipeService recipes;
    @Mock private AppSettingService settings;

    private StockOpsService svc;
    private final List<InventoryItem> stock = new ArrayList<>();
    private final List<InventoryTransaction> moves = new ArrayList<>();
    private final List<SupplierInvoiceItem> lines = new ArrayList<>();
    private final Map<Long, SupplierInvoice> inv = new HashMap<>();

    private InventoryItem item(long id, String name, double qty, double min, Double pack) {
        InventoryItem i = new InventoryItem();
        i.setId(id); i.setName(name); i.setUnit("pcs"); i.setQuantity(qty); i.setMinQuantity(min); i.setCostPerUnit(new BigDecimal("10.00")); i.setItemCategory("Stock");
        i.setPackSize(pack); i.setPackName(pack != null ? "case" : null);
        stock.add(i); return i;
    }
    private void use(long itemId, double q, String type, int daysAgo) {
        moves.add(InventoryTransaction.builder().itemId(itemId).transactionType(type).quantity(q).createdAt(LocalDateTime.now().minusDays(daysAgo)).build());
    }
    /** a purchase line: total paid for stock units received */
    private void buy(long invoiceId, long itemId, long supplierId, LocalDate date, double total, double stockQty) {
        SupplierInvoice si = new SupplierInvoice(); si.setId(invoiceId); si.setSupplierId(supplierId); si.setInvoiceDate(date); inv.put(invoiceId, si);
        SupplierInvoiceItem l = new SupplierInvoiceItem(); l.setInvoiceId(invoiceId); l.setInventoryItemId(itemId);
        l.setTotalPrice(BigDecimal.valueOf(total)); l.setUnitPrice(BigDecimal.valueOf(total)); l.setStockQuantity(stockQty); lines.add(l);
    }
    private RecipeDTO.RecipeResponse recipe(long dish, String dishName, long itemId, double cost) {
        return RecipeDTO.RecipeResponse.builder().menuItemId(dish).menuItemName(dishName).inventoryItemId(itemId).active(true).costPerServing(BigDecimal.valueOf(cost)).build();
    }
    private MenuItem dish(long id, String name, double price) {
        MenuItem m = new MenuItem(); m.setId(id); m.setName(name); m.setPrice(BigDecimal.valueOf(price)); m.setActive(true); return m;
    }

    @BeforeEach
    void setUp() {
        svc = new StockOpsService(items, txns, invItems, invoices, suppliers, menu, recipes, settings);
        when(items.findByActiveTrue()).thenAnswer(a -> stock);
        when(txns.findByCreatedAtBetween(any(), any())).thenAnswer(a -> moves);
        when(invItems.findByInventoryItemIdIn(any())).thenAnswer(a -> lines);
        when(invoices.findAllById(any())).thenAnswer(a -> new ArrayList<>(inv.values()));
        Supplier s = new Supplier(); s.setId(1L); s.setName("Coorg Foods");
        Supplier s2 = new Supplier(); s2.setId(2L); s2.setName("Metro");
        when(suppliers.findAll()).thenReturn(List.of(s, s2));
        when(settings.getSetting(anyString(), anyString())).thenAnswer(a -> a.getArgument(1));
    }

    @Test
    void reorderListHasOutBelowMinAndRunningOutSoonButNotHealthyItems() {
        item(1, "Cola", 0, 5, 12.0); item(2, "Bun", 4, 10, null); item(3, "Cheese", 20, 5, null); item(4, "Salt", 100, 5, null);
        use(3, 56, "SALE_CONSUMPTION", 3);                         // 56 in 14 days = 4/day -> 5 days left
        StockOpsDTO.Reorder r = svc.reorder();
        Map<String, StockOpsDTO.ReorderRow> by = new HashMap<>(); r.getItems().forEach(x -> by.put(x.getName(), x));
        assertEquals("OUT", by.get("Cola").getReason());
        assertEquals("BELOW_MIN", by.get("Bun").getReason());
        assertEquals("RUNS_OUT_SOON", by.get("Cheese").getReason());
        assertEquals(5.0, by.get("Cheese").getDaysLeft());
        assertFalse(by.containsKey("Salt"));
    }

    @Test
    void suggestionsUseWholePacksAndUsagePace() {
        item(1, "Cola", 0, 5, 12.0);
        use(1, 42, "STOCK_OUT", 2);                               // 3/day -> a week = 21 -> 2 cases
        StockOpsDTO.ReorderRow row = svc.reorder().getItems().get(0);
        assertEquals(24.0, row.getSuggestedUnits());
        assertEquals(2.0, row.getSuggestedPacks());
        assertEquals("case", row.getPackName());
    }

    @Test
    void refundsPutBackReduceTheUsagePace() {
        item(1, "Bun", 20, 5, null);
        use(1, 140, "SALE_CONSUMPTION", 2); use(1, 70, "REFUND_REVERSAL", 2);   // net 70 -> 5/day -> 4 days left
        assertEquals(4.0, svc.reorder().getItems().get(0).getDaysLeft());
    }

    @Test
    void supplierAndCostComeFromTheLatestPurchase() {
        item(1, "Cola", 0, 5, 12.0);
        buy(10, 1, 2, LocalDate.now().minusDays(30), 216, 12);     // 18 each from Metro
        buy(11, 1, 1, LocalDate.now().minusDays(3), 240, 12);      // 20 each from Coorg (latest)
        StockOpsDTO.ReorderRow row = svc.reorder().getItems().get(0);
        assertEquals("Coorg Foods", row.getSupplier());
        assertEquals(20.0, row.getLastUnitCost());
        assertEquals(row.getSuggestedUnits() * 20.0, row.getEstimatedCost(), 0.01);
    }

    @Test
    void fixedAssetsAreNeverOnTheReorderList() {
        InventoryItem chair = item(1, "Chair", 0, 2, null); chair.setItemCategory("Fixed asset");
        assertTrue(svc.reorder().getItems().isEmpty());
    }

    @Test
    void aPriceRiseOverTheThresholdIsFlaggedWithTheDishesThatUseIt() {
        item(1, "Cheese", 5, 1, null);
        buy(10, 1, 1, LocalDate.now().minusDays(20), 100, 1);     // 100 per unit
        buy(11, 1, 1, LocalDate.now().minusDays(2), 125, 1);      // 125 per unit: +25%
        when(recipes.getAllRecipes()).thenReturn(List.of(recipe(50, "Cheese Burger", 1, 25)));
        StockOpsDTO.CostAlerts a = svc.costAlerts();
        assertEquals(1, a.getChanges().size());
        StockOpsDTO.CostChange c = a.getChanges().get(0);
        assertEquals("UP", c.getDirection()); assertEquals(25.0, c.getChangePercent());
        assertEquals(List.of("Cheese Burger"), c.getDishes());
    }

    @Test
    void smallMovesAndSinglePurchasesAreNotAlerts() {
        item(1, "Cheese", 5, 1, null); item(2, "Oil", 5, 1, null);
        buy(10, 1, 1, LocalDate.now().minusDays(20), 100, 1); buy(11, 1, 1, LocalDate.now().minusDays(2), 105, 1);   // +5%
        buy(12, 2, 1, LocalDate.now().minusDays(2), 90, 1);                                                           // only one purchase
        when(recipes.getAllRecipes()).thenReturn(List.of());
        assertTrue(svc.costAlerts().getChanges().isEmpty());
    }

    @Test
    void aFallingPriceIsReportedAsDown() {
        item(1, "Cheese", 5, 1, null);
        buy(10, 1, 1, LocalDate.now().minusDays(20), 100, 1); buy(11, 1, 1, LocalDate.now().minusDays(2), 70, 1);
        when(recipes.getAllRecipes()).thenReturn(List.of());
        assertEquals("DOWN", svc.costAlerts().getChanges().get(0).getDirection());
    }

    @Test
    void dishesUnderTheMarginFloorAreListedWorstFirstAndBelowCostIsNegative() {
        item(1, "Cheese", 5, 1, null);
        when(menu.findAll()).thenReturn(List.of(dish(50, "Cheese Burger", 100), dish(51, "Loss Leader", 80), dish(52, "Fine Dish", 100)));
        when(recipes.getAllRecipes()).thenReturn(List.of(recipe(50, "Cheese Burger", 1, 75), recipe(51, "Loss Leader", 1, 90), recipe(52, "Fine Dish", 1, 40)));
        List<StockOpsDTO.DishMargin> low = svc.costAlerts().getLowMargin();
        assertEquals(List.of("Loss Leader", "Cheese Burger"), low.stream().map(StockOpsDTO.DishMargin::getName).toList());
        assertEquals("NEGATIVE", low.get(0).getStatus()); assertEquals("LOW", low.get(1).getStatus());
        assertEquals(25.0, low.get(1).getMarginPercent());
    }

    @Test
    void dishesWithoutARecipeOrInactiveAreNotJudged() {
        MenuItem off = dish(53, "Old", 100); off.setActive(false);
        when(menu.findAll()).thenReturn(List.of(dish(54, "No Recipe", 100), off));
        when(recipes.getAllRecipes()).thenReturn(List.of(recipe(53, "Old", 1, 99)));
        assertTrue(svc.costAlerts().getLowMargin().isEmpty());
    }

    @Test
    void settingsHaveDefaultsAndAreValidated() {
        StockOpsDTO.CostAlertSettings s = svc.getSettings();
        assertEquals(10.0, s.getChangeThresholdPercent()); assertEquals(30.0, s.getMarginFloorPercent());
        assertThrows(IllegalArgumentException.class, () -> svc.saveSettings(new StockOpsDTO.CostAlertSettings(0.0, 30.0), "m"));
        assertThrows(IllegalArgumentException.class, () -> svc.saveSettings(new StockOpsDTO.CostAlertSettings(10.0, 99.0), "m"));
        assertThrows(IllegalArgumentException.class, () -> svc.saveSettings(new StockOpsDTO.CostAlertSettings(null, 30.0), "m"));
    }
}
