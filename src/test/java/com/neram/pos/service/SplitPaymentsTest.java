package com.neram.pos.service;

import com.neram.pos.dto.BillRequestDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillPayment;
import com.neram.pos.entity.CashierShift;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SplitPaymentsTest {

    private static BillRequestDTO.PaymentPart part(String mode, String amount) { return new BillRequestDTO.PaymentPart(mode, amount == null ? null : new BigDecimal(amount), null); }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    // ---------- rules ----------
    @Test
    void partsThatAddUpToTheBillAreAccepted() {
        List<SplitPayments.Part> p = SplitPayments.validate(List.of(part("cash", "200"), part("UPI", "100.50")), bd("300.50"));
        assertEquals(2, p.size());
        assertEquals(Bill.PaymentMode.CASH, p.get(0).mode());
        assertEquals(bd("100.50"), p.get(1).amount());
    }

    @Test
    void aPennyOfRoundingIsTolerated() {
        assertEquals(2, SplitPayments.validate(List.of(part("CASH", "100.00"), part("CARD", "49.99")), bd("150.00")).size());
    }

    @Test
    void partsThatDoNotAddUpAreRefusedWithTheDifference() {
        IllegalArgumentException under = assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(List.of(part("CASH", "200"), part("UPI", "50")), bd("300")));
        assertTrue(under.getMessage().contains("₹250.00") && under.getMessage().contains("₹300.00") && under.getMessage().contains("₹50.00 is still unpaid"), under.getMessage());
        IllegalArgumentException over = assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(List.of(part("CASH", "200"), part("UPI", "150")), bd("300")));
        assertTrue(over.getMessage().contains("₹50.00 is too much"), over.getMessage());
    }

    @Test
    void badPartsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(List.of(part("BITCOIN", "10"), part("CASH", "10")), bd("20")));
        assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(List.of(part(null, "10"), part("CASH", "10")), bd("20")));
        assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(List.of(part("CASH", "0"), part("UPI", "20")), bd("20")));
        assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(List.of(part("CASH", "-5"), part("UPI", "25")), bd("20")));
        assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(List.of(part("CASH", null), part("UPI", "20")), bd("20")));
        List<BillRequestDTO.PaymentPart> seven = new ArrayList<>(); for (int i = 0; i < 7; i++) seven.add(part("CASH", "10"));
        assertThrows(IllegalArgumentException.class, () -> SplitPayments.validate(seven, bd("70")));
        assertTrue(SplitPayments.validate(null, bd("20")).isEmpty());
        assertTrue(SplitPayments.validate(List.of(), bd("20")).isEmpty());
    }

    @Test
    void primarySummaryAndTotalsPerMode() {
        List<SplitPayments.Part> p = SplitPayments.validate(List.of(part("UPI", "100"), part("CASH", "150"), part("UPI", "50")), bd("300"));
        assertEquals(Bill.PaymentMode.CASH, SplitPayments.primary(p).mode());
        assertEquals("UPI ₹100.00 + CASH ₹150.00 + UPI ₹50.00", SplitPayments.summary(p));
        Map<Bill.PaymentMode, BigDecimal> m = SplitPayments.byMode(p);
        assertEquals(bd("150.00"), m.get(Bill.PaymentMode.UPI)); assertEquals(bd("150.00"), m.get(Bill.PaymentMode.CASH));
        assertEquals(Bill.PaymentMode.UPI, SplitPayments.primary(SplitPayments.validate(List.of(part("UPI", "100"), part("CASH", "100")), bd("200"))).mode());   // tie: first
    }

    @Test
    void rescalingKeepsPartsInProportionAndSumsToTheNewNet() {
        List<SplitPayments.Part> p = SplitPayments.validate(List.of(part("CASH", "200"), part("UPI", "100")), bd("300"));
        List<SplitPayments.Part> r = SplitPayments.rescale(p, bd("450"));
        assertEquals(bd("300.00"), r.get(0).amount()); assertEquals(bd("150.00"), r.get(1).amount());
        List<SplitPayments.Part> odd = SplitPayments.rescale(SplitPayments.validate(List.of(part("CASH", "100"), part("UPI", "100"), part("CARD", "100")), bd("300")), bd("100"));
        assertEquals(bd("100.00"), odd.stream().map(SplitPayments.Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertSame(p, SplitPayments.rescale(p, bd("300")));
    }

    // ---------- the cash drawer ----------
    @Mock private CashierShiftRepository shiftRepo;
    @Mock private CashMovementRepository movementRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuditLogService audit;
    @Mock private ShiftSettingRepository settingRepo;
    @Mock private BillRepository billRepo;
    @Mock private BillRefundRepository refundRepo;
    @Mock private CafeExpenseRepository expenseRepo;
    @Mock private BillPaymentRepository paymentRepo;
    @InjectMocks private CashierShiftService shifts;
    private CashierShift shift;

    @BeforeEach
    void setUp() {
        shift = new CashierShift(); shift.setId(5L); shift.setStatus(CashierShift.ShiftStatus.OPEN);
        shift.setCashSales(BigDecimal.ZERO); shift.setUpiSales(BigDecimal.ZERO); shift.setCardSales(BigDecimal.ZERO);
        when(shiftRepo.findAllByStatusOrderByOpenedAtDesc(CashierShift.ShiftStatus.OPEN)).thenReturn(List.of(shift));
        when(shiftRepo.findById(5L)).thenReturn(Optional.of(shift));
        when(shiftRepo.save(any(CashierShift.class))).thenAnswer(a -> a.getArgument(0));
    }

    private Bill splitBill(String net, String summary) {
        Bill b = new Bill(); b.setId(9L); b.setBillNumber("N-9"); b.setNetAmount(bd(net)); b.setPaymentMode(Bill.PaymentMode.CASH); b.setPaymentSummary(summary); return b;
    }
    private BillPayment row(String mode, String amount) { return new BillPayment(null, 9L, mode, bd(amount), null, null); }

    @Test
    void aSplitPaymentGoesToEachModeOnTheShift() {
        when(paymentRepo.findByBillIdOrderByIdAsc(9L)).thenReturn(List.of(row("CASH", "200.00"), row("UPI", "100.00")));
        shifts.recordBillPayment(splitBill("300.00", "CASH ₹200.00 + UPI ₹100.00"));
        assertEquals(bd("200.00"), shift.getCashSales()); assertEquals(bd("100.00"), shift.getUpiSales()); assertEquals(BigDecimal.ZERO, shift.getCardSales());
    }

    @Test
    void aBillPaidOneWayStillGoesToItsModeOnly() {
        Bill b = splitBill("300.00", null); b.setPaymentMode(Bill.PaymentMode.CARD);
        shifts.recordBillPayment(b);
        assertEquals(bd("300.00"), shift.getCardSales()); assertEquals(BigDecimal.ZERO, shift.getCashSales());
    }

    @Test
    void editingASplitBillMovesEachPartByItsShare() {
        shift.setCashSales(bd("200.00")); shift.setUpiSales(bd("100.00"));
        Bill b = splitBill("450.00", "CASH ₹300.00 + UPI ₹150.00"); b.setShiftId(5L);
        List<SplitPayments.Part> oldParts = List.of(new SplitPayments.Part(Bill.PaymentMode.CASH, bd("200.00"), null), new SplitPayments.Part(Bill.PaymentMode.UPI, bd("100.00"), null));
        List<SplitPayments.Part> newParts = SplitPayments.rescale(oldParts, bd("450"));
        shifts.recordBillAdjustment(b, Bill.PaymentMode.CASH, bd("300.00"), oldParts, newParts);
        assertEquals(bd("300.00"), shift.getCashSales()); assertEquals(bd("150.00"), shift.getUpiSales());
    }

    @Test
    void switchingASplitBillToOneModeMovesAllMoneyThere() {
        shift.setCashSales(bd("200.00")); shift.setUpiSales(bd("100.00"));
        Bill b = splitBill("300.00", null); b.setPaymentMode(Bill.PaymentMode.CARD); b.setShiftId(5L);
        List<SplitPayments.Part> oldParts = List.of(new SplitPayments.Part(Bill.PaymentMode.CASH, bd("200.00"), null), new SplitPayments.Part(Bill.PaymentMode.UPI, bd("100.00"), null));
        shifts.recordBillAdjustment(b, Bill.PaymentMode.CASH, bd("300.00"), oldParts, List.of());
        assertEquals(bd("0.00"), shift.getCashSales()); assertEquals(bd("0.00"), shift.getUpiSales()); assertEquals(bd("300.00"), shift.getCardSales());
    }

    @Test
    void theOldSingleModeAdjustmentStillWorks() {
        shift.setCashSales(bd("100.00"));
        Bill b = splitBill("150.00", null); b.setShiftId(5L);
        shifts.recordBillAdjustment(b, Bill.PaymentMode.CASH, bd("100.00"));
        assertEquals(bd("150.00"), shift.getCashSales());
    }

    // ---------- the payment split in analytics ----------
    @Test
    void analyticsAddsEachPartToItsOwnModeAndCountsTheOrderOnce() {
        Bill split = splitBill("300.00", "CASH ₹200.00 + UPI ₹100.00");
        Bill single = new Bill(); single.setId(10L); single.setNetAmount(bd("100")); single.setPaymentMode(Bill.PaymentMode.UPI);
        Map<Long, List<SplitPayments.Part>> parts = Map.of(9L, List.of(new SplitPayments.Part(Bill.PaymentMode.CASH, bd("200.00"), null), new SplitPayments.Part(Bill.PaymentMode.UPI, bd("100.00"), null)));
        var slices = InsightsMath.byPaymentMode(List.of(split, single), parts);
        var by = new HashMap<String, com.neram.pos.dto.InsightsDTO.Slice>(); slices.forEach(s -> by.put(s.getLabel(), s));
        assertEquals(200.0, by.get("CASH").getAmount()); assertEquals(1, by.get("CASH").getOrders());
        assertEquals(200.0, by.get("UPI").getAmount()); assertEquals(1, by.get("UPI").getOrders());     // only the single UPI bill is an order here
        assertEquals(2, slices.stream().mapToInt(com.neram.pos.dto.InsightsDTO.Slice::getOrders).sum());
    }
}
