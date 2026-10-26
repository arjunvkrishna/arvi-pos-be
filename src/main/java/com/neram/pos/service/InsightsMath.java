package com.neram.pos.service;

import com.neram.pos.dto.InsightsDTO.*;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.MenuItem;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The numbers behind the analytics "insights", as plain functions over a list of paid bills so they can be tested without a database. */
public final class InsightsMath {
    private InsightsMath() {}

    static double d(BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }
    static double r2(double v) { return Math.round(v * 100.0) / 100.0; }
    static double r1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double share(double part, double total) { return total > 0 ? r1(part / total * 100.0) : 0.0; }

    public static Metric metric(double current, double previous) {
        Double change = previous > 0 ? r1((current - previous) / previous * 100.0) : null;
        return Metric.builder().current(r2(current)).previous(r2(previous)).changePercent(change).build();
    }

    public static double sales(List<Bill> bills) { return r2(bills.stream().mapToDouble(b -> d(b.getNetAmount())).sum()); }

    public static int itemCount(List<Bill> bills) {
        return bills.stream().filter(b -> b.getItems() != null).flatMap(b -> b.getItems().stream()).mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
    }

    public static double discountTotal(List<Bill> bills) { return r2(bills.stream().mapToDouble(b -> d(b.getDiscountAmount()) + d(b.getPromotionDiscountAmount())).sum()); }

    /** Sales and orders for each hour of the day (0..23). */
    public static List<HourPoint> byHour(List<Bill> bills) {
        double[] sales = new double[24]; int[] orders = new int[24];
        for (Bill b : bills) { if (b.getCreatedAt() == null) continue; int h = b.getCreatedAt().getHour(); sales[h] += d(b.getNetAmount()); orders[h]++; }
        List<HourPoint> out = new ArrayList<>();
        for (int h = 0; h < 24; h++) out.add(HourPoint.builder().hour(h).sales(r2(sales[h])).orders(orders[h]).build());
        return out;
    }

