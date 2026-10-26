package com.neram.pos.service;

import com.neram.pos.dto.MoneyReportsDTO;
import com.neram.pos.entity.CafeExpense;
import com.neram.pos.entity.ExpenseBudget;
import com.neram.pos.entity.Salary;
import com.neram.pos.entity.SupplierInvoice;
import com.neram.pos.repository.CafeExpenseRepository;
import com.neram.pos.repository.ExpenseBudgetRepository;
import com.neram.pos.repository.SalaryRepository;
import com.neram.pos.repository.SupplierInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/** Monthly budget for each expense category (plus payroll and supplier purchases) against what was actually spent. */
@Service
@RequiredArgsConstructor
public class BudgetService {

    public static final String PAYROLL = "PAYROLL";
    public static final String SUPPLIERS = "SUPPLIERS";
    /** expense types followed by the two computed categories; order is the order shown */
    public static final List<String> CATEGORIES;
    static {
        List<String> c = new ArrayList<>();
        for (CafeExpense.ExpenseType t : CafeExpense.ExpenseType.values()) c.add(t.name());
        c.add(PAYROLL); c.add(SUPPLIERS);
        CATEGORIES = List.copyOf(c);
    }

    private final CafeExpenseRepository expenses;
    private final SalaryRepository salaries;
    private final SupplierInvoiceRepository invoices;
    private final ExpenseBudgetRepository budgets;
    private final AuditLogService auditLog;

    static String label(String category) {
        if (PAYROLL.equals(category)) return "Salaries";
        if (SUPPLIERS.equals(category)) return "Supplier purchases";
        String l = category.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    @Transactional(readOnly = true)
    public MoneyReportsDTO.Budget report(int year, int month, LocalDate today) {
        YearMonth ym;
        try { ym = YearMonth.of(year, month); } catch (Exception e) { throw new IllegalArgumentException("Choose a valid month."); }
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        int daysIn = ym.lengthOfMonth();
        int elapsed = YearMonth.from(today).equals(ym) ? today.getDayOfMonth() : (ym.isBefore(YearMonth.from(today)) ? daysIn : 0);

        Map<String, Double> actual = new HashMap<>();
        for (CafeExpense e : expenses.findByExpenseDateBetween(from, to)) {
            if (e.getType() == null || e.getAmount() == null) continue;
            actual.merge(e.getType().name(), e.getAmount().doubleValue(), Double::sum);
        }
        double pay = 0;
        for (Salary s : salaries.findByPaidTrueAndPaidDateBetween(from, to)) pay += s.getNetSalary() != null ? s.getNetSalary().doubleValue() : 0;
        actual.put(PAYROLL, pay);
        double sup = 0;
        for (SupplierInvoice i : invoices.findByInvoiceDateBetweenOrderByInvoiceDateDesc(from, to)) sup += i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0;
        actual.put(SUPPLIERS, sup);

        Map<String, Double> plan = new HashMap<>();
        for (ExpenseBudget b : budgets.findAll()) plan.put(b.getCategory(), b.getMonthlyAmount().doubleValue());

        List<MoneyReportsDTO.BudgetRow> rows = new ArrayList<>();
        double totalBudget = 0, totalActual = 0, totalProjected = 0; boolean anyBudget = false;
        for (String c : CATEGORIES) {
            Double b = plan.get(c); double a = BudgetMath.r2(actual.getOrDefault(c, 0.0));
            double p = BudgetMath.projected(a, elapsed, daysIn);
            rows.add(row(c, label(c), b, a, p));
            if (b != null) { anyBudget = true; totalBudget += b; totalActual += a; totalProjected += p; }
        }
        // the total only covers categories that have a budget, so it compares like with like
        MoneyReportsDTO.BudgetRow total = row("TOTAL", "All budgeted categories", anyBudget ? totalBudget : null, BudgetMath.r2(totalActual), BudgetMath.r2(totalProjected));
        return MoneyReportsDTO.Budget.builder().year(year).month(month).daysElapsed(elapsed).daysInMonth(daysIn).rows(rows).total(total).build();
    }

    private static MoneyReportsDTO.BudgetRow row(String category, String label, Double budget, double actual, double projected) {
        return MoneyReportsDTO.BudgetRow.builder().category(category).label(label).budget(budget).actual(actual)
                .remaining(budget == null ? null : BudgetMath.r2(budget - actual)).usedPercent(BudgetMath.usedPercent(budget, actual))
                .projected(projected).status(BudgetMath.status(budget, actual, projected)).build();
    }

    /** Sets or clears (null or 0) the monthly budget of one category. */
    @Transactional
    public void set(String category, Double amount, String username) {
        String c = category == null ? "" : category.trim().toUpperCase();
        if (!CATEGORIES.contains(c)) throw new IllegalArgumentException("Unknown budget category '" + category + "'.");
        if (amount != null && (amount < 0 || amount.isNaN() || amount.isInfinite() || amount > 100_000_000)) throw new IllegalArgumentException("Budget must be an amount between 0 and 10 crore.");
        Optional<ExpenseBudget> existing = budgets.findByCategory(c);
        if (amount == null || amount <= 0) {
            existing.ifPresent(b -> { budgets.delete(b); auditLog.log("BUDGET_CLEAR", "EXPENSE_BUDGET", b.getId(), b.getMonthlyAmount().toPlainString(), c); });
            return;
        }
        ExpenseBudget b = existing.orElseGet(ExpenseBudget::new);
        String old = b.getMonthlyAmount() != null ? b.getMonthlyAmount().toPlainString() : null;
        b.setCategory(c);
        b.setMonthlyAmount(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP));
        b.setUpdatedBy(username);
        b.setUpdatedAt(LocalDateTime.now());
        b = budgets.save(b);
        auditLog.log("BUDGET_SET", "EXPENSE_BUDGET", b.getId(), old, c + "=" + b.getMonthlyAmount().toPlainString());
    }
}
