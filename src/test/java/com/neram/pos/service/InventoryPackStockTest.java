package com.neram.pos.service;

import com.neram.pos.dto.InventoryItemDTO;
import com.neram.pos.dto.InventoryItemRequestDTO;
import com.neram.pos.dto.SupplierInvoiceItemDTO;
import com.neram.pos.dto.SupplierInvoiceRequestDTO;
import com.neram.pos.entity.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stock is held in ONE stock unit (bottle, packet, piece...). A pack (case, carton) is a
 * conversion into that unit, so issuing 5 bottles from 10 cases of 12 leaves 115, and
 * a bread pack can be issued as 1.5 packs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryPackStockTest {

    @Mock private InventoryItemRepository itemRepo;
    @Mock private InventoryTransactionRepository txnRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuditLogService auditLog;
    @Mock private SupplierRepository supplierRepo;
    @Mock private SupplierInvoiceRepository invoiceRepo;
    @Mock private SupplierPaymentRepository paymentRepo;
    @Mock private SupplierInvoiceItemRepository invoiceItemRepo;

    private InventoryService inventory;
    private SupplierService suppliers;

    private final List<InventoryTransaction> txns = new ArrayList<>();
    private final List<SupplierInvoiceItem> savedLines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inventory = new InventoryService(itemRepo, txnRepo, userRepo, auditLog, invoiceItemRepo, invoiceRepo);
        suppliers = new SupplierService(supplierRepo, invoiceRepo, paymentRepo, invoiceItemRepo,
                itemRepo, txnRepo, userRepo, auditLog);

        when(itemRepo.save(any(InventoryItem.class))).thenAnswer(i -> {
            InventoryItem it = i.getArgument(0);
            if (it.getId() == null) it.setId(900L);
            return it;
        });
        when(txnRepo.save(any(InventoryTransaction.class))).thenAnswer(i -> {
            txns.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(invoiceItemRepo.save(any(SupplierInvoiceItem.class))).thenAnswer(i -> {
            savedLines.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(invoiceRepo.save(any(SupplierInvoice.class))).thenAnswer(i -> {
            SupplierInvoice inv = i.getArgument(0);
            if (inv.getId() == null) inv.setId(77L);
            return inv;
        });
        Supplier s = new Supplier();
        s.setId(5L);
        s.setName("Kareems Agencies");
        s.setPaymentTerms(30);
        when(supplierRepo.findById(5L)).thenReturn(Optional.of(s));
    }

    private InventoryItem item(long id, String name, String unit, double qty, Double packSize, String packName) {
        InventoryItem it = new InventoryItem();
        it.setId(id);
        it.setName(name);
        it.setUnit(unit);
        it.setQuantity(qty);
        it.setMinQuantity(0.0);
        it.setUnitVolume(1.0);
        it.setVolumeUnit(unit);
        it.setPackSize(packSize);
        it.setPackName(packName);
        it.setCostPerUnit(BigDecimal.ZERO);
        when(itemRepo.findById(id)).thenReturn(Optional.of(it));
        return it;
    }

    private SupplierInvoiceItemDTO line(Long itemId, double qty, String price, String mode, Double packSize, String packName) {
        SupplierInvoiceItemDTO d = new SupplierInvoiceItemDTO();
        d.setInventoryItemId(itemId);
        d.setQuantity(qty);
        d.setUnitPrice(new BigDecimal(price));
        d.setPurchaseMode(mode);
        d.setPackSize(packSize);
        d.setPackName(packName);
        return d;
    }

    private SupplierInvoiceRequestDTO invoice(SupplierInvoiceItemDTO... lines) {
        SupplierInvoiceRequestDTO r = new SupplierInvoiceRequestDTO();
        r.setSupplierId(5L);
        r.setInvoiceNumber("INV-1");
        r.setItems(List.of(lines));
        return r;
    }

    // ---- issuing stock -------------------------------------------------------------

    @Test
    @DisplayName("Water: 10 cases x 12 = 120 bottles; issuing 5 bottles leaves 115 (not 5 cases)")
    void issueBottlesFromCases() {
        InventoryItem water = item(1, "Water 1L", "bottle", 120, 12.0, "case");
        InventoryItemDTO out = inventory.decreaseStock(1L, 5.0, "bar", "staff");
        assertEquals(115.0, out.getQuantity());
        assertEquals(115.0, water.getQuantity());
        assertEquals(5.0, txns.get(0).getQuantity());
        assertEquals(120.0, txns.get(0).getPreviousStock());
        assertEquals(115.0, txns.get(0).getNewStock());
    }

    @Test
    @DisplayName("Water: issuing 1 whole case takes 12 bottles and says so in the history")
    void issueWholeCase() {
        InventoryItem water = item(1, "Water 1L", "bottle", 120, 12.0, "case");
        inventory.decreaseStock(1L, 1.0, "event", "staff", true);
        assertEquals(108.0, water.getQuantity());
        assertEquals(12.0, txns.get(0).getQuantity());
        assertTrue(txns.get(0).getNotes().contains("1 case = 12 bottle"), txns.get(0).getNotes());
    }

    @Test
    @DisplayName("Bread: 40 pieces in packs of 4; 6 pieces taken leaves 34, and 1.5 packs is the same 6")
    void breadFractionalPacks() {
        InventoryItem bread = item(2, "Bread", "piece", 40, 4.0, "pack");
        inventory.decreaseStock(2L, 6.0, null, "staff");
        assertEquals(34.0, bread.getQuantity());
        inventory.decreaseStock(2L, 1.5, null, "staff", true);
        assertEquals(28.0, bread.getQuantity());
    }

    @Test
    @DisplayName("Curry powder: counted in packets with no pack; 10 - 1 = 9")
    void simplePacketItem() {
        InventoryItem curry = item(3, "Curry powder 100g", "packet", 10, null, null);
        inventory.decreaseStock(3L, 1.0, null, "staff");
        assertEquals(9.0, curry.getQuantity());
    }

    @Test
    @DisplayName("Issuing in packs for an item that has no pack is refused")
    void packsForPacklessItem() {
        item(3, "Curry powder 100g", "packet", 10, null, null);
        assertThrows(IllegalArgumentException.class, () -> inventory.decreaseStock(3L, 1.0, null, "staff", true));
    }

    @Test
    @DisplayName("Cannot issue more than is in stock, in either unit")
    void cannotOverIssue() {
        item(1, "Water 1L", "bottle", 20, 12.0, "case");
        assertThrows(IllegalArgumentException.class, () -> inventory.decreaseStock(1L, 21.0, null, "staff"));
        assertThrows(IllegalArgumentException.class, () -> inventory.decreaseStock(1L, 2.0, null, "staff", true));
    }

    @Test
    @DisplayName("Repeated small issues never leave floating-point residue")
    void noFloatResidue() {
        InventoryItem flour = item(4, "Flour", "kg", 1.0, null, null);
        for (int i = 0; i < 3; i++) inventory.decreaseStock(4L, 0.1, null, "staff");
        assertEquals(0.7, flour.getQuantity());
    }

    // ---- creating / editing items -------------------------------------------------

    @Test
    @DisplayName("Creating an item with a pack stores the pack; pack size 1 or blank clears it")
    void createAndClearPack() {
        InventoryItemRequestDTO dto = new InventoryItemRequestDTO();
        dto.setName("Water 1L");
        dto.setUnit("bottle");
        dto.setQuantity(120.0);
        dto.setPackName("case");
        dto.setPackSize(12.0);
        InventoryItemDTO created = inventory.createItem(dto);
        assertEquals("case", created.getPackName());
        assertEquals(12.0, created.getPackSize());
        assertEquals(120.0, created.getQuantity());

        InventoryItem saved = item(900, "Water 1L", "bottle", 120, 12.0, "case");
        InventoryItemRequestDTO edit = new InventoryItemRequestDTO();
        edit.setName("Water 1L");
        edit.setPackSize(1.0);
        InventoryItemDTO cleared = inventory.updateItem(900L, edit);
        assertNull(cleared.getPackSize());
        assertNull(saved.getPackName());
    }

    // ---- buying -------------------------------------------------------------------------

    @Test
    @DisplayName("Buying 10 cases of 12 at 120/case adds 120 bottles at 10 each; the invoice total is 1200")
    void buyByCase() {
        InventoryItem water = item(1, "Water 1L", "bottle", 0, 12.0, "case");
        suppliers.createInvoice(invoice(line(1L, 10, "120.00", "PACK", null, null)), null, "manager");

        assertEquals(120.0, water.getQuantity());
        assertEquals(0, new BigDecimal("10.00").compareTo(water.getCostPerUnit()), "cost per bottle");

        SupplierInvoiceItem saved = savedLines.get(0);
        assertEquals(10.0, saved.getQuantity());
        assertEquals(120.0, saved.getStockQuantity());
        assertEquals("PACK", saved.getPurchaseMode());
        assertEquals(0, new BigDecimal("1200.00").compareTo(saved.getTotalPrice()), "line total");
        assertEquals(120.0, txns.get(0).getQuantity());
        assertEquals(0, new BigDecimal("10.00").compareTo(txns.get(0).getUnitPrice()));
    }

    @Test
    @DisplayName("A pack defined on the purchase line is remembered on the item")
    void packDefinedOnPurchase() {
        InventoryItem water = item(1, "Water 1L", "bottle", 0, null, null);
        suppliers.createInvoice(invoice(line(1L, 10, "120.00", "PACK", 12.0, "case")), null, "manager");
        assertEquals(12.0, water.getPackSize());
        assertEquals("case", water.getPackName());
        assertEquals(120.0, water.getQuantity());
    }

    @Test
    @DisplayName("Buying by pack when the item has no pack size is refused with a clear message")
    void buyByPackWithoutSize() {
        item(1, "Water 1L", "bottle", 0, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> suppliers.createInvoice(invoice(line(1L, 10, "120.00", "PACK", null, null)), null, "manager"));
        assertTrue(ex.getMessage().contains("how many bottle"), ex.getMessage());
    }

    @Test
    @DisplayName("Buying by bottle keeps working exactly as before (1:1)")
    void buyByUnit() {
        InventoryItem water = item(1, "Water 1L", "bottle", 5, 12.0, "case");
        suppliers.createInvoice(invoice(line(1L, 24, "10.00", "UNIT", null, null)), null, "manager");
        assertEquals(29.0, water.getQuantity());
        assertEquals(24.0, savedLines.get(0).getStockQuantity());
        assertEquals("UNIT", savedLines.get(0).getPurchaseMode());
    }

    @Test
    @DisplayName("Old requests without any pack fields behave as before")
    void legacyRequest() {
        InventoryItem curry = item(3, "Curry powder", "packet", 2, null, null);
        suppliers.createInvoice(invoice(line(3L, 10, "30.00", null, null, null)), null, "manager");
        assertEquals(12.0, curry.getQuantity());
    }

    @Test
    @DisplayName("Weighted average cost mixes a case purchase with existing bottles")
    void weightedAverageWithCases() {
        InventoryItem water = item(1, "Water 1L", "bottle", 120, 12.0, "case");
        water.setCostPerUnit(new BigDecimal("10.00"));
        // 12 more bottles bought as 1 case at 144 => 12/bottle ; (120*10 + 144) / 132 = 10.18
        suppliers.createInvoice(invoice(line(1L, 1, "144.00", "PACK", null, null)), null, "manager");
        assertEquals(132.0, water.getQuantity());
        assertEquals(0, new BigDecimal("10.18").compareTo(water.getCostPerUnit()), water.getCostPerUnit().toPlainString());
    }

    @Test
    @DisplayName("Deleting a case purchase takes the bottles back out, not the case count")
    void deleteReversesStockUnits() {
        InventoryItem water = item(1, "Water 1L", "bottle", 120, 12.0, "case");
        SupplierInvoice inv = new SupplierInvoice();
        inv.setId(77L);
        inv.setInvoiceNumber("INV-1");
        inv.setSupplierId(5L);
        inv.setPaidAmount(BigDecimal.ZERO);
        when(invoiceRepo.findById(77L)).thenReturn(Optional.of(inv));
        SupplierInvoiceItem l = SupplierInvoiceItem.builder().invoiceId(77L).inventoryItemId(1L).productName("Water 1L")
                .quantity(10.0).stockQuantity(120.0).unit("bottle").unitPrice(new BigDecimal("120")).totalPrice(new BigDecimal("1200")).build();
        when(invoiceItemRepo.findByInvoiceId(77L)).thenReturn(List.of(l));

        suppliers.deleteInvoice(77L, "manager");
        assertEquals(0.0, water.getQuantity());
    }

    @Test
    @DisplayName("Older purchase lines with no stock_quantity are reversed by their quantity")
    void deleteLegacyLine() {
        InventoryItem curry = item(3, "Curry powder", "packet", 10, null, null);
        SupplierInvoice inv = new SupplierInvoice();
        inv.setId(78L);
        inv.setInvoiceNumber("INV-2");
        inv.setSupplierId(5L);
        inv.setPaidAmount(BigDecimal.ZERO);
        when(invoiceRepo.findById(78L)).thenReturn(Optional.of(inv));
        SupplierInvoiceItem l = SupplierInvoiceItem.builder().invoiceId(78L).inventoryItemId(3L).productName("Curry powder")
                .quantity(4.0).unit("packet").unitPrice(new BigDecimal("30")).totalPrice(new BigDecimal("120")).build();
        when(invoiceItemRepo.findByInvoiceId(78L)).thenReturn(List.of(l));

        suppliers.deleteInvoice(78L, "manager");
        assertEquals(6.0, curry.getQuantity());
    }

    // ---- editing an invoice after some of its stock was already used --------------------------

    private SupplierInvoice existingInvoice(long id, long itemId, double caseQty, double stockQty) {
        SupplierInvoice inv = new SupplierInvoice();
        inv.setId(id);
        inv.setInvoiceNumber("INV-" + id);
        inv.setSupplierId(5L);
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setTotalAmount(BigDecimal.ZERO);
        inv.setOutstandingAmount(BigDecimal.ZERO);
        inv.setStatus(SupplierInvoice.InvoiceStatus.UNPAID);
        when(invoiceRepo.findById(id)).thenReturn(Optional.of(inv));
        SupplierInvoiceItem l = SupplierInvoiceItem.builder().invoiceId(id).inventoryItemId(itemId).productName("Water 1L")
                .quantity(caseQty).stockQuantity(stockQty).purchaseMode("PACK").packSize(12.0).packName("case")
                .unit("bottle").unitPrice(new BigDecimal("180")).totalPrice(new BigDecimal("1800")).build();
        when(invoiceItemRepo.findByInvoiceId(id)).thenReturn(List.of(l));
        return inv;
    }

    @Test
    @DisplayName("Editing a purchase upward after bottles were issued keeps the issued bottles issued (110 -> 230, not 240)")
    void editUpAfterIssue() {
        // bought 10 cases (120), 10 bottles issued -> 110 left
        InventoryItem water = item(1, "Water 1L", "bottle", 110, 12.0, "case");
        existingInvoice(77L, 1L, 10, 120);
        suppliers.updateInvoice(77L, invoice(line(1L, 20, "180.00", "PACK", null, null)), "manager");
        assertEquals(230.0, water.getQuantity(), "240 bought - 10 issued");
        InventoryTransaction t = txns.get(0);
        assertEquals(110.0, t.getPreviousStock(), "history shows the real stock before the edit");
        assertEquals(230.0, t.getNewStock());
        assertEquals(120.0, t.getQuantity(), "net change is +120 bottles");
        assertEquals("STOCK_IN", t.getTransactionType());
    }

    @Test
    @DisplayName("Editing a purchase down is refused when the bottles are already used")
    void editDownBelowIssued() {
        item(1, "Water 1L", "bottle", 30, 12.0, "case");   // 120 bought, 90 already used
        existingInvoice(77L, 1L, 10, 120);
        // down to 5 cases (60) would need 60 bottles to exist but only 30 + (60-120) < 0
        assertThrows(RuntimeException.class,
                () -> suppliers.updateInvoice(77L, invoice(line(1L, 5, "180.00", "PACK", null, null)), "manager"));
    }

    @Test
    @DisplayName("Editing only the price (same cases) leaves stock exactly as it is")
    void editPriceOnlyKeepsStock() {
        InventoryItem water = item(1, "Water 1L", "bottle", 110, 12.0, "case");
        existingInvoice(77L, 1L, 10, 120);
        suppliers.updateInvoice(77L, invoice(line(1L, 10, "190.00", "PACK", null, null)), "manager");
        assertEquals(110.0, water.getQuantity());
        assertTrue(txns.isEmpty(), "no stock movement is recorded when nothing moved");
    }
}
