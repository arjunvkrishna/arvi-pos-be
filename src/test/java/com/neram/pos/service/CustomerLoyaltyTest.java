package com.neram.pos.service;

import com.neram.pos.entity.Customer;
import com.neram.pos.entity.LoyaltyLedger;
import com.neram.pos.repository.CustomerRepository;
import com.neram.pos.repository.LoyaltyLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerLoyaltyTest {
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
        cust = Customer.builder().id(3L).name("Asha").phone("+919999999999").pointsBalance(10).build();
        when(customers.findByIdForUpdate(3L)).thenReturn(Optional.of(cust));
        when(ledger.save(any(LoyaltyLedger.class))).thenAnswer(i -> { rows.add(i.getArgument(0)); return i.getArgument(0); });
        when(ledger.findByBillId(anyLong())).thenAnswer(i -> new ArrayList<>(rows));
    }

    private static long anyLong() { return org.mockito.ArgumentMatchers.anyLong(); }

    @Test
    void phoneNumbersAreNormalisedToE164() {
        assertEquals("+919876543210", CustomerService.normalizePhone("98765 43210"));
        assertEquals("+919876543210", CustomerService.normalizePhone("919876543210"));
        assertEquals("+14155550123", CustomerService.normalizePhone("+1 415 555 0123"));
        assertNull(CustomerService.normalizePhone(null));
    }

    @Test
    void earnsOnePointPerHundredRupeesRoundedDown() {
        svc.earnPoints(3L, 1L, new BigDecimal("259.99"));
        assertEquals(12, cust.getPointsBalance());
        assertEquals("EARN", rows.get(0).getType());
        assertEquals(12, rows.get(0).getBalanceAfter());
    }

    @Test
    void smallSpendEarnsNothingAndWritesNoLedgerRow() {
        svc.earnPoints(3L, 1L, new BigDecimal("99"));
        assertEquals(10, cust.getPointsBalance());
        assertTrue(rows.isEmpty());
    }

    @Test
    void redeemRejectsMoreThanTheBalanceWithoutChangingIt() {
        cust.setPointsBalance(150);
        assertThrows(IllegalArgumentException.class, () -> svc.redeemPoints(3L, 1L, 200));
        assertEquals(150, cust.getPointsBalance());
        assertTrue(rows.isEmpty());
    }

    @Test
    void redeemDeductsAndLogsANegativeEntry() {
        cust.setPointsBalance(500);
        svc.redeemPoints(3L, 1L, 120);
        assertEquals(380, cust.getPointsBalance());
        assertEquals(-120, rows.get(0).getPoints());
    }

    @Test
    void reversingAnEarnAfterThePointsWereSpentFloorsAtZeroAndNotesTheDeficit() {
        cust.setPointsBalance(2);
        rows.add(LoyaltyLedger.builder().customerId(3L).billId(8L).type("EARN").points(5).balanceAfter(7).build());

        svc.reversePointsForBill(8L);

        assertEquals(0, cust.getPointsBalance());
        LoyaltyLedger refund = rows.get(rows.size() - 1);
        assertEquals("REFUND", refund.getType());
        assertTrue(refund.getNote().contains("deficit"));
    }

    @Test
    void reversalRunsOnlyOncePerBill() {
        rows.add(LoyaltyLedger.builder().customerId(3L).billId(8L).type("EARN").points(5).balanceAfter(15).build());
        cust.setPointsBalance(15);
        svc.reversePointsForBill(8L);
        assertEquals(10, cust.getPointsBalance());
        svc.reversePointsForBill(8L);
        assertEquals(10, cust.getPointsBalance());
    }

    @Test
    void reversingARedeemGivesThePointsBack() {
        cust.setPointsBalance(1);
        rows.add(LoyaltyLedger.builder().customerId(3L).billId(8L).type("REDEEM").points(-6).balanceAfter(1).build());
        svc.reversePointsForBill(8L);
        assertEquals(7, cust.getPointsBalance());
    }
}
