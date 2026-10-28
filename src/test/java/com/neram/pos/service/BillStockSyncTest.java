package com.neram.pos.service;

import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.Recipe;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.InventoryTransactionRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Stock follows the settled bill. A bun comes in packets of 4 (2 packets = 8 buns): selling 2 burgers leaves 6 buns, which is
 * 1 packet and 2 buns. An edit of a paid bill takes or returns only the difference; cancel puts back; void of made food is waste.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillStockSyncTest {
    private static final long BURGER = 100, MINI = 101, SANDWICH = 102, WATER = 103;

    @Mock private RecipeRepository recipeRepo;
    @Mock private MenuItemRepository menuRepo;
    @Mock private InventoryItemRepository itemRepo;
    @Mock private InventoryTransactionRepository txnRepo;
    @Mock private AuditLogService audit;

    private RecipeService recipes;
    private final Map<Long, InventoryItem> stock = new HashMap<>();
    private final List<InventoryTransaction> txns = new ArrayList<>();
    private final List<Recipe> recipeRows = new ArrayList<>();
    private InventoryItem bun, miniBun, bread, water, milk;

    private InventoryItem item(long id, String name, String unit, double qty) {
        InventoryItem i = new InventoryItem();
        i.setId(id); i.setName(name); i.setUnit(unit); i.setQuantity(qty);
        stock.put(id, i);
        return i;
    }
    private void link(long dish, InventoryItem inv, String perServing, String unit) {
        Recipe r = new Recipe();
        r.setId((long) recipeRows.size() + 1); r.setMenuItemId(dish); r.setInventoryItemId(inv.getId());
        r.setQuantityPerServing(new BigDecimal(perServing)); r.setUnit(unit); r.setActive(true);
        recipeRows.add(r);
    }
    private Bill bill(long id, Object... dishAndQty) {
        Bill b = new Bill(); b.setId(id); b.setBillNumber("NRM-" + id);
        List<BillItem> items = new ArrayList<>();
        for (int k = 0; k < dishAndQty.length; k += 2) {
            BillItem bi = new BillItem(); bi.setId(1000L + k); bi.setMenuItemId((Long) dishAndQty[k]); bi.setItemName("Dish" + dishAndQty[k]); bi.setQuantity((Integer) dishAndQty[k + 1]);
            items.add(bi);
        }
        b.setItems(items);
        return b;
    }
    private void edit(Bill b, Object... dishAndQty) { b.setItems(bill(b.getId(), dishAndQty).getItems()); }

    @BeforeEach
    void setUp() {
        recipes = new RecipeService(recipeRepo, menuRepo, itemRepo, txnRepo, audit, org.mockito.Mockito.mock(com.neram.pos.repository.ModifierRepository.class));
        bun = item(1, "Burger Bun", "piece", 8);           // 2 packets of 4
        miniBun = item(2, "Mini Burger Bun", "piece", 12);
        bread = item(3, "Sandwich Bread", "piece", 20);
        water = item(4, "Mineral Water Bottle", "bottle", 24);
        milk = item(5, "Milk", "packet", 10); milk.setAutoDeduct(false);
        link(BURGER, bun, "1", "piece");
        link(MINI, miniBun, "1", "piece");
        link(SANDWICH, bread, "2", "piece");
        link(WATER, water, "1", "bottle");
        link(SANDWICH, milk, "1", "packet");                // a recipe row for an item issued by hand: cost only

        when(itemRepo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(stock.get(i.getArgument(0))));
        when(itemRepo.save(any(InventoryItem.class))).thenAnswer(i -> i.getArgument(0));
        when(recipeRepo.findByMenuItemIdAndActiveTrue(anyLong())).thenAnswer(i -> recipeRows.stream().filter(r -> r.getMenuItemId().equals(i.getArgument(0))).collect(Collectors.toList()));
        when(txnRepo.save(any(InventoryTransaction.class))).thenAnswer(i -> { InventoryTransaction t = i.getArgument(0); txns.add(t); return t; });
        when(txnRepo.findByBillId(anyLong())).thenAnswer(i -> txns.stream().filter(t -> i.getArgument(0).equals(t.getBillId())).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("2 packets of 4 buns, 2 burgers sold: 6 buns left = 1 packet + 2 buns; 2 more: 4 left = 1 packet")
    void packetsOfBuns() {
        recipes.settleBill(bill(1, BURGER, 2));
        assertEquals(6.0, bun.getQuantity());
        recipes.settleBill(bill(2, BURGER, 2));
        assertEquals(4.0, bun.getQuantity());
        assertEquals(1.0, bun.getQuantity() / 4.0, "4 buns = exactly 1 packet");
    }

    @Test
    @DisplayName("settling the same bill again (retry, second click) changes nothing")
    void idempotent() {
        Bill b = bill(1, BURGER, 3);
        recipes.settleBill(b); recipes.settleBill(b); recipes.settleBill(b);
        assertEquals(5.0, bun.getQuantity());
        assertEquals(1, txns.size());
    }

    @Test
    @DisplayName("a mini burger takes a mini bun and a burger takes a burger bun: they are different stock")
    void differentBuns() {
        recipes.settleBill(bill(1, BURGER, 1, MINI, 3));
        assertEquals(7.0, bun.getQuantity());
        assertEquals(9.0, miniBun.getQuantity());
    }

    @Test
    @DisplayName("one dish with two ingredients: bread by recipe, milk (issued by hand) untouched")
    void manualIngredientsAreLeftAlone() {
        recipes.settleBill(bill(1, SANDWICH, 2));
        assertEquals(16.0, bread.getQuantity());
        assertEquals(10.0, milk.getQuantity());
    }

    @Test
    @DisplayName("two dishes that share an ingredient take it together; a bottle drink maps one to one")
    void sharedAndBottle() {
        link(MINI, bun, "1", "piece");                       // a second recipe row on the same bun
        recipes.settleBill(bill(1, BURGER, 2, MINI, 1, WATER, 2));
        assertEquals(5.0, bun.getQuantity());                // 2 + 1
        assertEquals(22.0, water.getQuantity());
    }

    @Test
    @DisplayName("editing a paid bill takes or returns only the difference: more, fewer, a new dish, a dish removed")
    void billEdit() {
        Bill b = bill(1, BURGER, 2);
        recipes.settleBill(b);
        assertEquals(6.0, bun.getQuantity());
        edit(b, BURGER, 5);                                  // 3 more
        recipes.settleBill(b);
        assertEquals(3.0, bun.getQuantity());
        edit(b, BURGER, 1);                                  // 4 back
        recipes.settleBill(b);
        assertEquals(7.0, bun.getQuantity());
        edit(b, BURGER, 1, WATER, 3);                        // a new dish
        recipes.settleBill(b);
        assertEquals(7.0, bun.getQuantity());
        assertEquals(21.0, water.getQuantity());
        edit(b, WATER, 3);                                   // the burger removed: its bun returns
        recipes.settleBill(b);
        assertEquals(8.0, bun.getQuantity());
        recipes.settleBill(b);                               // and nothing moves again
        assertEquals(8.0, bun.getQuantity());
        assertEquals(21.0, water.getQuantity());
    }

    @Test
    @DisplayName("cancelling puts everything back, and only once")
    void cancelRestores() {
        Bill b = bill(1, BURGER, 2, MINI, 2);
        recipes.settleBill(b);
        recipes.handleOrderCancellation(b, false, "cancelled");
        recipes.handleOrderCancellation(b, false, "cancelled");
        assertEquals(8.0, bun.getQuantity());
        assertEquals(12.0, miniBun.getQuantity());
    }

    @Test
    @DisplayName("voiding an unpaid order whose food was made takes the stock now and records the loss once as waste")
    void voidMadeFoodBeforePayment() {
        Bill b = bill(1, BURGER, 2);                          // KOT printed, never paid: nothing was taken yet
        recipes.handleOrderCancellation(b, true, "customer left");
        recipes.handleOrderCancellation(b, true, "customer left");
        assertEquals(6.0, bun.getQuantity());
        List<InventoryTransaction> waste = txns.stream().filter(t -> "WASTE".equals(t.getTransactionType())).collect(Collectors.toList());
        assertEquals(1, waste.size());
        assertEquals(2.0, waste.get(0).getQuantity());
        assertEquals(8.0, waste.get(0).getPreviousStock());
        assertEquals(6.0, waste.get(0).getNewStock());
    }

    @Test
    @DisplayName("voiding an unpaid order whose food was not made changes nothing")
    void voidUnmadeFoodBeforePayment() {
        recipes.handleOrderCancellation(bill(1, BURGER, 2), false, "wrong table");
        assertEquals(8.0, bun.getQuantity());
        assertTrue(txns.isEmpty());
    }

    @Test
    @DisplayName("an order printed under the old rule (stock taken at the kitchen ticket): not made = put back; made = stays taken, waste noted, nothing taken twice")
    void voidOldRuleOrders() {
        InventoryTransaction old = InventoryTransaction.builder().itemId(1L).transactionType("SALE_CONSUMPTION").quantity(2.0).billId(1L).billItemId(77L).recipeId(1L).build();
        txns.add(old);
        bun.setQuantity(6.0);
        recipes.handleOrderCancellation(bill(1, BURGER, 2), true, "dropped");
        assertEquals(6.0, bun.getQuantity(), "already taken at the kitchen ticket, not taken again");
        assertEquals(1, txns.stream().filter(t -> "WASTE".equals(t.getTransactionType())).count());

        txns.clear(); txns.add(InventoryTransaction.builder().itemId(1L).transactionType("SALE_CONSUMPTION").quantity(2.0).billId(2L).billItemId(78L).recipeId(1L).build());
        bun.setQuantity(6.0);
        recipes.handleOrderCancellation(bill(2, BURGER, 2), false, "wrong order");
        assertEquals(8.0, bun.getQuantity(), "put back");
    }

    @Test
    @DisplayName("units convert (g to kg); a recipe in a unit that cannot convert is skipped and never blocks the bill")
    void unitsConvertOrSkip() {
        InventoryItem cheese = item(6, "Cheese", "kg", 5);
        InventoryItem odd = item(7, "Odd", "box", 5);
        link(BURGER, cheese, "50", "g");
        link(BURGER, odd, "1", "piece");                     // box vs piece: not convertible
        recipes.settleBill(bill(1, BURGER, 4));
        assertEquals(4.8, cheese.getQuantity(), 0.0001);     // 4 x 50 g
        assertEquals(5.0, odd.getQuantity());
        assertEquals(4.0, bun.getQuantity());
    }

    @Test
    @DisplayName("stock already taken under the old rule (kitchen ticket) is not taken again at payment")
    void legacyConsumptionCounts() {
        InventoryTransaction old = InventoryTransaction.builder().itemId(1L).transactionType("SALE_CONSUMPTION").quantity(2.0).billId(1L).billItemId(77L).recipeId(1L).build();
        txns.add(old);
        bun.setQuantity(6.0);                                  // the 2 buns were already taken
        recipes.settleBill(bill(1, BURGER, 2));
        assertEquals(6.0, bun.getQuantity());
        assertEquals(1, txns.size());
    }

    @Test
    @DisplayName("a bill line without a dish id (an old bill) is matched by its name, so editing it does not return the stock")
    void lineWithoutDishId() {
        com.neram.pos.entity.MenuItem burger = new com.neram.pos.entity.MenuItem();
        burger.setId(BURGER); burger.setName("Old Burger");
        when(menuRepo.findByNameIgnoreCase("Old Burger")).thenReturn(List.of(burger));
        Bill b = bill(1, BURGER, 2);
        b.getItems().get(0).setMenuItemId(null);
        b.getItems().get(0).setItemName("Old Burger");
        recipes.settleBill(b);
        assertEquals(6.0, bun.getQuantity());
        recipes.settleBill(b);                               // a re-run (or an edit with the same items) keeps it
        assertEquals(6.0, bun.getQuantity());
        b.getItems().get(0).setItemName("Unknown dish");     // no dish matches: nothing is wrongly taken or returned for it
        when(menuRepo.findByNameIgnoreCase("Unknown dish")).thenReturn(List.of());
        recipes.settleBill(b);
        assertEquals(8.0, bun.getQuantity(), "the line no longer maps to a dish, so what was taken goes back");
    }

    @Test
    @DisplayName("nothing in stock (the purchase was forgotten): a sale takes it below zero, and the purchase, once added, puts it right")
    void soldBeforeThePurchaseWasEntered() {
        bun.setQuantity(0.0);
        Bill b = bill(1, BURGER, 2);
        recipes.settleBill(b);
        assertEquals(-2.0, bun.getQuantity(), "2 buns were used that were never added");
        recipes.settleBill(b);                               // no second take
        assertEquals(-2.0, bun.getQuantity());
        bun.setQuantity(bun.getQuantity() + 10);             // the purchase of 10 is entered later: 10 bought - 2 already used
        assertEquals(8.0, bun.getQuantity());
        edit(b, BURGER, 3);                                  // the bill is edited afterwards: one more bun
        recipes.settleBill(b);
        assertEquals(7.0, bun.getQuantity());
    }

    @Test
    @DisplayName("an inactive recipe row, a dish without a recipe, or no items take nothing")
    void nothingToTake() {
        recipeRows.forEach(r -> r.setActive(false));
        when(recipeRepo.findByMenuItemIdAndActiveTrue(anyLong())).thenReturn(List.of());
        recipes.settleBill(bill(1, BURGER, 2));
        recipes.settleBill(bill(2));
        assertEquals(8.0, bun.getQuantity());
        assertTrue(txns.isEmpty());
    }
}
