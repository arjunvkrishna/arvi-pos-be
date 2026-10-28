package com.neram.pos.service;

import com.neram.pos.entity.Customer;
import com.neram.pos.entity.LoyaltyLedger;
import com.neram.pos.repository.CustomerRepository;
import com.neram.pos.repository.LoyaltyLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Loyalty rules: ₹100 earns 1 point, 1 point pays ₹1, at least 100 points to redeem, and the points lapse when the
 * customer has not bought for the validity period (every purchase starts that period again).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyExpiryTest {

    private static final LocalDate DAY_ONE = LocalDate.of(2027, 1, 1);

    @Mock private CustomerRepository customers;
    @Mock private LoyaltyLedgerRepository ledger;
    @Mock private LoyaltySettingsService settings;

    private CustomerService svc;
    private Customer cust;
    private final List<LoyaltyLedger> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(settings.rules()).thenReturn(new LoyaltySettingsService.Rules(180, 100));
        svc = new CustomerService(customers, ledger, settings);
        cust = Customer.builder().id(3L).name("Asha").phone("+919999999999").pointsBalance(0).build();
        when(customers.findByIdForUpdate(3L)).thenReturn(Optional.of(cust));
        when(ledger.save(any(LoyaltyLedger.class))).thenAnswer(i -> { rows.add(i.getArgument(0)); return i.getArgument(0); });
        when(ledger.findByBillId(anyLong())).thenAnswer(i -> new ArrayList<>(rows));
    }

    private void buy(String rupees, LocalDate day) { svc.earnPoints(3L, 1L, new BigDecimal(rupees), day); }

    @Test
    @DisplayName("a purchase earns points and sets the expiry to the validity period after it")
    void purchaseSetsTheExpiry() {
        buy("500", DAY_ONE);
        assertEquals(5, cust.getPointsBalance());
        assertEquals(DAY_ONE.plusDays(180), cust.getPointsExpireAt());
    }

    @Test
    @DisplayName("buying again in the middle moves the expiry to the new purchase, not the old date")
    void newPurchaseRestartsThePeriod() {
        buy("500", DAY_ONE);
        buy("300", DAY_ONE.plusDays(100));
        assertEquals(8, cust.getPointsBalance());
        assertEquals(DAY_ONE.plusDays(280), cust.getPointsExpireAt());
    }

    @Test
    @DisplayName("a small purchase that earns nothing still restarts the period")
    void evenASmallPurchaseRestartsThePeriod() {
        buy("500", DAY_ONE);
        buy("40", DAY_ONE.plusDays(150));
        assertEquals(5, cust.getPointsBalance());
        assertEquals(DAY_ONE.plusDays(330), cust.getPointsExpireAt());
    }

    @Test
    @DisplayName("the last valid day still counts; the day after, the points are gone")
    void lastValidDayBoundary() {
        buy("500", DAY_ONE);
        LocalDate lastValid = cust.getPointsExpireAt();
        assertEquals(0, svc.expireOne(3L, lastValid));
        assertEquals(5, cust.getPointsBalance());
        assertEquals(5, svc.expireOne(3L, lastValid.plusDays(1)));
        assertEquals(0, cust.getPointsBalance());
        assertNull(cust.getPointsExpireAt());
        LoyaltyLedger row = rows.get(rows.size() - 1);
        assertEquals("EXPIRE", row.getType());
        assertEquals(-5, row.getPoints());
        assertEquals(0, row.getBalanceAfter());
    }

    @Test
    @DisplayName("lapsed points do not come back when the customer buys again: only the new points count")
    void lapsedPointsStayGone() {
        buy("500", DAY_ONE);
        buy("200", DAY_ONE.plusDays(181 + 1));
        assertEquals(2, cust.getPointsBalance());
        assertEquals(DAY_ONE.plusDays(182).plusDays(180), cust.getPointsExpireAt());
        assertTrue(rows.stream().anyMatch(r -> "EXPIRE".equals(r.getType()) && r.getPoints() == -5));
    }

    @Test
    @DisplayName("nothing to redeem until the balance reaches the minimum")
    void balanceBelowTheMinimumCannotRedeem() {
        cust.setPointsBalance(99);
        cust.setPointsExpireAt(DAY_ONE.plusDays(10));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.redeemPoints(3L, 1L, 99, DAY_ONE));
        assertTrue(e.getMessage().contains("at least 100"), e.getMessage());
        assertEquals(99, cust.getPointsBalance());
    }

    @Test
    @DisplayName("a redemption smaller than the minimum is refused even when the balance is enough")
    void redemptionBelowTheMinimumIsRefused() {
        cust.setPointsBalance(250);
        cust.setPointsExpireAt(DAY_ONE.plusDays(10));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.redeemPoints(3L, 1L, 99, DAY_ONE));
        assertTrue(e.getMessage().contains("least that can be redeemed is 100"), e.getMessage());
        assertEquals(250, cust.getPointsBalance());
    }

    @Test
    @DisplayName("redeeming exactly 100 points from 100 takes the balance back to 0 and clears the expiry")
    void redeemingEverythingResetsToZero() {
        cust.setPointsBalance(100);
        cust.setPointsExpireAt(DAY_ONE.plusDays(10));
        svc.redeemPoints(3L, 1L, 100, DAY_ONE);
        assertEquals(0, cust.getPointsBalance());
        assertNull(cust.getPointsExpireAt());
        assertEquals(-100, rows.get(0).getPoints());
        assertEquals("REDEEM", rows.get(0).getType());
    }

    @Test
    @DisplayName("redeeming part of the balance leaves the rest, and the purchase that follows restarts the period")
    void partialRedeemKeepsTheRest() {
        cust.setPointsBalance(250);
        cust.setPointsExpireAt(DAY_ONE.plusDays(10));
        svc.redeemPoints(3L, 1L, 100, DAY_ONE);
        assertEquals(150, cust.getPointsBalance());
        assertEquals(DAY_ONE.plusDays(10), cust.getPointsExpireAt());
        svc.earnPoints(3L, 1L, BigDecimal.ZERO, DAY_ONE);
        assertEquals(DAY_ONE.plusDays(180), cust.getPointsExpireAt());
    }

    @Test
    @DisplayName("points that have lapsed cannot be redeemed, and the customer is told why")
    void lapsedPointsCannotBeRedeemed() {
        cust.setPointsBalance(500);
        cust.setPointsExpireAt(DAY_ONE);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.redeemPoints(3L, 1L, 100, DAY_ONE.plusDays(1)));
        assertTrue(e.getMessage().contains("expired"), e.getMessage());
    }

    @Test
    @DisplayName("a customer who holds points but has no date yet (older data) is not expired; the next purchase gives a date")
    void missingDateIsNeverExpired() {
        cust.setPointsBalance(40);
        assertEquals(0, svc.expireOne(3L, DAY_ONE.plusDays(5000)));
        assertEquals(40, cust.getPointsBalance());
        buy("100", DAY_ONE);
        assertEquals(41, cust.getPointsBalance());
        assertEquals(DAY_ONE.plusDays(180), cust.getPointsExpireAt());
    }

    @Test
    @DisplayName("reversing the earn of a bill back to zero clears the expiry; giving redeemed points back gives them a date")
    void reversalKeepsTheDateConsistent() {
        buy("500", DAY_ONE);
        rows.clear();
        rows.add(LoyaltyLedger.builder().customerId(3L).billId(1L).type("EARN").points(5).balanceAfter(5).build());
        svc.reversePointsForBill(1L, DAY_ONE);
        assertEquals(0, cust.getPointsBalance());
        assertNull(cust.getPointsExpireAt());

        rows.clear();
        rows.add(LoyaltyLedger.builder().customerId(3L).billId(2L).type("REDEEM").points(-100).balanceAfter(0).build());
        svc.reversePointsForBill(2L, DAY_ONE);
        assertEquals(100, cust.getPointsBalance());
        assertEquals(DAY_ONE.plusDays(180), cust.getPointsExpireAt());
    }

    @Test
    @DisplayName("a bill with no customer (no phone number given) earns nothing and touches nobody")
    void noCustomerNoPoints() {
        svc.earnPoints(null, 1L, new BigDecimal("5000"), DAY_ONE);
        verify(customers, never()).findByIdForUpdate(any());
        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("the hourly job expires each lapsed customer and keeps going when one fails")
    void jobExpiresEveryoneAndSurvivesAFailure() {
        CustomerService service = mock(CustomerService.class);
        when(service.idsDueForExpiry(DAY_ONE)).thenReturn(List.of(1L, 2L, 3L));
        when(service.expireOne(1L, DAY_ONE)).thenReturn(50);
        when(service.expireOne(2L, DAY_ONE)).thenThrow(new RuntimeException("boom"));
        when(service.expireOne(3L, DAY_ONE)).thenReturn(120);
        assertEquals(2, new LoyaltyExpiryJob(service).run(DAY_ONE));
        verify(service).expireOne(3L, DAY_ONE);
    }
}
