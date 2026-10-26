package com.neram.pos.service;

import com.neram.pos.dto.InsightsDTO.*;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.MenuItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InsightsMathTest {
    private static Bill bill(String when, String net, Bill.OrderType type, Bill.PaymentMode mode, Long staff, String table, Object... dishQtyPrice) {
        Bill b = new Bill();
        b.setCreatedAt(LocalDateTime.parse(when)); b.setNetAmount(new BigDecimal(net)); b.setSubtotal(new BigDecimal(net)); b.setOrderType(type); b.setPaymentMode(mode); b.setCreatedBy(staff); b.setTableNumber(table);
        List<BillItem> items = new ArrayList<>();
        for (int k = 0; k < dishQtyPrice.length; k += 3) {
            BillItem i = new BillItem(); i.setMenuItemId((Long) dishQtyPrice[k]); i.setItemName("Dish" + dishQtyPrice[k]); i.setQuantity((Integer) dishQtyPrice[k + 1]);
            i.setSubtotal(new BigDecimal(String.valueOf(dishQtyPrice[k + 2]))); items.add(i);
        }
        b.setItems(items);
        return b;
    }
    private static final Bill.OrderType T = Bill.OrderType.TABLE;

    @Test
    @DisplayName("change vs the previous period: percent, and no percent when there was nothing before")
    void metrics() {
        assertEquals(25.0, InsightsMath.metric(125, 100).getChangePercent());
        assertEquals(-50.0, InsightsMath.metric(50, 100).getChangePercent());
        assertNull(InsightsMath.metric(80, 0).getChangePercent());
        assertEquals(80.0, InsightsMath.metric(80, 0).getCurrent());
    }

    @Test
    @DisplayName("busy hours and weekdays: sales and orders land in the right hour and day; salesPerDay divides by the weekdays in the period")
    void hoursAndWeekdays() {
        List<Bill> bills = List.of(
                bill("2026-10-19T20:15:00", "300", T, Bill.PaymentMode.CASH, 1L, "T-1"),      // Monday 20:xx
                bill("2026-10-19T20:45:00", "200", T, Bill.PaymentMode.UPI, 1L, "T-2"),
                bill("2026-10-26T09:05:00", "100", T, Bill.PaymentMode.CASH, 2L, "T-1"));     // the next Monday 09:xx
        List<HourPoint> h = InsightsMath.byHour(bills);
        assertEquals(24, h.size());
        assertEquals(500.0, h.get(20).getSales()); assertEquals(2, h.get(20).getOrders());
        assertEquals(100.0, h.get(9).getSales());
        assertEquals(0.0, h.get(3).getSales());
        List<WeekdayPoint> w = InsightsMath.byWeekday(bills, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-11-01"));   // two full weeks
        assertEquals("Mon", w.get(0).getLabel());
        assertEquals(600.0, w.get(0).getSales()); assertEquals(3, w.get(0).getOrders()); assertEquals(2, w.get(0).getDays());
        assertEquals(300.0, w.get(0).getSalesPerDay()); assertEquals(200.0, w.get(0).getAvgOrder());
        assertEquals(0.0, w.get(1).getSales());
        List<List<Double>> map = InsightsMath.heatmap(bills);
        assertEquals(7, map.size()); assertEquals(24, map.get(0).size());
        assertEquals(500.0, map.get(0).get(20)); assertEquals(100.0, map.get(0).get(9));
    }

    @Test
    @DisplayName("splits: order type, payment mode (with shares), categories, staff, tables (dine-in only)")
    void splits() {
        List<Bill> bills = List.of(
                bill("2026-10-19T12:00:00", "300", T, Bill.PaymentMode.CASH, 1L, "T-1", 10L, 2, "200", 11L, 1, "100"),
                bill("2026-10-19T13:00:00", "100", Bill.OrderType.TAKEAWAY, Bill.PaymentMode.UPI, 2L, null, 10L, 1, "100"),
                bill("2026-10-19T14:00:00", "100", Bill.OrderType.OUTDOOR, Bill.PaymentMode.UPI, 2L, null, 11L, 1, "100"));
        List<Slice> type = InsightsMath.byOrderType(bills);
        assertEquals("Dine-in", type.get(0).getLabel()); assertEquals(60.0, type.get(0).getShare());
        assertEquals(Set.of("Dine-in", "Takeaway", "Outdoor"), Set.copyOf(type.stream().map(Slice::getLabel).toList()));
        List<Slice> pay = InsightsMath.byPaymentMode(bills);
        assertEquals("CASH", pay.get(0).getLabel()); assertEquals(300.0, pay.get(0).getAmount());
        assertEquals(40.0, pay.get(1).getShare());
        List<Slice> cat = InsightsMath.byCategory(bills, Map.of(10L, "Burgers", 11L, "Drinks"));
        assertEquals("Burgers", cat.get(0).getLabel()); assertEquals(300.0, cat.get(0).getAmount()); assertEquals(3, cat.get(0).getQuantity());
        assertEquals("Drinks", cat.get(1).getLabel());
        assertEquals("Other", InsightsMath.byCategory(List.of(bill("2026-10-19T12:00:00", "50", T, Bill.PaymentMode.CASH, 1L, "T-1", 99L, 1, "50")), Map.of()).get(0).getLabel());
        List<StaffRow> staff = InsightsMath.byStaff(bills, Map.of(1L, "asha", 2L, "ravi"));
        assertEquals("asha", staff.get(0).getStaff()); assertEquals(300.0, staff.get(0).getSales()); assertEquals(60.0, staff.get(0).getShare());
        assertEquals("ravi", staff.get(1).getStaff()); assertEquals(2, staff.get(1).getOrders()); assertEquals(100.0, staff.get(1).getAvgOrder());
        List<TableRow> tables = InsightsMath.byTable(bills, 8);
        assertEquals(1, tables.size()); assertEquals("T-1", tables.get(0).getTable());
    }

    @Test
    @DisplayName("discounts and promotions as a share of gross, and the GST collected")
    void discountsAndTax() {
        Bill a = bill("2026-10-19T12:00:00", "190", T, Bill.PaymentMode.CASH, 1L, "T-1");
        a.setSubtotal(new BigDecimal("200")); a.setDiscountAmount(new BigDecimal("10")); a.setTaxAmount(new BigDecimal("9.5")); a.setCgstAmount(new BigDecimal("4.75")); a.setSgstAmount(new BigDecimal("4.75"));
        Bill b = bill("2026-10-19T13:00:00", "100", T, Bill.PaymentMode.CASH, 1L, "T-2");
        b.setSubtotal(new BigDecimal("100")); b.setPromotionDiscountAmount(new BigDecimal("5"));
        Discounts d = InsightsMath.discounts(List.of(a, b));
        assertEquals(10.0, d.getDiscountAmount()); assertEquals(5.0, d.getPromotionAmount()); assertEquals(15.0, d.getTotal());
        assertEquals(2, d.getBillsWithDiscount()); assertEquals(5.0, d.getPercentOfGross());
        Tax t = InsightsMath.tax(List.of(a, b));
        assertEquals(190.0, t.getTaxableSales()); assertEquals(4.75, t.getCgst()); assertEquals(9.5, t.getTotalTax());
    }

    @Test
    @DisplayName("dish profit and the menu matrix: popular + earns well = STAR, popular + low profit = PLOWHORSE, rare + earns = PUZZLE, rare + low = DOG; no recipe = no cost")
    void menuMatrix() {
        // quantities: A 20, B 20, C 2, D 2, E 5 (no recipe). Cost per serving: A 20 of 100 (profit 80), B 90 of 100 (profit 10), C 20 of 100, D 90 of 100
        List<Bill> bills = List.of(bill("2026-10-19T12:00:00", "5000", T, Bill.PaymentMode.CASH, 1L, "T-1",
                1L, 20, "2000", 2L, 20, "2000", 3L, 2, "200", 4L, 2, "200", 5L, 5, "500"));
        List<DishRow> rows = InsightsMath.dishes(bills, Map.of(1L, 20.0, 2L, 90.0, 3L, 20.0, 4L, 90.0), Map.of(1L, "Burgers"), 40);
        DishRow a = rows.stream().filter(r -> r.getMenuItemId() == 1L).findFirst().orElseThrow();
        assertEquals(400.0, a.getCost()); assertEquals(1600.0, a.getProfit()); assertEquals(80.0, a.getMarginPercent()); assertEquals("Burgers", a.getCategory());
        assertEquals("STAR", a.getQuadrant());
        assertEquals("PLOWHORSE", rows.stream().filter(r -> r.getMenuItemId() == 2L).findFirst().orElseThrow().getQuadrant());
        assertEquals("PUZZLE", rows.stream().filter(r -> r.getMenuItemId() == 3L).findFirst().orElseThrow().getQuadrant());
        assertEquals("DOG", rows.stream().filter(r -> r.getMenuItemId() == 4L).findFirst().orElseThrow().getQuadrant());
        DishRow e = rows.stream().filter(r -> r.getMenuItemId() == 5L).findFirst().orElseThrow();
        assertNull(e.getCost()); assertNull(e.getQuadrant()); assertEquals(500.0, e.getRevenue());
        assertEquals(1, InsightsMath.dishes(bills, Map.of(), Map.of(), 1).size(), "the list is capped");
    }

    @Test
    @DisplayName("dishes that never sold in the period")
    void neverSold() {
        MenuItem a = new MenuItem(); a.setId(1L); a.setName("Burger");
        MenuItem b = new MenuItem(); b.setId(2L); b.setName("Tea");
        List<NameRow> n = InsightsMath.neverSold(List.of(a, b), Set.of(1L), Map.of(2L, "Hot drinks"));
        assertEquals(1, n.size()); assertEquals("Tea", n.get(0).getName()); assertEquals("Hot drinks", n.get(0).getCategory());
    }

    @Test
    @DisplayName("periods: the previous period has the same length; custom with an end before the start is refused")
    void periods() {
        LocalDate today = LocalDate.parse("2026-10-23");                       // a Friday
        AnalyticsPeriod w = AnalyticsPeriod.resolve("THIS_WEEK", null, null, today);
        assertEquals(LocalDate.parse("2026-10-19"), w.start()); assertEquals(LocalDate.parse("2026-10-14"), w.prevStart()); assertEquals(LocalDate.parse("2026-10-18"), w.prevEnd());
        AnalyticsPeriod lm = AnalyticsPeriod.resolve("last_month", null, null, today);
        assertEquals(LocalDate.parse("2026-09-01"), lm.start()); assertEquals(LocalDate.parse("2026-09-30"), lm.end()); assertEquals(LocalDate.parse("2026-08-31"), lm.prevEnd());
        AnalyticsPeriod c = AnalyticsPeriod.resolve("CUSTOM", LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-19"), today);
        assertEquals(LocalDate.parse("2026-09-30"), c.prevStart()); assertEquals(LocalDate.parse("2026-10-09"), c.prevEnd());
        assertThrows(IllegalArgumentException.class, () -> AnalyticsPeriod.resolve("CUSTOM", LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-10"), today));
        AnalyticsPeriod def = AnalyticsPeriod.resolve(null, null, null, today);
        assertEquals("THIS_MONTH", def.name()); assertEquals(LocalDate.parse("2026-10-01"), def.start());
        assertEquals("THIS_MONTH", AnalyticsPeriod.resolve("nonsense", null, null, today).name());
    }
}
