package com.neram.pos.service;

import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillPayment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Statutory revenue arithmetic for the P&L: Net Sales Revenue = tendered payments - GST - tips.
 * Single-mode bills have no bill_payments rows (only split bills do), so a bill without rows is
 * taken as paid in full by its payment mode. Refunds reduce what was tendered, and the bill's GST
 * and tips are scaled down by the same share so a refund does not leave tax behind.
 */
public final class RevenueMath {

    private RevenueMath() {}

    public record Totals(BigDecimal grossSales, BigDecimal gst, BigDecimal tips, BigDecimal tendered,
                         BigDecimal loyaltyRedeemed, BigDecimal netSalesRevenue) {}

    public static Totals compute(List<Bill> bills, Map<Long, List<BillPayment>> paymentsByBill, Map<Long, BigDecimal> refundsByBill) {
        BigDecimal gross = BigDecimal.ZERO, gst = BigDecimal.ZERO, tips = BigDecimal.ZERO;
        BigDecimal tendered = BigDecimal.ZERO, loyalty = BigDecimal.ZERO;

        for (Bill b : bills) {
            BigDecimal net = nz(b.getNetAmount());
            List<BillPayment> rows = paymentsByBill.get(b.getId());
            boolean split = rows != null && !rows.isEmpty();

            BigDecimal paid = BigDecimal.ZERO, billLoyalty = BigDecimal.ZERO;
            if (split) {
                for (BillPayment bp : rows) {
                    paid = paid.add(nz(bp.getAmount()));
                    if ("LOYALTY_POINTS".equalsIgnoreCase(bp.getMode())) billLoyalty = billLoyalty.add(nz(bp.getAmount()));
                }
            } else {
                paid = net;
                if (b.getPaymentMode() == Bill.PaymentMode.LOYALTY_POINTS) billLoyalty = net;
            }

            BigDecimal refunded = nz(refundsByBill.get(b.getId())).min(paid);
            BigDecimal keep = paid.signum() > 0 ? paid.subtract(refunded).divide(paid, 6, RoundingMode.HALF_UP) : BigDecimal.ONE;

            gross = gross.add(net.subtract(refunded).max(BigDecimal.ZERO));
            gst = gst.add(nz(b.getTaxAmount()).multiply(keep));
            tips = tips.add(nz(b.getTipAmount()).multiply(keep));
            tendered = tendered.add(paid.subtract(refunded));
            loyalty = loyalty.add(billLoyalty.multiply(keep));
        }

        BigDecimal netRevenue = tendered.subtract(gst).subtract(tips).max(BigDecimal.ZERO);
        return new Totals(s(gross), s(gst), s(tips), s(tendered), s(loyalty), s(netRevenue));
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal s(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
