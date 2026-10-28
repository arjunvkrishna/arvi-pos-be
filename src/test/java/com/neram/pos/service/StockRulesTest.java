package com.neram.pos.service;

import com.neram.pos.dto.ExpiryBatchDTO;
import com.neram.pos.dto.InventoryItemDTO;
import com.neram.pos.dto.InventoryItemRequestDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.Recipe;
import com.neram.pos.entity.SupplierInvoice;
import com.neram.pos.entity.SupplierInvoiceItem;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Items issued by hand skip recipe deduction; expiry shows on the item list; issued stock carries a reason type. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockRulesTest {
    @Mock private InventoryItemRepository itemRepo;
    @Mock private InventoryTransactionRepository txnRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuditLogService auditLog;
    @Mock private SupplierInvoiceItemRepository invoiceItemRepo;
    @Mock private SupplierInvoiceRepository invoiceRepo;
    @Mock private RecipeRepository recipeRepo;
    @Mock private MenuItemRepository menuRepo;

    private InventoryService inventory;
    private InventoryItem milk;

    @BeforeEach
    void setUp() {
        inventory = new InventoryService(itemRepo, txnRepo, userRepo, auditLog, invoiceItemRepo, invoiceRepo);
        milk = new InventoryItem();
        milk.setId(5L); milk.setName("Milk"); milk.setUnit("packet"); milk.setQuantity(10.0); milk.setMinQuantity(2.0);
        when(itemRepo.findById(5L)).thenReturn(Optional.of(milk));
        when(itemRepo.save(any(InventoryItem.class))).thenAnswer(i -> i.getArgument(0));
        when(itemRepo.findByActiveTrue()).thenReturn(List.of(milk));
    }

    @Test
    @DisplayName("an item issued by hand is not reduced when a dish with a recipe sells; one that deducts automatically still is")
    void autoDeductSwitch() {
        RecipeService recipes = new RecipeService(recipeRepo, menuRepo, itemRepo, txnRepo, auditLog, org.mockito.Mockito.mock(com.neram.pos.repository.ModifierRepository.class));
        Recipe r = new Recipe(); r.setId(1L); r.setMenuItemId(7L); r.setInventoryItemId(5L); r.setQuantityPerServing(new BigDecimal("1")); r.setUnit("packet"); r.setActive(true);
        when(recipeRepo.findByMenuItemIdAndActiveTrue(7L)).thenReturn(List.of(r));
        when(txnRepo.findByBillId(1L)).thenReturn(List.of());
        Bill bill = new Bill(); bill.setId(1L); bill.setBillNumber("NRM-1");
        BillItem line = new BillItem(); line.setId(11L); line.setMenuItemId(7L); line.setItemName("Tea"); line.setQuantity(2);
        bill.setItems(List.of(line));

        milk.setAutoDeduct(false);
        recipes.settleBill(bill);
        assertEquals(10.0, milk.getQuantity(), "issued by hand: stock untouched");
        verify(txnRepo, never()).save(any(InventoryTransaction.class));

        milk.setAutoDeduct(true);
        recipes.settleBill(bill);
        assertEquals(8.0, milk.getQuantity(), "automatic: 2 teas x 1 packet");
    }

    @Test
    @DisplayName("the item list says when stock expires: a delivery with an expiry date, and one covered by the item's shelf life")
    void expiryOnTheList() {
        milk.setQuantity(12.0);
        SupplierInvoice inv = new SupplierInvoice(); inv.setId(1L); inv.setInvoiceNumber("INV-1"); inv.setInvoiceDate(LocalDate.now().minusDays(3));
        SupplierInvoiceItem line = SupplierInvoiceItem.builder().invoiceId(1L).inventoryItemId(5L).quantity(12.0).expiryDate(LocalDate.now().plusDays(1)).build();
        when(invoiceItemRepo.findByInventoryItemIdIn(any())).thenReturn(List.of(line));
        when(invoiceRepo.findAllById(any())).thenReturn(List.of(inv));

        InventoryItemDTO dto = inventory.getAllItems().get(0);
        assertEquals("SOON", dto.getExpiryStatus());
        assertEquals(12.0, dto.getExpiringQuantity());
        assertEquals(LocalDate.now().plusDays(1), dto.getNextExpiryDate());
        assertTrue(dto.getAutoDeduct());

        List<ExpiryBatchDTO> batches = inventory.getExpiryBatches(false);
        assertEquals(1, batches.size());
        assertEquals("INV-1", batches.get(0).getInvoiceNumber());

        // shelf life instead of a typed date: 3 days old invoice, 2 days of shelf life => already expired
        line.setExpiryDate(null); milk.setShelfLifeDays(2);
        assertEquals("EXPIRED", inventory.getAllItems().get(0).getExpiryStatus());
        // nothing tracked => no status
        milk.setShelfLifeDays(null);
        assertNull(inventory.getAllItems().get(0).getExpiryStatus());
        assertTrue(inventory.getExpiryBatches(true).isEmpty());
    }

    @Test
    @DisplayName("issued stock keeps its reason type (default USAGE); an unknown one is refused; expired stock gets its own note")
    void reasonTypes() {
        inventory.decreaseStock(5L, 1.0, null, "manager", false, null);
        inventory.decreaseStock(5L, 2.0, null, "manager", false, "expired");
        assertThrows(IllegalArgumentException.class, () -> inventory.decreaseStock(5L, 1.0, "x", "manager", false, "THEFT"));
        ArgumentCaptor<InventoryTransaction> cap = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(txnRepo, times(2)).save(cap.capture());
        assertEquals("USAGE", cap.getAllValues().get(0).getReasonType());
        assertEquals("EXPIRED", cap.getAllValues().get(1).getReasonType());
        assertTrue(cap.getAllValues().get(1).getNotes().startsWith("Expired stock written off"));
        assertEquals(7.0, milk.getQuantity());
    }

    @Test
    @DisplayName("the switch, shelf life and warning days are saved when sent and left alone when not")
    void saveRules() {
        InventoryItemRequestDTO dto = new InventoryItemRequestDTO();
        dto.setName("Milk"); dto.setAutoDeduct(false); dto.setShelfLifeDays(5); dto.setExpiryWarnDays(3);
        inventory.updateItem(5L, dto);
        assertFalse(milk.getAutoDeduct()); assertEquals(5, milk.getShelfLifeDays()); assertEquals(3, milk.getExpiryWarnDays());
        InventoryItemRequestDTO other = new InventoryItemRequestDTO(); other.setName("Milk");
        inventory.updateItem(5L, other);
        assertFalse(milk.getAutoDeduct()); assertEquals(5, milk.getShelfLifeDays());
        InventoryItemRequestDTO bad = new InventoryItemRequestDTO(); bad.setName("Milk"); bad.setShelfLifeDays(-1);
        assertThrows(IllegalArgumentException.class, () -> inventory.updateItem(5L, bad));
        InventoryItemRequestDTO clear = new InventoryItemRequestDTO(); clear.setName("Milk"); clear.setShelfLifeDays(0);
        inventory.updateItem(5L, clear);
        assertNull(milk.getShelfLifeDays(), "0 clears the shelf life");
    }

    @Test
    @DisplayName("issuing more than is in stock is refused unless allowed; when allowed the stock goes negative and the movement says the purchase was not entered")
    void issueBelowZero() {
        milk.setQuantity(2.0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> inventory.decreaseStock(5L, 5.0, "x", "manager", false, null, false));
        assertTrue(e.getMessage().contains("record it anyway"));
        assertEquals(2.0, milk.getQuantity());
        inventory.decreaseStock(5L, 5.0, "prep", "manager", false, null, true);
        assertEquals(-3.0, milk.getQuantity());
        ArgumentCaptor<InventoryTransaction> cap = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(txnRepo).save(cap.capture());
        assertEquals(-3.0, cap.getValue().getNewStock());
        assertTrue(cap.getValue().getNotes().contains("purchase not entered yet"));
        // already negative: any further issue needs the same permission
        assertThrows(IllegalArgumentException.class, () -> inventory.decreaseStock(5L, 1.0, "x", "manager", false, null, false));
        inventory.decreaseStock(5L, 1.0, "x", "manager", false, null, true);
        assertEquals(-4.0, milk.getQuantity());
    }
}
