package com.neram.pos.service;

import com.neram.pos.dto.MoneyReportsDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillRefund;

import java.util.*;

/** Per-staff sales and tips from paid bills (pure, no database). The staff member is the one who took the order. */
public final class StaffPerformanceMath {
    private StaffPerformanceMath() {}

    private static double d(java.math.BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }
    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }

    /**
     * @param bills   paid bills in the period
     * @param refunds successful refunds in the period (counted against the staff member who took that bill's order)
     * @param names   user id to name
     * @param paid    tips handed over in the period, per staff
     * @param received tips received all time, per staff
     * @param paidAll tips handed over all time, per staff
     */
    public static MoneyReportsDTO.StaffReport build(List<Bill> bills, List<BillRefund> refunds, Map<Long, String> names, Map<Long, Double> paid,
                                                    Map<Long, Double> received, Map<Long, Double> paidAll, java.time.LocalDate start, java.time.LocalDate end) {
        Map<Long, Bill> billById = new HashMap<>();
        Map<Long, double[]> acc = new LinkedHashMap<>();   // orders, sales, items, discounts, refunds, tips
        for (Bill b : bills) {
            billById.put(b.getId(), b);
            if (b.getCreatedBy() == null) continue;
            double[] a = acc.computeIfAbsent(b.getCreatedBy(), k -> new double[6]);
            a[0]++; a[1] += d(b.getNetAmount());
            a[2] += b.getItems() == null ? 0 : b.getItems().stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
            a[3] += d(b.getDiscountAmount()) + d(b.getPromotionDiscountAmount());
            a[5] += d(b.getTipAmount());
        }
        for (BillRefund r : refunds) {
            Bill b = billById.get(r.getBillId());
            if (b == null || b.getCreatedBy() == null) continue;
            acc.get(b.getCreatedBy())[4] += d(r.getAmount());
        }
        // a staff member with a balance or payout but no orders in this period is still listed
        Set<Long> ids = new LinkedHashSet<>(acc.keySet());
        for (Map.Entry<Long, Double> e : received.entrySet()) if (e.getValue() > 0.004) ids.add(e.getKey());
        ids.addAll(paid.keySet());

        double totalSales = acc.values().stream().mapToDouble(a -> a[1]).sum();
        List<MoneyReportsDTO.StaffRow> rows = new ArrayList<>();
        double totalTips = 0, totalOwed = 0;
        for (Long id : ids) {
            double[] a = acc.getOrDefault(id, new double[6]);
            double owed = r2(Math.max(0, received.getOrDefault(id, 0.0) - paidAll.getOrDefault(id, 0.0)));
            rows.add(MoneyReportsDTO.StaffRow.builder().staffId(id).staff(names.getOrDefault(id, "Staff #" + id))
                    .orders((int) a[0]).sales(r2(a[1])).avgOrder(a[0] > 0 ? r2(a[1] / a[0]) : 0).itemsPerOrder(a[0] > 0 ? Math.round(a[2] / a[0] * 10.0) / 10.0 : 0)
                    .discounts(r2(a[3])).refunds(r2(a[4])).tips(r2(a[5])).tipsPaid(r2(paid.getOrDefault(id, 0.0))).tipsOwed(owed)
                    .salesShare(totalSales > 0 ? Math.round(a[1] / totalSales * 1000.0) / 10.0 : 0).build());
            totalTips += a[5]; totalOwed += owed;
        }
        rows.sort(Comparator.comparingDouble(MoneyReportsDTO.StaffRow::getSales).reversed().thenComparing(MoneyReportsDTO.StaffRow::getStaff));
        long takers = acc.keySet().size();
        return MoneyReportsDTO.StaffReport.builder().start(start).end(end).staff(rows).totalSales(r2(totalSales)).totalOrders((int) acc.values().stream().mapToDouble(a -> a[0]).sum())
                .totalTips(r2(totalTips)).totalTipsOwed(r2(totalOwed)).equalShare(takers > 0 ? r2(totalTips / takers) : 0).build();
    }
}
