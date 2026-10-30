package com.neram.pos.service;

import com.neram.pos.entity.Bill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Only cash, UPI, card and loyalty points are accepted; anything else is refused rather than quietly becoming cash. */
class AcceptedPaymentModeTest {

    @Test
    @DisplayName("cash, UPI, card and loyalty points are accepted, in any letter case")
    void accepted() {
        assertEquals(Bill.PaymentMode.CASH, BillService.acceptedPaymentMode("cash"));
        assertEquals(Bill.PaymentMode.UPI, BillService.acceptedPaymentMode(" UPI "));
        assertEquals(Bill.PaymentMode.CARD, BillService.acceptedPaymentMode("Card"));
        assertEquals(Bill.PaymentMode.LOYALTY_POINTS, BillService.acceptedPaymentMode("loyalty_points"));
    }

    @Test
    @DisplayName("an unknown mode is refused and the message lists what is accepted")
    void unknownRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> BillService.acceptedPaymentMode("cheque"));
        assertTrue(e.getMessage().contains("CASH, UPI, CARD or LOYALTY_POINTS"));
    }
}