    /** Monday..Sunday. salesPerDay divides by how many of that weekday fell in the period, so a long period is not unfairly favoured. */
    public static List<WeekdayPoint> byWeekday(List<Bill> bills, LocalDate start, LocalDate end) {
        double[] sales = new double[7]; int[] orders = new int[7]; int[] days = new int[7];
        for (LocalDate x = start; !x.isAfter(end); x = x.plusDays(1)) days[x.getDayOfWeek().getValue() - 1]++;
        for (Bill b : bills) { if (b.getCreatedAt() == null) continue; int i = b.getCreatedAt().getDayOfWeek().getValue() - 1; sales[i] += d(b.getNetAmount()); orders[i]++; }
        List<WeekdayPoint> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            out.add(WeekdayPoint.builder().day(i + 1).label(DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)).sales(r2(sales[i])).orders(orders[i])
                    .avgOrder(orders[i] > 0 ? r2(sales[i] / orders[i]) : 0).days(days[i]).salesPerDay(days[i] > 0 ? r2(sales[i] / days[i]) : 0).build());
        }
        return out;
    }

    public static List<List<Double>> heatmap(List<Bill> bills) {
        double[][] m = new double[7][24];
        for (Bill b : bills) { if (b.getCreatedAt() == null) continue; m[b.getCreatedAt().getDayOfWeek().getValue() - 1][b.getCreatedAt().getHour()] += d(b.getNetAmount()); }
        List<List<Double>> out = new ArrayList<>();
        for (double[] row : m) { List<Double> r = new ArrayList<>(); for (double v : row) r.add(r2(v)); out.add(r); }
        return out;
    }

    private static List<Slice> slices(Map<String, double[]> m) {
        double total = m.values().stream().mapToDouble(v -> v[1]).sum();
        return m.entrySet().stream()
                .map(e -> Slice.builder().label(e.getKey()).orders((int) e.getValue()[0]).amount(r2(e.getValue()[1])).quantity((int) e.getValue()[2]).share(share(e.getValue()[1], total)).build())
                .sorted(Comparator.comparingDouble(Slice::getAmount).reversed()).collect(Collectors.toList());
    }

    public static String orderTypeLabel(Bill b) {
        Bill.OrderType t = b.getOrderType();
        if (t == null) t = b.getTakeawayToken() != null ? Bill.OrderType.TAKEAWAY : b.getOutdoorToken() != null ? Bill.OrderType.OUTDOOR : Bill.OrderType.TABLE;
        return t == Bill.OrderType.TAKEAWAY ? "Takeaway" : t == Bill.OrderType.OUTDOOR ? "Outdoor" : "Dine-in";
    }

    public static List<Slice> byOrderType(List<Bill> bills) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Bill b : bills) { double[] v = m.computeIfAbsent(orderTypeLabel(b), k -> new double[3]); v[0]++; v[1] += d(b.getNetAmount()); }
        return slices(m);
    }

    public static List<Slice> byPaymentMode(List<Bill> bills) { return byPaymentMode(bills, Map.of()); }

    /** Money per payment mode. A bill paid in more than one way adds each part to its own mode; the order itself is counted once, under the mode that took the most. */
    public static List<Slice> byPaymentMode(List<Bill> bills, Map<Long, List<SplitPayments.Part>> parts) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Bill b : bills) {
            List<SplitPayments.Part> split = b.getId() == null ? List.of() : parts.getOrDefault(b.getId(), List.of());
            if (split.size() > 1) {
                String primary = SplitPayments.primary(split).mode().name();
                for (SplitPayments.Part p : split) {
                    double[] v = m.computeIfAbsent(p.mode().name(), k -> new double[3]);
                    v[1] += d(p.amount());
                    if (p.mode().name().equals(primary) && p == SplitPayments.primary(split)) v[0]++;
                }
                continue;
            }
            double[] v = m.computeIfAbsent(b.getPaymentMode() != null ? b.getPaymentMode().name() : "CASH", k -> new double[3]); v[0]++; v[1] += d(b.getNetAmount());
        }
        return slices(m);
    }

    /** Revenue and quantity per menu category (dishes whose category is unknown go under "Other"). */
    public static List<Slice> byCategory(List<Bill> bills, Map<Long, String> categoryOfDish) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Bill b : bills) {
            if (b.getItems() == null) continue;
            Set<String> seen = new HashSet<>();
            for (BillItem i : b.getItems()) {
                String c = i.getMenuItemId() != null ? categoryOfDish.getOrDefault(i.getMenuItemId(), "Other") : "Other";
                double[] v = m.computeIfAbsent(c, k -> new double[3]);
                if (seen.add(c)) v[0]++;
                v[1] += d(i.getSubtotal()); v[2] += i.getQuantity() == null ? 0 : i.getQuantity();
            }
        }
        return slices(m);
    }

    public static List<StaffRow> byStaff(List<Bill> bills, Map<Long, String> names) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Bill b : bills) { String n = b.getCreatedBy() != null ? names.getOrDefault(b.getCreatedBy(), "Staff #" + b.getCreatedBy()) : "Unknown"; double[] v = m.computeIfAbsent(n, k -> new double[2]); v[0]++; v[1] += d(b.getNetAmount()); }
        double total = m.values().stream().mapToDouble(v -> v[1]).sum();
        return m.entrySet().stream().map(e -> StaffRow.builder().staff(e.getKey()).orders((int) e.getValue()[0]).sales(r2(e.getValue()[1])).avgOrder(r2(e.getValue()[1] / e.getValue()[0])).share(share(e.getValue()[1], total)).build())
                .sorted(Comparator.comparingDouble(StaffRow::getSales).reversed()).collect(Collectors.toList());
    }

    public static List<TableRow> byTable(List<Bill> bills, int limit) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Bill b : bills) { if (!"Dine-in".equals(orderTypeLabel(b)) || b.getTableNumber() == null || b.getTableNumber().isBlank()) continue; double[] v = m.computeIfAbsent(b.getTableNumber().trim(), k -> new double[2]); v[0]++; v[1] += d(b.getNetAmount()); }
        return m.entrySet().stream().map(e -> TableRow.builder().table(e.getKey()).orders((int) e.getValue()[0]).sales(r2(e.getValue()[1])).build())
                .sorted(Comparator.comparingDouble(TableRow::getSales).reversed()).limit(limit).collect(Collectors.toList());
    }

    public static Discounts discounts(List<Bill> bills) {
        double disc = bills.stream().mapToDouble(b -> d(b.getDiscountAmount())).sum();
        double promo = bills.stream().mapToDouble(b -> d(b.getPromotionDiscountAmount())).sum();
        double gross = bills.stream().mapToDouble(b -> d(b.getSubtotal())).sum();
        int n = (int) bills.stream().filter(b -> d(b.getDiscountAmount()) > 0 || d(b.getPromotionDiscountAmount()) > 0).count();
        return Discounts.builder().discountAmount(r2(disc)).promotionAmount(r2(promo)).total(r2(disc + promo)).billsWithDiscount(n).percentOfGross(share(disc + promo, gross)).build();
    }

    public static Tax tax(List<Bill> bills) {
        double taxable = 0, cgst = 0, sgst = 0, total = 0, round = 0;
        for (Bill b : bills) {
            double t = d(b.getTaxAmount());
            if (t <= 0 && d(b.getCgstAmount()) + d(b.getSgstAmount()) <= 0) continue;
            taxable += d(b.getSubtotal()) - d(b.getDiscountAmount()) - d(b.getPromotionDiscountAmount());
            cgst += d(b.getCgstAmount()); sgst += d(b.getSgstAmount()); total += t > 0 ? t : d(b.getCgstAmount()) + d(b.getSgstAmount()); round += d(b.getRoundOff());
        }
        return Tax.builder().taxableSales(r2(taxable)).cgst(r2(cgst)).sgst(r2(sgst)).totalTax(r2(total)).roundOff(r2(round)).build();
    }

    /**
     * Per dish: quantity, revenue and (with a recipe) cost, profit and margin, then the classic menu matrix. A dish is popular when it sells at
     * least 70% of the average quantity; it earns well when its profit per dish is at least the average profit per dish sold.
     */
    public static List<DishRow> dishes(List<Bill> bills, Map<Long, Double> costPerServing, Map<Long, String> categoryOfDish, int limit) {
        Map<String, double[]> agg = new LinkedHashMap<>();
        Map<String, Long> ids = new HashMap<>(); Map<String, String> names = new HashMap<>();
        for (Bill b : bills) {
            if (b.getItems() == null) continue;
            for (BillItem i : b.getItems()) {
                String key = i.getMenuItemId() != null ? "id:" + i.getMenuItemId() : "n:" + String.valueOf(i.getItemName()).toLowerCase();
                double[] v = agg.computeIfAbsent(key, k -> new double[2]);
                v[0] += i.getQuantity() == null ? 0 : i.getQuantity(); v[1] += d(i.getSubtotal());
                ids.putIfAbsent(key, i.getMenuItemId()); names.putIfAbsent(key, i.getItemName());
            }
        }
        List<DishRow> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> e : agg.entrySet()) {
            Long id = ids.get(e.getKey()); Double cps = id != null ? costPerServing.get(id) : null;
            int qty = (int) e.getValue()[0]; double rev = r2(e.getValue()[1]);
            DishRow.DishRowBuilder row = DishRow.builder().menuItemId(id).name(names.get(e.getKey())).category(id != null ? categoryOfDish.getOrDefault(id, "Other") : "Other").quantity(qty).revenue(rev);
            if (cps != null && cps > 0) {
                double cost = r2(cps * qty);
                row.costPerServing(r2(cps)).cost(cost).profit(r2(rev - cost)).marginPercent(rev > 0 ? r1((rev - cost) / rev * 100.0) : 0.0);
            }
            rows.add(row.build());
        }
        List<DishRow> known = rows.stream().filter(r -> r.getCost() != null).collect(Collectors.toList());
        if (!known.isEmpty()) {
            double avgQty = known.stream().mapToInt(DishRow::getQuantity).sum() / (double) known.size();
            double totalQty = known.stream().mapToInt(DishRow::getQuantity).sum();
            double avgProfitPerDish = totalQty > 0 ? known.stream().mapToDouble(DishRow::getProfit).sum() / totalQty : 0;
            for (DishRow r : known) {
                boolean popular = r.getQuantity() >= 0.7 * avgQty;
                boolean earns = r.getQuantity() > 0 && r.getProfit() / r.getQuantity() >= avgProfitPerDish;
                r.setQuadrant(popular ? (earns ? "STAR" : "PLOWHORSE") : (earns ? "PUZZLE" : "DOG"));
            }
        }
        rows.sort(Comparator.comparingDouble(DishRow::getRevenue).reversed());
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    public static List<NameRow> neverSold(List<MenuItem> active, Set<Long> sold, Map<Long, String> categoryOfDish) {
        return active.stream().filter(m -> !sold.contains(m.getId()))
                .map(m -> NameRow.builder().name(m.getName()).category(categoryOfDish.getOrDefault(m.getId(), "Other")).build())
                .sorted(Comparator.comparing(NameRow::getCategory).thenComparing(NameRow::getName)).collect(Collectors.toList());
    }
}
