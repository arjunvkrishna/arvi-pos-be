package com.neram.pos.service;

import com.neram.pos.dto.MoneyReportsDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyReportsTest {

    // ---------- budget math ----------
    @Test
    void budgetStatusRules() {
        assertEquals("NONE", BudgetMath.status(null, 500, 500));
        assertEquals("NONE", BudgetMath.status(0.0, 500, 500));
        assertEquals("OK", BudgetMath.status(1000.0, 300, 600));
        assertEquals("WATCH", BudgetMath.status(1000.0, 800, 900));      // 80% used
        assertEquals("WATCH", BudgetMath.status(1000.0, 400, 1200));     // heading above
        assertEquals("OVER", BudgetMath.status(1000.0, 1001, 1500));
        assertEquals("WATCH", BudgetMath.status(1000.0, 1000, 1000));    // exactly at the budget is not over
    }

    @Test
    void projectionFollowsThePaceAndPastMonthsAreFinal() {
        assertEquals(3100.0, BudgetMath.projected(1000, 10, 31));
        assertEquals(1000.0, BudgetMath.projected(1000, 31, 31));        // month finished
        assertEquals(500.0, BudgetMath.projected(500, 0, 30));           // not started
        assertEquals(33.3, BudgetMath.usedPercent(300.0, 100));
        assertNull(BudgetMath.usedPercent(null, 100));
    }

    // ---------- budget service ----------
    @Mock private CafeExpenseRepository expenseRepo;
    @Mock private SalaryRepository salaryRepo;
    @Mock private SupplierInvoiceRepository invoiceRepo;
    @Mock private ExpenseBudgetRepository budgetRepo;
    @Mock private AuditLogService audit;
    private BudgetService budget;
    private final List<ExpenseBudget> saved = new ArrayList<>();

    private CafeExpense expense(CafeExpense.ExpenseType t, double amount) { CafeExpense e = new CafeExpense(); e.setType(t); e.setAmount(BigDecimal.valueOf(amount)); return e; }

    @BeforeEach
    void setUp() {
        budget = new BudgetService(expenseRepo, salaryRepo, invoiceRepo, budgetRepo, audit);
        when(budgetRepo.findAll()).thenAnswer(a -> new ArrayList<>(saved));
        when(budgetRepo.findByCategory(anyString())).thenAnswer(a -> saved.stream().filter(b -> b.getCategory().equals(a.getArgument(0))).findFirst());
        when(budgetRepo.save(any(ExpenseBudget.class))).thenAnswer(a -> { ExpenseBudget b = a.getArgument(0); if (b.getId() == null) { b.setId((long) saved.size() + 1); saved.add(b); } return b; });
        doAnswer(a -> { saved.remove(a.getArgument(0)); return null; }).when(budgetRepo).delete(any(ExpenseBudget.class));
    }

    private static String anyString() { return org.mockito.ArgumentMatchers.anyString(); }

    @Test
    void reportComparesEachCategoryWithItsBudgetAndAddsPayrollAndSuppliers() {
        budget.set("RENT", 20000.0, "m"); budget.set("ELECTRICITY", 3000.0, "m"); budget.set("PAYROLL", 50000.0, "m");
        when(expenseRepo.findByExpenseDateBetween(any(), any())).thenReturn(List.of(expense(CafeExpense.ExpenseType.RENT, 20000), expense(CafeExpense.ExpenseType.ELECTRICITY, 3500), expense(CafeExpense.ExpenseType.WATER, 400)));
        Salary s = new Salary(); s.setNetSalary(BigDecimal.valueOf(30000)); when(salaryRepo.findByPaidTrueAndPaidDateBetween(any(), any())).thenReturn(List.of(s));
        SupplierInvoice inv = new SupplierInvoice(); inv.setTotalAmount(BigDecimal.valueOf(12000)); when(invoiceRepo.findByInvoiceDateBetweenOrderByInvoiceDateDesc(any(), any())).thenReturn(List.of(inv));
        MoneyReportsDTO.Budget r = budget.report(2026, 10, LocalDate.of(2026, 10, 15));
        Map<String, MoneyReportsDTO.BudgetRow> by = new HashMap<>(); r.getRows().forEach(x -> by.put(x.getCategory(), x));
        assertEquals(15, r.getDaysElapsed()); assertEquals(31, r.getDaysInMonth());
        assertEquals("WATCH", by.get("RENT").getStatus());              // 100% used = at the budget, not over
        assertEquals("OVER", by.get("ELECTRICITY").getStatus());
        assertEquals(-500.0, by.get("ELECTRICITY").getRemaining());
        assertEquals("NONE", by.get("WATER").getStatus());              // spent, but no budget set
        assertEquals(400.0, by.get("WATER").getActual());
        assertEquals(30000.0, by.get("PAYROLL").getActual());
        assertEquals(12000.0, by.get("SUPPLIERS").getActual());
        assertEquals(73000.0, r.getTotal().getBudget());               // 20000 + 3000 + 50000: only budgeted categories
        assertEquals(53500.0, r.getTotal().getActual());              // 20000 + 3500 + 30000
    }

    @Test
    void budgetsAreSetClearedAndValidated() {
        budget.set("rent", 100.0, "m");
        assertEquals(1, saved.size()); assertEquals("RENT", saved.get(0).getCategory());
        budget.set("RENT", 250.0, "m");
        assertEquals(1, saved.size()); assertEquals(0, saved.get(0).getMonthlyAmount().compareTo(BigDecimal.valueOf(250)));
        budget.set("RENT", null, "m");
        assertTrue(saved.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> budget.set("LUNCH", 1.0, "m"));
        assertThrows(IllegalArgumentException.class, () -> budget.set("RENT", -5.0, "m"));
        assertThrows(IllegalArgumentException.class, () -> budget.report(2026, 13, LocalDate.now()));
    }

    // ---------- staff performance ----------
    private Bill bill(long id, Long by, double net, double discount, double tip, int qty) {
        Bill b = new Bill(); b.setId(id); b.setCreatedBy(by); b.setNetAmount(BigDecimal.valueOf(net)); b.setDiscountAmount(BigDecimal.valueOf(discount)); b.setTipAmount(BigDecimal.valueOf(tip));
        BillItem i = new BillItem(); i.setQuantity(qty); b.setItems(new ArrayList<>(List.of(i))); return b;
    }

    @Test
    void staffRowsHaveSalesAverageItemsDiscountsRefundsAndTips() {
        List<Bill> bills = List.of(bill(1, 10L, 500, 50, 20, 4), bill(2, 10L, 300, 0, 0, 2), bill(3, 11L, 200, 0, 30, 1));
        BillRefund r = new BillRefund(); r.setBillId(2L); r.setAmount(BigDecimal.valueOf(100));
        MoneyReportsDTO.StaffReport rep = StaffPerformanceMath.build(bills, List.of(r), Map.of(10L, "asha", 11L, "ravi"), Map.of(), Map.of(10L, 20.0, 11L, 30.0), Map.of(), LocalDate.now(), LocalDate.now());
        MoneyReportsDTO.StaffRow asha = rep.getStaff().get(0);
        assertEquals("asha", asha.getStaff());
        assertEquals(2, asha.getOrders()); assertEquals(800.0, asha.getSales()); assertEquals(400.0, asha.getAvgOrder()); assertEquals(3.0, asha.getItemsPerOrder());
        assertEquals(50.0, asha.getDiscounts()); assertEquals(100.0, asha.getRefunds()); assertEquals(20.0, asha.getTips());
        assertEquals(80.0, asha.getSalesShare());
        assertEquals(50.0, rep.getTotalTips()); assertEquals(25.0, rep.getEqualShare());
        assertEquals(1000.0, rep.getTotalSales()); assertEquals(3, rep.getTotalOrders());
    }

    @Test
    void owedTipsAreReceivedMinusHandedOverAndNeverNegative() {
        MoneyReportsDTO.StaffReport rep = StaffPerformanceMath.build(List.of(bill(1, 10L, 100, 0, 40, 1)), List.of(), Map.of(10L, "asha", 12L, "old"), Map.of(10L, 15.0),
                Map.of(10L, 100.0, 12L, 50.0), Map.of(10L, 60.0, 12L, 80.0), LocalDate.now(), LocalDate.now());
        Map<String, MoneyReportsDTO.StaffRow> by = new HashMap<>(); rep.getStaff().forEach(x -> by.put(x.getStaff(), x));
        assertEquals(40.0, by.get("asha").getTipsOwed());               // 100 - 60
        assertEquals(15.0, by.get("asha").getTipsPaid());               // in this period
        assertEquals(0.0, by.get("old").getTipsOwed());                 // paid more than received: clamp
        assertEquals(40.0, rep.getTotalTipsOwed());
    }

    @Test
    void aStaffMemberWithOwedTipsButNoOrdersIsStillListed() {
        MoneyReportsDTO.StaffReport rep = StaffPerformanceMath.build(List.of(), List.of(), Map.of(10L, "asha"), Map.of(), Map.of(10L, 70.0), Map.of(), LocalDate.now(), LocalDate.now());
        assertEquals(1, rep.getStaff().size()); assertEquals(70.0, rep.getStaff().get(0).getTipsOwed()); assertEquals(0, rep.getStaff().get(0).getOrders());
        assertEquals(0.0, rep.getEqualShare());
    }

    // ---------- tip payouts ----------
    @Mock private BillRepository billRepo;
    @Mock private BillRefundRepository refundRepo;
    @Mock private TipPayoutRepository payoutRepo;
    @Mock private UserRepository userRepo;

    private StaffPerformanceService staffService(double received, double alreadyPaid) {
        User u = new User(); u.setId(10L); u.setUsername("asha");
        when(userRepo.findById(10L)).thenReturn(Optional.of(u));
        when(billRepo.sumTipsByCreator()).thenReturn(Collections.singletonList(new Object[]{10L, BigDecimal.valueOf(received)}));
        TipPayout p = new TipPayout(); p.setStaffId(10L); p.setAmount(BigDecimal.valueOf(alreadyPaid));
        when(payoutRepo.findAll()).thenReturn(alreadyPaid > 0 ? List.of(p) : List.of());
        when(payoutRepo.save(any(TipPayout.class))).thenAnswer(a -> { TipPayout t = a.getArgument(0); t.setId(1L); return t; });
        return new StaffPerformanceService(billRepo, refundRepo, payoutRepo, userRepo, audit);
    }

    @Test
    void aPayoutIsRecordedUpToWhatIsOwed() {
        StaffPerformanceService s = staffService(100, 30);
        MoneyReportsDTO.Payout p = s.pay(10L, 70.0, " thank you ", "manager", LocalDate.of(2026, 10, 23));
        assertEquals(70.0, p.getAmount()); assertEquals("thank you", p.getNote()); assertEquals("asha", p.getStaffName());
        ArgumentCaptor<TipPayout> c = ArgumentCaptor.forClass(TipPayout.class); verify(payoutRepo).save(c.capture());
        assertEquals(LocalDate.of(2026, 10, 23), c.getValue().getPaidOn());
    }

    @Test
    void aPayoutAboveWhatIsOwedOrWithABadAmountIsRefused() {
        StaffPerformanceService s = staffService(100, 30);
        assertThrows(IllegalArgumentException.class, () -> s.pay(10L, 70.01, null, "m", LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> s.pay(10L, 0.0, null, "m", LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> s.pay(10L, -5.0, null, "m", LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> s.pay(10L, null, null, "m", LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> s.pay(null, 5.0, null, "m", LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> s.pay(99L, 5.0, null, "m", LocalDate.now()));
        verify(payoutRepo, never()).save(any());
    }

    // ---------- tip on a bill ----------
    @Test
    void aTipMustBeZeroOrMoreAndNotAbsurd() {
        assertEquals(new BigDecimal("0.00"), BillService.validTip(null));
        assertEquals(new BigDecimal("25.50"), BillService.validTip(new BigDecimal("25.5")));
        assertEquals(new BigDecimal("0.00"), BillService.validTip(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> BillService.validTip(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> BillService.validTip(new BigDecimal("100001")));
    }

    // ---------- daily close text ----------
    private MoneyReportsDTO.DailyClose.DailyCloseBuilder base() {
        return MoneyReportsDTO.DailyClose.builder().date(LocalDate.of(2026, 10, 23)).sales(12450).orders(31).avgBill(401.6).previousDaySales(10000).changePercent(24.5)
                .payments(List.of(MoneyReportsDTO.NameAmount.builder().name("Cash").amount(7000).count(15).build(), MoneyReportsDTO.NameAmount.builder().name("UPI").amount(5450).count(16).build()))
                .shifts(List.of()).topDishes(List.of()).topStaff(List.of()).overBudget(List.of());
    }

    @Test
    void textLeavesOutSectionsThatHaveNothingToSay() {
        String t = DailyCloseText.format(base().build());
        assertTrue(t.contains("NERAM daily close - Fri, 23 Oct 2026"));
        assertTrue(t.contains("Sales ₹12,450 from 31 orders, average bill ₹402"));
        assertTrue(t.contains("Up 24.5% on the day before (₹10,000)"));
        assertTrue(t.contains("Paid by: Cash ₹7,000, UPI ₹5,450"));
        assertTrue(t.contains("No shift today."));
        assertFalse(t.contains("Losses")); assertFalse(t.contains("Stock to look at")); assertFalse(t.contains("Over budget"));
    }

    @Test
    void textShowsLossesCashShortageStockAndBudgetProblems() {
        MoneyReportsDTO.ShiftLine closed = MoneyReportsDTO.ShiftLine.builder().cashier("asha").status("CLOSED").expectedCash(5000.0).countedCash(4880.0).variance(-120.0).build();
        MoneyReportsDTO.ShiftLine open = MoneyReportsDTO.ShiftLine.builder().cashier("ravi").status("OPEN").expectedCash(900.0).build();
        String t = DailyCloseText.format(base().discounts(300).refundCount(2).refunds(450).voidedCount(1).voidedValue(220).wasteCost(80).shifts(List.of(closed, open)).openShifts(1)
                .lowStock(3).belowZero(1).expired(1).expiringSoon(2).overBudget(List.of("Electricity")).tips(150).expenses(2000).topExpense("Rent").build());
        assertTrue(t.contains("Discounts ₹300")); assertTrue(t.contains("Refunds ₹450 (2)")); assertTrue(t.contains("Voided orders 1 worth ₹220")); assertTrue(t.contains("Stock wasted ₹80"));
        assertTrue(t.contains("asha: expected ₹5,000, counted ₹4,880 (SHORT ₹120)"));
        assertTrue(t.contains("ravi: still open, expected cash ₹900"));
        assertTrue(t.contains("3 low")); assertTrue(t.contains("1 below zero")); assertTrue(t.contains("1 expired")); assertTrue(t.contains("2 expiring soon"));
        assertTrue(t.contains("Over budget this month: Electricity")); assertTrue(t.contains("Tips received ₹150")); assertTrue(t.contains("most on Rent"));
    }

    @Test
    void textSaysWhenNothingWasSoldTheDayBefore() {
        String t = DailyCloseText.format(base().previousDaySales(0).changePercent(null).build());
        assertTrue(t.contains("Nothing sold the day before."));
        assertEquals("12 am - 1 am", DailyCloseText.hour(0)); assertEquals("1 pm - 2 pm", DailyCloseText.hour(13)); assertEquals("11 pm - 12 am", DailyCloseText.hour(23));
    }
}
