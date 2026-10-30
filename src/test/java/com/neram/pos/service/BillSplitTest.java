package com.neram.pos.service;

import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.service.BillSplit.Portion;
import com.neram.pos.service.BillSplit.Share;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** A split bill must add up to the original bill to the last paisa, whatever the discount, GST, parcel charge or quantities. */
class BillSplitTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static BillItem line(long id, String name, int qty, String unit) {
        BillItem i = new BillItem();
        i.setId(id);
        i.setItemName(name);
        i.setQuantity(qty);
        i.setPrice(d(unit));
        i.setSubtotal(d(unit).multiply(BigDecimal.valueOf(qty)).setScale(2));
        i.setIsTakeaway(false);
        return i;
    }

    private static Bill bill(String net, BillItem... items) {
        Bill b = new Bill();
        b.setId(7L);
        b.setBillNumber("NRM-1");
        b.setStatus(Bill.Status.BILL_PRINTED);
        b.setItems(new ArrayList<>(List.of(items)));
        BigDecimal sub = BigDecimal.ZERO;
        for (BillItem i : items) sub = sub.add(i.getSubtotal());
        b.setSubtotal(sub);
        b.setNetAmount(d(net));
        return b;
    }

    private static List<Share> shares(Object... pairs) {
        List<Share> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) out.add(new Share(((Number) pairs[i]).longValue(), (Integer) pairs[i + 1]));
        return out;
    }

    private static BigDecimal sum(List<Portion> ps, java.util.function.Function<Portion, BigDecimal> f) {
        BigDecimal t = BigDecimal.ZERO;
        for (Portion p : ps) t = t.add(f.apply(p));
        return t;
    }

    @Test
    @DisplayName("a plain bill splits into the items each guest ate, and the bills add up to the bill")
    void plainBill() {
        Bill b = bill("1000.00", line(1, "Biriyani", 2, "250.00"), line(2, "Parotta", 4, "50.00"), line(3, "Juice", 2, "150.00"));
        List<Portion> ps = BillSplit.divide(b, List.of(shares(1L, 1, 2L, 2, 3L, 1), shares(1L, 1, 2L, 2, 3L, 1)));
        assertEquals(2, ps.size());
        assertEquals(d("500.00"), ps.get(0).netAmount());
        assertEquals(d("500.00"), ps.get(1).netAmount());
        assertEquals(d("1000.00"), sum(ps, Portion::netAmount));
        assertEquals(1, ps.get(0).items().stream().filter(i -> i.billItemId() == 1L).findFirst().orElseThrow().quantity());
    }

    @Test
    @DisplayName("an uneven split keeps each bill's own items and total")
    void unevenSplit() {
        Bill b = bill("1000.00", line(1, "Biriyani", 2, "250.00"), line(2, "Parotta", 4, "50.00"), line(3, "Juice", 2, "150.00"));
        List<Portion> ps = BillSplit.divide(b, List.of(shares(1L, 2), shares(2L, 4, 3L, 2)));
        assertEquals(d("500.00"), ps.get(0).netAmount());
        assertEquals(d("500.00"), ps.get(1).netAmount());
        List<Portion> ps2 = BillSplit.divide(b, List.of(shares(1L, 1), shares(1L, 1, 2L, 4, 3L, 2)));
        assertEquals(d("250.00"), ps2.get(0).netAmount());
        assertEquals(d("750.00"), ps2.get(1).netAmount());
    }

    @Test
    @DisplayName("discount, promotion, parcel charge, GST and round-off are shared out and every part adds up exactly")
    void everythingAddsUp() {
        Bill b = bill("0", line(1, "Dosa", 3, "33.33"), line(2, "Tea", 5, "17.77"), line(3, "Cake", 1, "123.45"));
        b.setGstEnabled(true);
        b.setDiscountAmount(d("30.77"));
        b.setPromotionDiscountAmount(d("10.01"));
        b.setParcelCharge(d("20.00"));
        b.setCgstAmount(d("13.33"));
        b.setSgstAmount(d("13.33"));
        b.setTaxAmount(d("26.66"));
        b.setRoundOff(d("0.00"));
        // original: subtotal - discount + parcel + gst + round-off - promo
        BigDecimal before = b.getSubtotal().subtract(d("30.77")).add(d("20.00")).add(d("26.66"));
        BigDecimal roundOff = before.setScale(0, java.math.RoundingMode.HALF_UP).subtract(before);
        b.setRoundOff(roundOff);
        BigDecimal net = before.add(roundOff).subtract(d("10.01"));
        net = net.setScale(0, java.math.RoundingMode.HALF_UP); // whole rupees: keeps the bill "rounded"
        b.setNetAmount(net);
        b.setRoundOff(net.add(d("10.01")).subtract(before));

        List<Portion> ps = BillSplit.divide(b, List.of(shares(1L, 2, 2L, 1), shares(1L, 1, 2L, 4), shares(3L, 1)));
        assertEquals(b.getNetAmount().setScale(2), sum(ps, Portion::netAmount));
        assertEquals(b.getSubtotal(), sum(ps, Portion::subtotal));
        assertEquals(d("30.77"), sum(ps, Portion::discountAmount));
        assertEquals(d("10.01"), sum(ps, Portion::promotionDiscountAmount));
        assertEquals(d("20.00"), sum(ps, Portion::parcelCharge));
        assertEquals(d("13.33"), sum(ps, Portion::cgstAmount));
        assertEquals(d("13.33"), sum(ps, Portion::sgstAmount));
        for (Portion p : ps) {
            assertEquals(0, p.netAmount().remainder(BigDecimal.ONE).signum(), "each bill is a whole number of rupees: " + p.netAmount());
            BigDecimal shown = p.subtotal().subtract(p.discountAmount()).add(p.parcelCharge()).add(p.taxAmount()).add(p.roundOff()).subtract(p.promotionDiscountAmount()).subtract(p.couponDiscount());
            assertEquals(0, p.netAmount().compareTo(shown), "what the bill prints adds up to its total");
            assertTrue(p.roundOff().abs().compareTo(BigDecimal.ONE) < 0, "round-off stays under a rupee: " + p.roundOff());
        }
    }

    @Test
    @DisplayName("a line ordered several times can be shared between bills, paisa by paisa")
    void sharedLine() {
        BillItem shared = line(1, "Platter", 3, "33.33");
        shared.setSubtotal(d("100.00"));
        Bill b = bill("100.00", shared);
        List<Portion> ps = BillSplit.divide(b, List.of(shares(1L, 1), shares(1L, 2)));
        assertEquals(d("100.00"), sum(ps, Portion::subtotal));
        assertEquals(d("100.00"), sum(ps, Portion::netAmount));
        assertEquals(d("33.33"), ps.get(0).subtotal());
        assertEquals(d("66.67"), ps.get(1).subtotal());
    }

    @Test
    @DisplayName("a paid bill can be split too, a cancelled one cannot")
    void statuses() {
        Bill b = bill("100.00", line(1, "A", 1, "50.00"), line(2, "B", 1, "50.00"));
        b.setStatus(Bill.Status.PAID);
        assertEquals(2, BillSplit.divide(b, List.of(shares(1L, 1), shares(2L, 1))).size());
        b.setStatus(Bill.Status.CANCELLED);
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(1L, 1), shares(2L, 1))));
        b.setStatus(Bill.Status.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(1L, 1), shares(2L, 1))));
    }

    @Test
    @DisplayName("every unit must be placed once, every bill needs an item, 2 to 6 bills")
    void refusals() {
        Bill b = bill("100.00", line(1, "A", 2, "25.00"), line(2, "B", 1, "50.00"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(1L, 1), shares(2L, 1)))).getMessage().contains("not placed"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(1L, 2, 2L, 1), shares(1L, 1)))).getMessage().contains("More of A"));
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(1L, 2, 2L, 1), shares())));
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(1L, 2, 2L, 1))));
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, null));
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(99L, 1), shares(1L, 2, 2L, 1))));
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, List.of(shares(1L, -1), shares(1L, 3, 2L, 1))));
        List<List<Share>> seven = new ArrayList<>();
        for (int i = 0; i < 7; i++) seven.add(shares(1L, 1));
        assertThrows(IllegalArgumentException.class, () -> BillSplit.divide(b, seven));
    }

    @Test
    @DisplayName("the shares of any amount always add up exactly, for any weights")
    void allocateIsExact() {
        Random r = new Random(42);
        for (int round = 0; round < 500; round++) {
            int n = 2 + r.nextInt(5);
            BigDecimal[] w = new BigDecimal[n];
            for (int i = 0; i < n; i++) w[i] = BigDecimal.valueOf(r.nextInt(100000), 2);
            BigDecimal total = BigDecimal.valueOf(r.nextInt(2_000_000), 2);
            BigDecimal unit = r.nextBoolean() ? new BigDecimal("0.01") : BigDecimal.ONE;
            if (unit.compareTo(BigDecimal.ONE) == 0) total = total.setScale(0, java.math.RoundingMode.DOWN).setScale(2);
            BigDecimal[] out = BillSplit.allocate(total, w, unit);
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal x : out) { sum = sum.add(x); assertTrue(x.signum() >= 0); }
            assertEquals(0, total.compareTo(sum), "total " + total + " unit " + unit);
        }
    }

    @Test
    @DisplayName("the printable bill carries only its own items and amounts and is never the stored bill")
    void asBillIsACopy() {
        Bill b = bill("100.00", line(1, "A", 1, "50.00"), line(2, "B", 1, "50.00"));
        List<Portion> ps = BillSplit.divide(b, List.of(shares(1L, 1), shares(2L, 1)));
        Bill copy = BillSplit.asBill(b, ps.get(0));
        assertNotSame(b, copy);
        assertEquals(1, copy.getItems().size());
        assertEquals("A", copy.getItems().get(0).getItemName());
        assertEquals(d("50.00"), copy.getNetAmount());
        assertEquals(2, b.getItems().size(), "the stored bill is untouched");
        assertEquals(d("100.00"), b.getNetAmount());
    }

    @Test
    @DisplayName("quantities that wrap around a 32-bit sum to the ordered quantity are refused, not printed as 2147483647 dishes")
    void hugeQuantitiesDoNotWrapAround() {
        Bill b = bill("200.00", line(1, "A", 2, "100.00"));
        // 2147483647 + 2147483647 + 4 wraps to 2 in int arithmetic
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BillSplit.divide(b, List.of(shares(1L, Integer.MAX_VALUE), shares(1L, Integer.MAX_VALUE), shares(1L, 4)))).getMessage().contains("More of A"));
        // the same line named twice in one bill adds up too
        assertThrows(IllegalArgumentException.class,
                () -> BillSplit.divide(b, List.of(shares(1L, Integer.MAX_VALUE, 1L, Integer.MAX_VALUE, 1L, 4), shares())));
    }
}
