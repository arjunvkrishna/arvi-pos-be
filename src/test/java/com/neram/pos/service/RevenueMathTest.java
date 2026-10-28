package com.neram.pos.service;

import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillPayment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Net Sales Revenue = tendered payments - GST - tips, with split bills, single-mode bills and refunds mixed. */
class RevenueMathTest {

    private static Bill bill(long id, String net, String tax, String tip, Bill.PaymentMode mode) {
        Bill b = new Bill();
        b.setId(id);
        b.setNetAmount(new BigDecimal(net));
        b.setTaxAmount(new BigDecimal(tax));
        b.setTipAmount(new BigDecimal(tip));
        b.setPaymentMode(mode);
        b.setStatus(Bill.Status.PAID);
        return b;
    }

    private static BillPayment pay(long billId, String mode, String amount) {
        return new BillPayment(null, billId, mode, new BigDecimal(amount), null, null);
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void singleModeBillsAreCountedEvenWhenAnotherBillIsSplit() {
        Bill single = bill(1, "105.00", "5.00", "0.00", Bill.PaymentMode.CASH);
        Bill split = bill(2, "210.00", "10.00", "0.00", Bill.PaymentMode.UPI);
        Map<Long, List<BillPayment>> pays = Map.of(2L, List.of(pay(2, "CASH", "100.00"), pay(2, "UPI", "110.00")));

        RevenueMath.Totals t = RevenueMath.compute(List.of(single, split), pays, Map.of());

        eq("315.00", t.tendered());
        eq("15.00", t.gst());
        eq("300.00", t.netSalesRevenue());
    }

    @Test
    void tipsAreNotRevenue() {
        Bill b = bill(1, "230.00", "10.00", "20.00", Bill.PaymentMode.CARD);
        RevenueMath.Totals t = RevenueMath.compute(List.of(b), Map.of(), Map.of());
        eq("200.00", t.netSalesRevenue());
        eq("20.00", t.tips());
    }

    @Test
    void partialRefundScalesDownRevenueGstAndTips() {
        Bill b = bill(1, "200.00", "20.00", "10.00", Bill.PaymentMode.UPI);
        b.setStatus(Bill.Status.PARTIALLY_REFUNDED);
        RevenueMath.Totals t = RevenueMath.compute(List.of(b), Map.of(), Map.of(1L, new BigDecimal("50.00")));

        eq("150.00", t.tendered());
        eq("15.00", t.gst());
        eq("7.50", t.tips());
        eq("127.50", t.netSalesRevenue());
    }

    @Test
    void refundNeverExceedsWhatWasPaid() {
        Bill b = bill(1, "100.00", "0.00", "0.00", Bill.PaymentMode.CASH);
        RevenueMath.Totals t = RevenueMath.compute(List.of(b), Map.of(), Map.of(1L, new BigDecimal("999")));
        eq("0.00", t.netSalesRevenue());
    }

    @Test
    void loyaltyRedeemedIsTrackedForSplitAndSingleBills() {
        Bill loyaltyOnly = bill(1, "80.00", "0.00", "0.00", Bill.PaymentMode.LOYALTY_POINTS);
        Bill split = bill(2, "100.00", "0.00", "0.00", Bill.PaymentMode.CASH);
        Map<Long, List<BillPayment>> pays = Map.of(2L, List.of(pay(2, "CASH", "60.00"), pay(2, "LOYALTY_POINTS", "40.00")));
        RevenueMath.Totals t = RevenueMath.compute(List.of(loyaltyOnly, split), pays, Map.of());
        eq("120.00", t.loyaltyRedeemed());
    }
}
