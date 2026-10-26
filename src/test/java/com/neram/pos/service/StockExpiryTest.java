package com.neram.pos.service;

import com.neram.pos.service.StockExpiry.Batch;
import com.neram.pos.service.StockExpiry.Receipt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockExpiryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private Receipt r(long id, String received, double qty, String expiry) {
        return new Receipt(id, "INV-" + id, LocalDate.parse(received), qty, expiry == null ? null : LocalDate.parse(expiry));
    }

    @Test
    @DisplayName("stock on hand is the newest deliveries: 12 left of 20 + 24 is 12 from the newer one")
    void newestDeliveriesAreOnHand() {
        List<Batch> b = StockExpiry.onHand(12, List.of(r(1, "2026-10-20", 20, "2026-10-25"), r(2, "2026-10-23", 24, "2026-11-03")), null, 2, TODAY);
        assertEquals(1, b.size());
        assertEquals(LocalDate.parse("2026-11-03"), b.get(0).expiry());
        assertEquals(12.0, b.get(0).quantity());
        assertEquals("OK", b.get(0).status());
        assertEquals(9, b.get(0).daysLeft());
    }

    @Test
    @DisplayName("when the stock reaches back into the older delivery, both show, earliest expiry first")
    void spansTwoDeliveries() {
        List<Batch> b = StockExpiry.onHand(30, List.of(r(1, "2026-10-20", 20, "2026-10-25"), r(2, "2026-10-23", 24, "2026-11-03")), null, 2, TODAY);
        assertEquals(2, b.size());
        assertEquals(6.0, b.get(0).quantity());                   // the old delivery: only 6 of its 20 are left (30 - 24)
        assertEquals("SOON", b.get(0).status());                  // expires today
        assertEquals(0, b.get(0).daysLeft());
        assertEquals(24.0, b.get(1).quantity());
    }

    @Test
    @DisplayName("status: expired before today, soon within the warning days, else ok")
    void statuses() {
        assertEquals("EXPIRED", StockExpiry.statusOf(-1, 2));
        assertEquals("SOON", StockExpiry.statusOf(0, 2));
        assertEquals("SOON", StockExpiry.statusOf(2, 2));
        assertEquals("OK", StockExpiry.statusOf(3, 2));
        assertEquals("OK", StockExpiry.statusOf(1, 0));
        assertEquals("SOON", StockExpiry.statusOf(0, 0));
    }

    @Test
    @DisplayName("no expiry on the line: the item's shelf life counts from the invoice date; without either, nothing is tracked")
    void shelfLife() {
        assertEquals(LocalDate.parse("2026-10-25"), StockExpiry.onHand(5, List.of(r(1, "2026-10-20", 10, null)), 5, 2, TODAY).get(0).expiry());
        assertTrue(StockExpiry.onHand(5, List.of(r(1, "2026-10-20", 10, null)), null, 2, TODAY).isEmpty());
        // an expiry typed on the line wins over the shelf life
        assertEquals(LocalDate.parse("2026-12-01"), StockExpiry.onHand(5, List.of(r(1, "2026-10-20", 10, "2026-12-01")), 5, 2, TODAY).get(0).expiry());
    }

    @Test
    @DisplayName("nothing on hand, or older stock without a record: no batches")
    void emptyCases() {
        assertTrue(StockExpiry.onHand(0, List.of(r(1, "2026-10-20", 10, "2026-10-26")), null, 2, TODAY).isEmpty());
        assertTrue(StockExpiry.onHand(10, List.of(), null, 2, TODAY).isEmpty());
        // 15 on hand but only 10 were ever received with a date: the other 5 are untracked, not invented
        assertEquals(10.0, StockExpiry.onHand(15, List.of(r(1, "2026-10-20", 10, "2026-10-26")), null, 2, TODAY).get(0).quantity());
    }

    @Test
    @DisplayName("summary: the earliest batch that matters, the worst status, and the quantity affected")
    void summary() {
        List<Batch> b = StockExpiry.onHand(40, List.of(r(1, "2026-10-10", 10, "2026-10-24"), r(2, "2026-10-20", 10, "2026-10-26"), r(3, "2026-10-23", 20, "2026-11-20")), null, 2, TODAY);
        StockExpiry.Summary s = StockExpiry.summarise(b);
        assertEquals("EXPIRED", s.status());
        assertEquals(LocalDate.parse("2026-10-24"), s.nextExpiry());
        assertEquals(20.0, s.affectedQuantity());                 // 10 expired + 10 soon
        assertNull(StockExpiry.summarise(List.of()).status());
        assertEquals("OK", StockExpiry.summarise(StockExpiry.onHand(5, List.of(r(1, "2026-10-23", 5, "2026-12-01")), null, 2, TODAY)).status());
    }

    @Test
    @DisplayName("bought 3 on 23 Oct (expires 26), used 2, bought 2 more on 25 Oct (expires 28): 1 old and 2 new, each with its own date, day by day")
    void olderBreadKeepsItsOwnDate() {
        List<Receipt> receipts = List.of(r(1, "2026-10-23", 3, "2026-10-26"), r(2, "2026-10-25", 2, "2026-10-28"));
        double onHand = 3;                                        // 3 - 2 used + 2 bought
        // 25 Oct (the day of the second purchase): the old one is 1 day from 26 -> soon, the new ones are fine
        List<Batch> d25 = StockExpiry.onHand(onHand, receipts, null, 2, LocalDate.parse("2026-10-25"));
        assertEquals(2, d25.size());
        assertEquals(LocalDate.parse("2026-10-26"), d25.get(0).expiry());
        assertEquals(1.0, d25.get(0).quantity());
        assertEquals("SOON", d25.get(0).status());
        assertEquals(LocalDate.parse("2026-10-28"), d25.get(1).expiry());
        assertEquals(2.0, d25.get(1).quantity());
        assertEquals("OK", d25.get(1).status());
        StockExpiry.Summary sum25 = StockExpiry.summarise(d25);
        assertEquals(LocalDate.parse("2026-10-26"), sum25.nextExpiry());      // the alert is about the 26th, not the 28th
        assertEquals(1.0, sum25.affectedQuantity());

        // 27 Oct: the 1 old one is expired, the 2 new ones are expiring soon (28 is 1 day away)
        List<Batch> d27 = StockExpiry.onHand(onHand, receipts, null, 2, LocalDate.parse("2026-10-27"));
        assertEquals("EXPIRED", d27.get(0).status());
        assertEquals(1.0, d27.get(0).quantity());
        assertEquals("SOON", d27.get(1).status());
        assertEquals(3.0, StockExpiry.summarise(d27).affectedQuantity());

        // once the old one is used (2 left), only the 28th remains
        List<Batch> after = StockExpiry.onHand(2, receipts, null, 2, LocalDate.parse("2026-10-26"));
        assertEquals(1, after.size());
        assertEquals(LocalDate.parse("2026-10-28"), after.get(0).expiry());
        assertEquals(2.0, after.get(0).quantity());
    }
}
