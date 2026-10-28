package com.neram.pos.service;

import com.neram.pos.dto.AccountingDTO;
import com.neram.pos.dto.AccountingDTO.*;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final BillRefundRepository billRefundRepository;
    private final CafeExpenseRepository cafeExpenseRepository;
    private final SalaryRepository salaryRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final GiftCardRepository giftCardRepository;
    private final TipPoolEmployeeShareRepository tipPoolEmployeeShareRepository;
    private final CashierShiftRepository cashierShiftRepository;

    public ProfitAndLossReport getProfitAndLoss(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(startDate, endDate);

        List<Long> billIds = bills.stream().map(Bill::getId).collect(Collectors.toList());
        Map<Long, List<BillPayment>> paymentsByBill = billIds.isEmpty() ? Map.of()
                : billPaymentRepository.findByBillIdIn(billIds).stream().collect(Collectors.groupingBy(BillPayment::getBillId));
        Map<Long, BigDecimal> refundsByBill = new HashMap<>();
        for (Bill b : bills) {
            if (b.getStatus() == Bill.Status.PARTIALLY_REFUNDED) {
                refundsByBill.put(b.getId(), billRefundRepository.sumSuccessfulRefundsByBillId(b.getId()));
            }
        }

        RevenueMath.Totals totals = RevenueMath.compute(bills, paymentsByBill, refundsByBill);
        BigDecimal grossSales = totals.grossSales();
        BigDecimal gstCollected = totals.gst();
        BigDecimal tipsCollected = totals.tips();
        BigDecimal loyaltyRedeemed = totals.loyaltyRedeemed();
        // Net Sales Revenue = Sum(bill_payments) - GST Collected - Tips Collected
        BigDecimal netSalesRevenue = totals.netSalesRevenue();

        // COGS from Supplier Invoices in date range
        List<SupplierInvoice> invoices = supplierInvoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(startDate, endDate);
        BigDecimal cogs = invoices.stream()
                .map(inv -> inv.getSubtotal() != null ? inv.getSubtotal() : (inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Gross Profit = Net Sales Revenue - COGS - Loyalty Points Redeemed
        BigDecimal grossProfit = netSalesRevenue.subtract(cogs).subtract(loyaltyRedeemed);

        // Operating Expenses (Cafe Expenses in date range)
        List<CafeExpense> expenses = cafeExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(startDate, endDate);
        BigDecimal operatingExpenses = BigDecimal.ZERO;
        Map<String, BigDecimal> categoryMap = new LinkedHashMap<>();

        for (CafeExpense exp : expenses) {
            BigDecimal amt = exp.getAmount() != null ? exp.getAmount() : BigDecimal.ZERO;
            operatingExpenses = operatingExpenses.add(amt);
            String cat = exp.getType() != null ? exp.getType().name() : "MISC";
            categoryMap.put(cat, categoryMap.getOrDefault(cat, BigDecimal.ZERO).add(amt));
        }

        // Salaries Paid in date range
        List<Salary> salaries = salaryRepository.findByPaidTrueAndPaidDateBetween(startDate, endDate);
        BigDecimal salariesPaid = salaries.stream()
                .map(s -> s.getNetSalary() != null ? s.getNetSalary() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Net Profit = Gross Profit - Operating Expenses - Salaries Paid
        BigDecimal netProfit = grossProfit.subtract(operatingExpenses).subtract(salariesPaid);

        List<ExpenseCategoryRow> breakdown = new ArrayList<>();
        double totalExpDouble = operatingExpenses.doubleValue();
        for (Map.Entry<String, BigDecimal> entry : categoryMap.entrySet()) {
            double pct = totalExpDouble > 0 ? (entry.getValue().doubleValue() / totalExpDouble) * 100.0 : 0.0;
            breakdown.add(ExpenseCategoryRow.builder()
                    .category(entry.getKey())
                    .amount(entry.getValue())
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .build());
        }

        return ProfitAndLossReport.builder()
                .period(startDate + " to " + endDate)
                .startDate(startDate)
                .endDate(endDate)
                .grossSales(grossSales.setScale(2, RoundingMode.HALF_UP))
                .gstCollected(gstCollected.setScale(2, RoundingMode.HALF_UP))
                .tipsCollected(tipsCollected.setScale(2, RoundingMode.HALF_UP))
                .netSalesRevenue(netSalesRevenue.setScale(2, RoundingMode.HALF_UP))
                .cogs(cogs.setScale(2, RoundingMode.HALF_UP))
                .loyaltyPointsRedeemed(loyaltyRedeemed.setScale(2, RoundingMode.HALF_UP))
                .grossProfit(grossProfit.setScale(2, RoundingMode.HALF_UP))
                .operatingExpenses(operatingExpenses.setScale(2, RoundingMode.HALF_UP))
                .salariesPaid(salariesPaid.setScale(2, RoundingMode.HALF_UP))
                .netProfit(netProfit.setScale(2, RoundingMode.HALF_UP))
                .expenseBreakdown(breakdown)
                .build();
    }

    public BalanceSheetReport getBalanceSheet() {
        LocalDate today = LocalDate.now();

        // 1. Inventory Valuation at Cost
        List<InventoryItem> items = inventoryItemRepository.findByActiveTrue();
        BigDecimal inventoryValuation = BigDecimal.ZERO;
        for (InventoryItem item : items) {
            if (item.getQuantity() != null && item.getQuantity() > 0 && item.getCostPerUnit() != null) {
                inventoryValuation = inventoryValuation.add(item.getCostPerUnit().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        // 2. Cash In Drawers / Operating Cash Float
        BigDecimal cashInDrawers = BigDecimal.ZERO;
        List<CashierShift> openShifts = cashierShiftRepository.findAllByStatusOrderByOpenedAtDesc(CashierShift.ShiftStatus.OPEN);
        for (CashierShift shift : openShifts) {
            BigDecimal drawerCash = shift.getOpeningFloat() != null ? shift.getOpeningFloat() : BigDecimal.ZERO;
            if (shift.getCashSales() != null) drawerCash = drawerCash.add(shift.getCashSales());
            if (shift.getGiftCardCashSales() != null) drawerCash = drawerCash.add(shift.getGiftCardCashSales());
            if (shift.getCashOutTotal() != null) drawerCash = drawerCash.subtract(shift.getCashOutTotal());
            if (shift.getCashRefunds() != null) drawerCash = drawerCash.subtract(shift.getCashRefunds());
            cashInDrawers = cashInDrawers.add(drawerCash);
        }

        BigDecimal totalAssets = cashInDrawers.add(inventoryValuation);

        // 3. Liabilities
        BigDecimal unpaidSuppliers = supplierInvoiceRepository.totalOutstandingAll();
        if (unpaidSuppliers == null) unpaidSuppliers = BigDecimal.ZERO;

        BigDecimal giftCardLiability = giftCardRepository.sumActiveBalances();
        if (giftCardLiability == null) giftCardLiability = BigDecimal.ZERO;

        BigDecimal unpaidTips = tipPoolEmployeeShareRepository.sumUnpaidTips();
        if (unpaidTips == null) unpaidTips = BigDecimal.ZERO;

        BigDecimal totalLiabilities = unpaidSuppliers.add(giftCardLiability).add(unpaidTips);

        // 4. Net Worth / Equity
        BigDecimal netWorth = totalAssets.subtract(totalLiabilities);

        return BalanceSheetReport.builder()
                .asOfDate(today)
                .assets(Assets.builder()
                        .cashInDrawerAndBank(cashInDrawers.setScale(2, RoundingMode.HALF_UP))
                        .inventoryValuationAtCost(inventoryValuation.setScale(2, RoundingMode.HALF_UP))
                        .totalAssets(totalAssets.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .liabilities(Liabilities.builder()
                        .unpaidSupplierInvoices(unpaidSuppliers.setScale(2, RoundingMode.HALF_UP))
                        .giftCardLiability(giftCardLiability.setScale(2, RoundingMode.HALF_UP))
                        .unpaidTips(unpaidTips.setScale(2, RoundingMode.HALF_UP))
                        .totalLiabilities(totalLiabilities.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .netWorthOrEquity(netWorth.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    public GiftCardLiabilitySummary getGiftCardLiability() {
        BigDecimal balance = giftCardRepository.sumActiveBalances();
        if (balance == null) balance = BigDecimal.ZERO;
        long count = giftCardRepository.findAll().stream()
                .filter(g -> g.getStatus() == GiftCard.Status.ACTIVE)
                .count();
        return GiftCardLiabilitySummary.builder()
                .activeGiftCardBalance(balance.setScale(2, RoundingMode.HALF_UP))
                .activeCardCount(count)
                .build();
    }
}
