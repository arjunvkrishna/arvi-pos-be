package com.neram.pos.service;

import com.neram.pos.dto.StockOpsDTO;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.StockCount;
import com.neram.pos.entity.StockCountLine;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A count freezes the book quantity; applying books counted minus that frozen figure on top of the current stock. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockCountServiceTest {
    @Mock private StockCountRepository counts;
    @Mock private StockCountLineRepository lines;
    @Mock private InventoryItemRepository items;
    @Mock private InventoryTransactionRepository txns;
    @Mock private UserRepository users;
    @Mock private AuditLogService audit;

    private StockCountService svc;
    private InventoryItem cola, bun, rice;
    private final List<StockCountLine> store = new ArrayList<>();
    private StockCount count;

    private InventoryItem item(long id, String name, double qty, String cost) {
        InventoryItem i = new InventoryItem();
        i.setId(id); i.setName(name); i.setUnit("pcs"); i.setQuantity(qty); i.setCostPerUnit(new BigDecimal(cost)); i.setItemCategory("Stock");
        when(items.findById(id)).thenReturn(Optional.of(i));
        return i;
    }

    @BeforeEach
    void setUp() {
        svc = new StockCountService(counts, lines, items, txns, users, audit);
        cola = item(1, "Cola", 10, "20.00"); bun = item(2, "Bun", 24, "5.00"); rice = item(3, "Rice", 8, "60.00");
        when(items.findByActiveTrue()).thenReturn(List.of(cola, bun, rice));
        when(counts.save(any(StockCount.class))).thenAnswer(a -> { StockCount c = a.getArgument(0); if (c.getId() == null) c.setId(7L); count = c; return c; });
        when(counts.findById(7L)).thenAnswer(a -> Optional.ofNullable(count));
        when(lines.saveAll(any())).thenAnswer(a -> { Iterable<StockCountLine> in = a.getArgument(0); long n = store.size() + 1; for (StockCountLine l : in) { if (l.getId() == null) l.setId(n++); if (!store.contains(l)) store.add(l); } return new ArrayList<>(store); });
        when(lines.findByCountIdOrderByItemNameAsc(7L)).thenAnswer(a -> new ArrayList<>(store));
    }

    private StockOpsDTO.Count started() { return svc.start(null, "month end", "manager"); }
    private Long lineOf(String name) { return store.stream().filter(l -> l.getItemName().equals(name)).findFirst().orElseThrow().getId(); }
    private void enter(String name, Double v) { svc.saveLines(7L, List.of(new StockOpsDTO.LineEntry(lineOf(name), v))); }

    @Test
    void startFreezesBookQuantitiesForEveryActiveItem() {
        StockOpsDTO.Count c = started();
        assertEquals("DRAFT", c.getStatus());
        assertEquals(3, c.getItemCount());
        assertEquals(0, c.getCountedCount());
        assertEquals(10.0, c.getLines().stream().filter(l -> l.getName().equals("Cola")).findFirst().orElseThrow().getBookQty());
    }

    @Test
    void onlyOneOpenCountAtATime() {
        when(counts.existsByStatus("DRAFT")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, this::started);
    }

    @Test
    void differencesAndTheirMoneyValueAreShownBeforeApplying() {
        started();
        enter("Cola", 8.0);   // 2 missing at 20 = -40
        enter("Bun", 26.0);   // 2 extra at 5   = +10
        enter("Rice", 8.0);   // same
        StockOpsDTO.Count c = svc.get(7L);
        assertEquals(3, c.getCountedCount());
        assertEquals(2, c.getDifferenceCount());
        assertEquals(-30.0, c.getVarianceValue());
        assertEquals(-2.0, c.getLines().stream().filter(l -> l.getName().equals("Cola")).findFirst().orElseThrow().getVariance());
    }

    @Test
    void applyBooksOnlyDifferencesAsAdjustmentsAndIgnoresUncountedItems() {
        started();
        enter("Cola", 8.0); enter("Bun", 26.0);       // Rice not counted
        svc.apply(7L, "manager");
        assertEquals(8.0, cola.getQuantity());
        assertEquals(26.0, bun.getQuantity());
        assertEquals(8.0, rice.getQuantity());         // untouched
        ArgumentCaptor<InventoryTransaction> t = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(txns, times(2)).save(t.capture());
        assertTrue(t.getAllValues().stream().allMatch(x -> "ADJUSTMENT".equals(x.getTransactionType()) && "COUNT".equals(x.getReasonType())));
        assertEquals("APPLIED", count.getStatus());
    }

    @Test
    void salesDuringTheCountAreNotLost() {
        started();                                     // book 10
        cola.setQuantity(7.0);                         // 3 sold while counting
        enter("Cola", 8.0);                            // shelf shows 8 -> 2 missing relative to the frozen book
        svc.apply(7L, "manager");
        assertEquals(5.0, cola.getQuantity());         // 7 - 2, not forced to 8
    }

    @Test
    void anAppliedOrCancelledCountCannotBeChanged() {
        started(); enter("Cola", 9.0);
        svc.apply(7L, "manager");
        assertThrows(IllegalArgumentException.class, () -> svc.apply(7L, "manager"));
        assertThrows(IllegalArgumentException.class, () -> svc.saveLines(7L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> svc.cancel(7L, "manager"));
        assertEquals(9.0, cola.getQuantity());
    }

    @Test
    void cancelLeavesStockAlone() {
        started(); enter("Cola", 1.0);
        svc.cancel(7L, "manager");
        assertEquals("CANCELLED", count.getStatus());
        assertEquals(10.0, cola.getQuantity());
        verify(txns, never()).save(any());
    }

    @Test
    void negativeOrForeignEntriesAreRefused() {
        started();
        assertThrows(IllegalArgumentException.class, () -> enter("Cola", -1.0));
        assertThrows(IllegalArgumentException.class, () -> svc.saveLines(7L, List.of(new StockOpsDTO.LineEntry(999L, 1.0))));
    }

    @Test
    void clearingAnEntryMeansNotCounted() {
        started(); enter("Cola", 4.0); enter("Cola", null);
        svc.apply(7L, "manager");
        assertEquals(10.0, cola.getQuantity());
    }
}
