package com.neram.pos.service;

import com.neram.pos.dto.ComprehensiveAnalyticsDTO;
import com.neram.pos.dto.ComprehensiveAnalyticsDTO.*;
import com.neram.pos.dto.MovingDishDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final CafeExpenseRepository cafeExpenseRepository;
    private final SalaryRepository salaryRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SupplierInvoiceItemRepository supplierInvoiceItemRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final EmployeeRepository employeeRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final BillRefundRepository billRefundRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final BillPaymentRepository billPaymentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<MovingDishDTO> getWeeklyTopMovingDishes() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> results = billItemRepository.findTopMovingDishesSince(sevenDaysAgo);

        List<MovingDishDTO> dtoList = new ArrayList<>();
        for (Object[] row : results) {
            Long menuItemId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String itemName = (String) row[1];
            Long totalQty = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            Double totalRev = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

            dtoList.add(MovingDishDTO.builder()
                    .menuItemId(menuItemId)
                    .dishName(itemName)
                    .totalQuantity(totalQty)
                    .totalRevenue(Math.round(totalRev * 100.0) / 100.0)
                    .build());
        }
        return dtoList;
    }

    public Map<String, Object> getDashboardStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        BigDecimal todayRevenue = billRepository.calculateRevenueBetween(todayStart, todayEnd);

        LocalDate startMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endMonth = startMonth.withDayOfMonth(startMonth.lengthOfMonth());
        BigDecimal monthRevenue = billRepository.calculateRevenueBetween(startMonth.atStartOfDay(), endMonth.atTime(LocalTime.MAX));

        long totalBills = billRepository.countTotalBills();

        Map<String, Object> stats = new HashMap<>();
        stats.put("todayRevenue", todayRevenue != null ? todayRevenue.doubleValue() : 0.0);
        stats.put("monthRevenue", monthRevenue != null ? monthRevenue.doubleValue() : 0.0);
        stats.put("totalBills", totalBills);
        return stats;
    }

    /** The report period (name, dates and the previous period of equal length) for a period name or custom dates. */
    private record PeriodRange(String period, LocalDate start, LocalDate end, LocalDate prevStart, LocalDate prevEnd) {
    }

    private PeriodRange resolvePeriod(String periodParam, LocalDate customStart, LocalDate customEnd) {
        String period = (periodParam != null && !periodParam.isBlank()) ? periodParam.toUpperCase() : "THIS_MONTH";
        LocalDate today = LocalDate.now();

        LocalDate startDate;
        LocalDate endDate;
        LocalDate prevStartDate;
        LocalDate prevEndDate;

        switch (period) {
            case "TODAY":
                startDate = today;
                endDate = today;
                prevStartDate = today.minusDays(1);
                prevEndDate = today.minusDays(1);
                break;
            case "YESTERDAY":
                startDate = today.minusDays(1);
                endDate = today.minusDays(1);
                prevStartDate = today.minusDays(2);
                prevEndDate = today.minusDays(2);
                break;
            case "THIS_WEEK":
                startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                endDate = today;
                long weekDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
                prevEndDate = startDate.minusDays(1);
                prevStartDate = prevEndDate.minusDays(weekDays - 1);
                break;
            case "LAST_WEEK":
                LocalDate prevMonday = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                startDate = prevMonday;
                endDate = prevMonday.plusDays(6);
                prevStartDate = startDate.minusWeeks(1);
                prevEndDate = endDate.minusWeeks(1);
                break;
            case "LAST_MONTH":
                LocalDate lastMonthDay = today.minusMonths(1);
                startDate = lastMonthDay.withDayOfMonth(1);
                endDate = lastMonthDay.withDayOfMonth(lastMonthDay.lengthOfMonth());
                LocalDate monthBefore = lastMonthDay.minusMonths(1);
                prevStartDate = monthBefore.withDayOfMonth(1);
                prevEndDate = monthBefore.withDayOfMonth(monthBefore.lengthOfMonth());
                break;
            case "THIS_YEAR":
                startDate = today.withDayOfYear(1);
                endDate = today;
                prevStartDate = today.minusYears(1).withDayOfYear(1);
                prevEndDate = today.minusYears(1);
                break;
            case "LAST_YEAR":
                LocalDate lastYear = today.minusYears(1);
                startDate = lastYear.withDayOfYear(1);
                endDate = lastYear.withDayOfYear(lastYear.lengthOfYear());
                LocalDate twoYearsAgo = today.minusYears(2);
                prevStartDate = twoYearsAgo.withDayOfYear(1);
                prevEndDate = twoYearsAgo.withDayOfYear(twoYearsAgo.lengthOfYear());
                break;
            case "CUSTOM":
                startDate = (customStart != null) ? customStart : today.minusDays(30);
                endDate = (customEnd != null) ? customEnd : today;
                long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
                prevEndDate = startDate.minusDays(1);
                prevStartDate = prevEndDate.minusDays(days - 1);
                break;
            case "THIS_MONTH":
            default:
                period = "THIS_MONTH";
                startDate = today.withDayOfMonth(1);
                endDate = today;
                prevStartDate = today.minusMonths(1).withDayOfMonth(1);
                int dayOfMonth = Math.min(today.getDayOfMonth(), today.minusMonths(1).lengthOfMonth());
                prevEndDate = today.minusMonths(1).withDayOfMonth(dayOfMonth);
                break;
        }
        return new PeriodRange(period, startDate, endDate, prevStartDate, prevEndDate);
    }

    /**
     * Comprehensive Analytics for Centralized Management View
     */
    public ComprehensiveAnalyticsDTO getComprehensiveAnalytics(String periodParam, LocalDate customStart, LocalDate customEnd) {
        PeriodRange range = resolvePeriod(periodParam, customStart, customEnd);
        String period = range.period();
        LocalDate startDate = range.start();
        LocalDate endDate = range.end();
        LocalDate prevStartDate = range.prevStart();
        LocalDate prevEndDate = range.prevEnd();

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        LocalDateTime prevStartDateTime = prevStartDate.atStartOfDay();
        LocalDateTime prevEndDateTime = prevEndDate.atTime(LocalTime.MAX);

        // 1. Sales & Revenue
        List<Bill> currentBills = billRepository.findByCreatedAtBetweenAndStatusOrderByCreatedAtDesc(startDateTime, endDateTime, Bill.Status.PAID);
        double totalSales = currentBills.stream().mapToDouble(b -> b.getNetAmount() != null ? b.getNetAmount().doubleValue() : 0.0).sum();
        totalSales = round(totalSales);

        BigDecimal prevRevenueRaw = billRepository.calculateRevenueBetween(prevStartDateTime, prevEndDateTime);
        double prevTotalSales = round(prevRevenueRaw != null ? prevRevenueRaw.doubleValue() : 0.0);

        Double salesGrowthPercent = 0.0;
        if (prevTotalSales > 0.0) {
            salesGrowthPercent = round(((totalSales - prevTotalSales) / prevTotalSales) * 100.0);
        } else if (totalSales > 0.0) {
            salesGrowthPercent = 100.0;
        }

        long totalOrders = currentBills.size();
        double avgOrderValue = totalOrders > 0 ? round(totalSales / totalOrders) : 0.0;

        // 2. Expenses (Cafe Expenses)
        List<CafeExpense> expenses = cafeExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(startDate, endDate);
        double totalOperatingExpenses = expenses.stream().mapToDouble(e -> e.getAmount() != null ? e.getAmount().doubleValue() : 0.0).sum();
        totalOperatingExpenses = round(totalOperatingExpenses);

        // 3. Salaries Paid
        List<Salary> salaries = salaryRepository.findByPaidTrueAndPaidDateBetween(startDate, endDate);
        double totalSalariesPaid = salaries.stream().mapToDouble(s -> s.getNetSalary() != null ? s.getNetSalary().doubleValue() : 0.0).sum();
        totalSalariesPaid = round(totalSalariesPaid);

        // 4. Supplier Purchases & Payments
        List<SupplierInvoice> invoices = supplierInvoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(startDate, endDate);
        double totalSupplierPurchases = invoices.stream().mapToDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0).sum();
        totalSupplierPurchases = round(totalSupplierPurchases);

        List<SupplierPayment> supplierPayments = supplierPaymentRepository.findByPaymentDateBetweenOrderByPaymentDateDesc(startDate, endDate);
        double totalSupplierPayments = supplierPayments.stream().mapToDouble(sp -> sp.getAmount() != null ? sp.getAmount().doubleValue() : 0.0).sum();
        totalSupplierPayments = round(totalSupplierPayments);

        double totalSupplierOutstanding = round(totalSupplierPurchases - totalSupplierPayments);
        if (totalSupplierOutstanding < 0) totalSupplierOutstanding = 0.0;

        // 5. Total Expenses & Profit Calculations
        double supplierCost = (totalSupplierPayments > 0.0) ? totalSupplierPayments : totalSupplierPurchases;
        double totalExpenses = round(totalOperatingExpenses + totalSalariesPaid + supplierCost);
        double grossProfit = round(totalSales - supplierCost);
        double netProfit = round(grossProfit - (totalOperatingExpenses + totalSalariesPaid));

        // 6. Inventory Valuation & Alerts
        List<InventoryItem> inventoryItems = inventoryItemRepository.findByActiveTrue();
        double inventoryValuation = inventoryItems.stream()
                .mapToDouble(i -> (i.getQuantity() != null ? i.getQuantity() : 0.0) * (i.getCostPerUnit() != null ? i.getCostPerUnit().doubleValue() : 0.0))
                .sum();
        inventoryValuation = round(inventoryValuation);

        List<InventoryItemSummary> lowStockList = new ArrayList<>();
        List<InventoryItemSummary> outOfStockList = new ArrayList<>();

        long lowStockCount = 0;
        long outOfStockCount = 0;

        for (InventoryItem item : inventoryItems) {
            double qty = item.getQuantity() != null ? item.getQuantity() : 0.0;
            double minQty = item.getMinQuantity() != null ? item.getMinQuantity() : 0.0;

            if (qty <= 0.0) {
                outOfStockCount++;
                outOfStockList.add(toItemSummary(item));
            } else if (qty <= minQty) {
                lowStockCount++;
                lowStockList.add(toItemSummary(item));
            }
        }

        // 7. Time-series Sales & Cash Flow Trend (Day or Month Buckets)
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<TimePoint> salesTrend = new ArrayList<>();
        List<CashFlowPoint> cashFlowTrend = new ArrayList<>();

        if (totalDays <= 35) {
            // Daily grouping
            Map<LocalDate, List<Bill>> billsByDay = currentBills.stream()
                    .collect(Collectors.groupingBy(b -> b.getCreatedAt().toLocalDate()));
            Map<LocalDate, Double> expByDay = expenses.stream()
                    .collect(Collectors.groupingBy(CafeExpense::getExpenseDate, Collectors.summingDouble(e -> e.getAmount() != null ? e.getAmount().doubleValue() : 0.0)));
            Map<LocalDate, Double> salaryByDay = salaries.stream()
                    .filter(s -> s.getPaidDate() != null)
                    .collect(Collectors.groupingBy(Salary::getPaidDate, Collectors.summingDouble(s -> s.getNetSalary() != null ? s.getNetSalary().doubleValue() : 0.0)));
            Map<LocalDate, Double> purByDay = invoices.stream()
                    .filter(i -> i.getInvoiceDate() != null)
                    .collect(Collectors.groupingBy(SupplierInvoice::getInvoiceDate, Collectors.summingDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0)));

            Map<LocalDate, Double> suppPayByDay = supplierPayments.stream()
                    .filter(sp -> sp.getPaymentDate() != null)
                    .collect(Collectors.groupingBy(SupplierPayment::getPaymentDate, Collectors.summingDouble(sp -> sp.getAmount() != null ? sp.getAmount().doubleValue() : 0.0)));

            LocalDate cursor = startDate;
            while (!cursor.isAfter(endDate)) {
                String label = cursor.format(DATE_FMT);
                List<Bill> dayBills = billsByDay.getOrDefault(cursor, Collections.emptyList());
                double daySales = dayBills.stream().mapToDouble(b -> b.getNetAmount() != null ? b.getNetAmount().doubleValue() : 0.0).sum();
                daySales = round(daySales);
                long dayOrders = dayBills.size();
                double dayAvg = dayOrders > 0 ? round(daySales / dayOrders) : 0.0;

                salesTrend.add(TimePoint.builder()
                        .label(label)
                        .sales(daySales)
                        .orders(dayOrders)
                        .avgOrder(dayAvg)
                        .build());

                double daySupplierOut = (totalSupplierPayments > 0.0) ? suppPayByDay.getOrDefault(cursor, 0.0) : purByDay.getOrDefault(cursor, 0.0);
                double dayMoneyOut = expByDay.getOrDefault(cursor, 0.0) + salaryByDay.getOrDefault(cursor, 0.0) + daySupplierOut;
                dayMoneyOut = round(dayMoneyOut);
                double netCash = round(daySales - dayMoneyOut);

                cashFlowTrend.add(CashFlowPoint.builder()
                        .label(label)
                        .moneyIn(daySales)
                        .moneyOut(dayMoneyOut)
                        .netCash(netCash)
                        .build());

                cursor = cursor.plusDays(1);
            }
        } else {
            // Monthly grouping for longer ranges (e.g. THIS_YEAR, LAST_YEAR)
            Map<String, List<Bill>> billsByMonth = currentBills.stream()
                    .collect(Collectors.groupingBy(b -> b.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"))));
            Map<String, Double> expByMonth = expenses.stream()
                    .collect(Collectors.groupingBy(e -> e.getExpenseDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                            Collectors.summingDouble(e -> e.getAmount() != null ? e.getAmount().doubleValue() : 0.0)));
            Map<String, Double> salByMonth = salaries.stream()
                    .filter(s -> s.getPaidDate() != null)
                    .collect(Collectors.groupingBy(s -> s.getPaidDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                            Collectors.summingDouble(s -> s.getNetSalary() != null ? s.getNetSalary().doubleValue() : 0.0)));
            Map<String, Double> purByMonth = invoices.stream()
                    .filter(i -> i.getInvoiceDate() != null)
                    .collect(Collectors.groupingBy(i -> i.getInvoiceDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                            Collectors.summingDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0)));
            Map<String, Double> suppPayByMonth = supplierPayments.stream()
                    .filter(sp -> sp.getPaymentDate() != null)
                    .collect(Collectors.groupingBy(sp -> sp.getPaymentDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                            Collectors.summingDouble(sp -> sp.getAmount() != null ? sp.getAmount().doubleValue() : 0.0)));

            LocalDate cursor = startDate.withDayOfMonth(1);
            while (!cursor.isAfter(endDate)) {
                String monthKey = cursor.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                List<Bill> mBills = billsByMonth.getOrDefault(monthKey, Collections.emptyList());
                double mSales = round(mBills.stream().mapToDouble(b -> b.getNetAmount() != null ? b.getNetAmount().doubleValue() : 0.0).sum());
                long mOrders = mBills.size();
                double mAvg = mOrders > 0 ? round(mSales / mOrders) : 0.0;

                salesTrend.add(TimePoint.builder()
                        .label(monthKey)
                        .sales(mSales)
                        .orders(mOrders)
                        .avgOrder(mAvg)
                        .build());

                double mSupplierOut = (totalSupplierPayments > 0.0) ? suppPayByMonth.getOrDefault(monthKey, 0.0) : purByMonth.getOrDefault(monthKey, 0.0);
                double mMoneyOut = round(expByMonth.getOrDefault(monthKey, 0.0) + salByMonth.getOrDefault(monthKey, 0.0) + mSupplierOut);
                cashFlowTrend.add(CashFlowPoint.builder()
                        .label(monthKey)
                        .moneyIn(mSales)
                        .moneyOut(mMoneyOut)
                        .netCash(round(mSales - mMoneyOut))
                        .build());

                cursor = cursor.plusMonths(1);
            }
        }

        // 8. Expense Breakdown by Category
        List<CategoryBreakdown> expenseBreakdown = new ArrayList<>();
        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();

        for (CafeExpense exp : expenses) {
            String cat = exp.getType() != null ? exp.getType().name() : "MISC";
            categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + (exp.getAmount() != null ? exp.getAmount().doubleValue() : 0.0));
            categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0L) + 1);
        }

        if (totalSalariesPaid > 0.0) {
            categoryTotals.put("SALARY", totalSalariesPaid);
            categoryCounts.put("SALARY", (long) salaries.size());
        }

        double supplierExpenseCategory = (totalSupplierPayments > 0.0) ? totalSupplierPayments : totalSupplierPurchases;
        long supplierCount = (totalSupplierPayments > 0.0) ? (long) supplierPayments.size() : (long) invoices.size();
        if (supplierExpenseCategory > 0.0) {
            categoryTotals.put("SUPPLIERS", supplierExpenseCategory);
            categoryCounts.put("SUPPLIERS", supplierCount);
        }

        double grandTotalExpenses = totalExpenses > 0 ? totalExpenses : 1.0;
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            double amt = round(entry.getValue());
            long count = categoryCounts.getOrDefault(entry.getKey(), 0L);
            double pct = round((amt / grandTotalExpenses) * 100.0);
            expenseBreakdown.add(CategoryBreakdown.builder()
                    .category(entry.getKey())
                    .amount(amt)
                    .count(count)
                    .percentage(pct)
                    .build());
        }
        expenseBreakdown.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));

        // 9. Dish Sales Performance (Top & Low Selling Dishes)
        List<Object[]> dishResults = billItemRepository.findDishSalesBetween(startDateTime, endDateTime);
        List<DishPerformance> allDishes = new ArrayList<>();
        double grandDishRevenue = totalSales > 0 ? totalSales : 1.0;

        for (Object[] row : dishResults) {
            Long menuItemId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String itemName = (String) row[1];
            Long totalQty = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            Double totalRev = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            double pct = round((totalRev / grandDishRevenue) * 100.0);

            allDishes.add(DishPerformance.builder()
                    .menuItemId(menuItemId)
                    .dishName(itemName)
                    .quantity(totalQty)
                    .revenue(round(totalRev))
                    .percentageOfSales(pct)
                    .build());
        }

        List<DishPerformance> topSellingDishes = allDishes.stream().limit(10).collect(Collectors.toList());
        List<DishPerformance> lowSellingDishes = new ArrayList<>();
        if (allDishes.size() > 5) {
            lowSellingDishes = allDishes.subList(Math.max(allDishes.size() - 5, 0), allDishes.size());
            Collections.reverse(lowSellingDishes);
        }

        // 10. Product Cost Analytics (from Supplier Invoice Items)
        List<Object[]> productPurchaseResults = supplierInvoiceItemRepository.findPurchasedProductsBetween(startDate, endDate);
        List<PurchasedProductCost> topPurchasedProducts = new ArrayList<>();
        for (Object[] row : productPurchaseResults) {
            String pName = (String) row[0];
            String unit = (String) row[1];
            Double pQty = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            Double pCost = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            double avgRate = pQty > 0 ? round(pCost / pQty) : 0.0;

            topPurchasedProducts.add(PurchasedProductCost.builder()
                    .productName(pName)
                    .unit(unit != null ? unit : "unit")
                    .totalQuantity(round(pQty))
                    .totalSpend(round(pCost))
                    .avgUnitRate(avgRate)
                    .build());
        }

        // 11. Supplier Spending Breakdown
        Map<Long, List<SupplierInvoice>> invoicesBySupplier = invoices.stream()
                .filter(i -> i.getSupplierId() != null)
                .collect(Collectors.groupingBy(SupplierInvoice::getSupplierId));

        Map<Long, String> supplierNames = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));

        Set<Long> allSupplierIds = new LinkedHashSet<>(invoicesBySupplier.keySet());
        for (SupplierPayment sp : supplierPayments) {
            if (sp.getSupplierId() != null) {
                allSupplierIds.add(sp.getSupplierId());
            }
        }

        List<SupplierSpend> supplierSpending = new ArrayList<>();
        double grandSupplierSpend = totalSupplierPurchases > 0 ? totalSupplierPurchases : (totalSupplierPayments > 0 ? totalSupplierPayments : 1.0);
        for (Long sId : allSupplierIds) {
            String sName = supplierNames.getOrDefault(sId, "Supplier #" + sId);
            List<SupplierInvoice> sInvoices = invoicesBySupplier.getOrDefault(sId, Collections.emptyList());
            double spend = sInvoices.stream().mapToDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0).sum();
            spend = round(spend);
            long count = sInvoices.size();
            double baseAmount = totalSupplierPurchases > 0 ? spend : 0.0;
            double pct = round((baseAmount / grandSupplierSpend) * 100.0);

            double sPaid = supplierPayments.stream()
                    .filter(sp -> Objects.equals(sp.getSupplierId(), sId))
                    .mapToDouble(sp -> sp.getAmount() != null ? sp.getAmount().doubleValue() : 0.0)
                    .sum();
            sPaid = round(sPaid);
            double sOutstanding = round(spend - sPaid);
            if (sOutstanding < 0) sOutstanding = 0.0;

            supplierSpending.add(SupplierSpend.builder()
                    .supplierId(sId)
                    .supplierName(sName)
                    .invoiceCount(count)
                    .totalSpend(spend)
                    .amountPaid(sPaid)
                    .outstandingBalance(sOutstanding)
                    .percentage(pct)
                    .build());
        }
        supplierSpending.sort((a, b) -> Double.compare(b.getTotalSpend() + b.getAmountPaid(), a.getTotalSpend() + a.getAmountPaid()));

        // 12. "Every Penny" Traceable Source Records (Drill-Down)
        Map<Long, String> employeeNames = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName, (a, b) -> a));

        Map<Long, String> invoiceNumbers = invoices.stream()
                .collect(Collectors.toMap(SupplierInvoice::getId, SupplierInvoice::getInvoiceNumber, (a, b) -> a));

        List<BillRecord> billRecords = currentBills.stream().limit(100).map(b -> BillRecord.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .tableNumber(b.getTableNumber())
                .customerName(b.getCustomerName())
                .netAmount(b.getNetAmount() != null ? b.getNetAmount().doubleValue() : 0.0)
                .paymentMode(b.getPaymentSummary() != null ? b.getPaymentSummary() : (b.getPaymentMode() != null ? b.getPaymentMode().name() : "CASH"))
                .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "")
                .build()).collect(Collectors.toList());

        List<ExpenseRecord> expenseRecords = expenses.stream().limit(100).map(e -> ExpenseRecord.builder()
                .id(e.getId())
                .type(e.getType() != null ? e.getType().name() : "MISC")
                .description(e.getDescription())
                .amount(e.getAmount() != null ? e.getAmount().doubleValue() : 0.0)
                .expenseDate(e.getExpenseDate() != null ? e.getExpenseDate().toString() : "")
                .paidBy(e.getPaidBy())
                .receiptRef(e.getReceiptRef())
                .build()).collect(Collectors.toList());

        List<SupplierInvoiceRecord> purchaseRecords = invoices.stream().limit(100).map(i -> SupplierInvoiceRecord.builder()
                .id(i.getId())
                .invoiceNumber(i.getInvoiceNumber())
                .supplierName(supplierNames.getOrDefault(i.getSupplierId(), "Supplier #" + i.getSupplierId()))
                .invoiceDate(i.getInvoiceDate() != null ? i.getInvoiceDate().toString() : "")
                .subtotal(i.getSubtotal() != null ? i.getSubtotal().doubleValue() : 0.0)
                .gstAmount(i.getGstAmount() != null ? i.getGstAmount().doubleValue() : 0.0)
                .totalAmount(i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0)
                .status(i.getStatus() != null ? i.getStatus().name() : "UNPAID")
                .build()).collect(Collectors.toList());

        List<SupplierPaymentRecord> supplierPaymentRecords = supplierPayments.stream().limit(100).map(sp -> SupplierPaymentRecord.builder()
                .id(sp.getId())
                .paymentDate(sp.getPaymentDate() != null ? sp.getPaymentDate().toString() : "")
                .supplierId(sp.getSupplierId())
                .supplierName(supplierNames.getOrDefault(sp.getSupplierId(), "Supplier #" + sp.getSupplierId()))
                .invoiceId(sp.getInvoiceId())
                .invoiceNumber(sp.getInvoiceId() != null ? invoiceNumbers.getOrDefault(sp.getInvoiceId(), "INV-" + sp.getInvoiceId()) : "-")
                .amount(sp.getAmount() != null ? sp.getAmount().doubleValue() : 0.0)
                .paymentMode(sp.getPaymentMode() != null ? sp.getPaymentMode().name() : "CASH")
                .referenceNumber(sp.getReferenceNumber())
                .notes(sp.getNotes())
                .build()).collect(Collectors.toList());

        List<SalaryRecord> salaryRecords = salaries.stream().limit(100).map(s -> SalaryRecord.builder()
                .id(s.getId())
                .employeeName(employeeNames.getOrDefault(s.getEmployeeId(), "Staff #" + s.getEmployeeId()))
                .month(s.getMonth())
                .year(s.getYear())
                .netSalary(s.getNetSalary() != null ? s.getNetSalary().doubleValue() : 0.0)
                .paymentDate(s.getPaidDate() != null ? s.getPaidDate().toString() : "")
                .paymentMode(s.getPaymentMode())
                .status(s.getPaid() != null && s.getPaid() ? "PAID" : "PENDING")
                .build()).collect(Collectors.toList());

        SourceRecords drillDown = SourceRecords.builder()
                .sales(billRecords)
                .expenses(expenseRecords)
                .purchases(purchaseRecords)
                .supplierPayments(supplierPaymentRecords)
                .salaries(salaryRecords)
                .build();

        // Assemble DTO
        return ComprehensiveAnalyticsDTO.builder()
                .periodInfo(PeriodInfo.builder()
                        .period(period)
                        .startDate(startDate.toString())
                        .endDate(endDate.toString())
                        .prevStartDate(prevStartDate.toString())
                        .prevEndDate(prevEndDate.toString())
                        .build())
                .kpis(KpiSummary.builder()
                        .totalSales(totalSales)
                        .prevTotalSales(prevTotalSales)
                        .salesGrowthPercent(salesGrowthPercent)
                        .totalOrders(totalOrders)
                        .avgOrderValue(avgOrderValue)
                        .totalOperatingExpenses(totalOperatingExpenses)
                        .totalSalariesPaid(totalSalariesPaid)
                        .totalSupplierPurchases(totalSupplierPurchases)
                        .totalSupplierPayments(totalSupplierPayments)
                        .totalSupplierOutstanding(totalSupplierOutstanding)
                        .totalExpenses(totalExpenses)
                        .grossProfit(grossProfit)
                        .netProfit(netProfit)
                        .inventoryValuation(inventoryValuation)
                        .lowStockCount(lowStockCount)
                        .outOfStockCount(outOfStockCount)
                        .build())
                .salesTrend(salesTrend)
                .cashFlowTrend(cashFlowTrend)
                .expenseBreakdown(expenseBreakdown)
                .topSellingDishes(topSellingDishes)
                .lowSellingDishes(lowSellingDishes)
                .topPurchasedProducts(topPurchasedProducts)
                .supplierSpending(supplierSpending)
                .inventory(InventoryAnalytics.builder()
                        .totalValuation(inventoryValuation)
                        .totalActiveItems((long) inventoryItems.size())
                        .lowStockCount(lowStockCount)
                        .outOfStockCount(outOfStockCount)
                        .lowStockItems(lowStockList)
                        .outOfStockItems(outOfStockList)
                        .build())
                .drillDown(drillDown)
                .build();
    }

    /**
     * One page of a report table for a period, newest first. sections: sales (paid bills), expenses, purchases (supplier
     * invoices), supplierPayments, salaries (paid payroll), supplierSpending and purchasedProducts (the two ranked summaries).
     * The first five are paged in the database; the two summaries are computed lists that are sliced.
     */
    public org.springframework.data.domain.Page<?> getDrillDownPage(String section, String periodParam, LocalDate customStart, LocalDate customEnd,
                                                                   org.springframework.data.domain.Pageable pageable) {
        PeriodRange range = resolvePeriod(periodParam, customStart, customEnd);
        LocalDate start = range.start();
        LocalDate end = range.end();
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);
        // The queries sort themselves; only the request's page and size are used.
        org.springframework.data.domain.Pageable plain = (pageable == null || pageable.isUnpaged()) ? org.springframework.data.domain.Pageable.unpaged()
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        String s = section == null ? "" : section.trim();

        switch (s) {
            case "sales":
                return billRepository.findByCreatedAtBetweenAndStatusOrderByCreatedAtDescIdDesc(startTime, endTime, Bill.Status.PAID, plain)
                        .map(b -> BillRecord.builder()
                                .id(b.getId())
                                .billNumber(b.getBillNumber())
                                .tableNumber(b.getTableNumber())
                                .customerName(b.getCustomerName())
                                .netAmount(b.getNetAmount() != null ? b.getNetAmount().doubleValue() : 0.0)
                                .paymentMode(b.getPaymentSummary() != null ? b.getPaymentSummary() : (b.getPaymentMode() != null ? b.getPaymentMode().name() : "CASH"))
                                .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "")
                                .build());
            case "expenses":
                return cafeExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(start, end, plain)
                        .map(e -> ExpenseRecord.builder()
                                .id(e.getId())
                                .type(e.getType() != null ? e.getType().name() : "MISC")
                                .description(e.getDescription())
                                .amount(e.getAmount() != null ? e.getAmount().doubleValue() : 0.0)
                                .expenseDate(e.getExpenseDate() != null ? e.getExpenseDate().toString() : "")
                                .paidBy(e.getPaidBy())
                                .receiptRef(e.getReceiptRef())
                                .build());
            case "purchases": {
                Map<Long, String> supplierNames = supplierNameMap();
                return supplierInvoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDescIdDesc(start, end, plain)
                        .map(i -> SupplierInvoiceRecord.builder()
                                .id(i.getId())
                                .invoiceNumber(i.getInvoiceNumber())
                                .supplierName(supplierNames.getOrDefault(i.getSupplierId(), "Supplier #" + i.getSupplierId()))
                                .invoiceDate(i.getInvoiceDate() != null ? i.getInvoiceDate().toString() : "")
                                .subtotal(i.getSubtotal() != null ? i.getSubtotal().doubleValue() : 0.0)
                                .gstAmount(i.getGstAmount() != null ? i.getGstAmount().doubleValue() : 0.0)
                                .totalAmount(i.getTotalAmount() != null ? i.getTotalAmount().doubleValue() : 0.0)
                                .status(i.getStatus() != null ? i.getStatus().name() : "UNPAID")
                                .build());
            }
            case "supplierPayments": {
                Map<Long, String> supplierNames = supplierNameMap();
                return supplierPaymentRepository.findByPaymentDateBetweenOrderByPaymentDateDescIdDesc(start, end, plain)
                        .map(sp -> SupplierPaymentRecord.builder()
                                .id(sp.getId())
                                .paymentDate(sp.getPaymentDate() != null ? sp.getPaymentDate().toString() : "")
                                .supplierId(sp.getSupplierId())
                                .supplierName(supplierNames.getOrDefault(sp.getSupplierId(), "Supplier #" + sp.getSupplierId()))
                                .invoiceId(sp.getInvoiceId())
                                .invoiceNumber(sp.getInvoiceId() != null
                                        ? supplierInvoiceRepository.findById(sp.getInvoiceId()).map(SupplierInvoice::getInvoiceNumber).orElse("INV-" + sp.getInvoiceId())
                                        : "-")
                                .amount(sp.getAmount() != null ? sp.getAmount().doubleValue() : 0.0)
                                .paymentMode(sp.getPaymentMode() != null ? sp.getPaymentMode().name() : "CASH")
                                .referenceNumber(sp.getReferenceNumber())
                                .notes(sp.getNotes())
                                .build());
            }
            case "salaries": {
                Map<Long, String> employeeNames = employeeRepository.findAll().stream()
                        .collect(Collectors.toMap(Employee::getId, Employee::getName, (a, b) -> a));
                return salaryRepository.findByPaidTrueAndPaidDateBetweenOrderByPaidDateDescIdDesc(start, end, plain)
                        .map(sal -> SalaryRecord.builder()
                                .id(sal.getId())
                                .employeeName(employeeNames.getOrDefault(sal.getEmployeeId(), "Staff #" + sal.getEmployeeId()))
                                .month(sal.getMonth())
                                .year(sal.getYear())
                                .netSalary(sal.getNetSalary() != null ? sal.getNetSalary().doubleValue() : 0.0)
                                .paymentDate(sal.getPaidDate() != null ? sal.getPaidDate().toString() : "")
                                .paymentMode(sal.getPaymentMode())
                                .status(sal.getPaid() != null && sal.getPaid() ? "PAID" : "PENDING")
                                .build());
            }
            case "supplierSpending":
                return com.neram.pos.util.PagedResponse.slice(getComprehensiveAnalytics(periodParam, customStart, customEnd).getSupplierSpending(), plain);
            case "purchasedProducts":
                return com.neram.pos.util.PagedResponse.slice(getComprehensiveAnalytics(periodParam, customStart, customEnd).getTopPurchasedProducts(), plain);
            default:
                throw new IllegalArgumentException("Unknown report section: " + section);
        }
    }

    private Map<Long, String> supplierNameMap() {
        return supplierRepository.findAll().stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));
    }

    private InventoryItemSummary toItemSummary(InventoryItem item) {
        return InventoryItemSummary.builder()
                .id(item.getId())
                .name(item.getName())
                .currentQuantity(round(item.getQuantity() != null ? item.getQuantity() : 0.0))
                .minQuantity(round(item.getMinQuantity() != null ? item.getMinQuantity() : 0.0))
                .unit(item.getUnit())
                .costPerUnit(item.getCostPerUnit() != null ? item.getCostPerUnit().doubleValue() : 0.0)
                .build();
    }

    public com.neram.pos.dto.ComprehensiveProfitReportDTO getComprehensiveProfitReport(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        // 1. Revenue: paid sales, less discounts and successful refunds, excluding indirect tax collected
        List<Bill.Status> paidStatuses = List.of(Bill.Status.PAID, Bill.Status.PARTIALLY_REFUNDED, Bill.Status.REFUNDED);
        List<Bill> paidBills = billRepository.findByCreatedAtBetweenAndStatusIn(startDateTime, endDateTime, paidStatuses);

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal totalDiscounts = BigDecimal.ZERO;
        BigDecimal taxCollected = BigDecimal.ZERO;

        for (Bill b : paidBills) {
            if (b.getSubtotal() != null) grossSales = grossSales.add(b.getSubtotal());
            if (b.getDiscountAmount() != null) totalDiscounts = totalDiscounts.add(b.getDiscountAmount());
            if (b.getTaxAmount() != null) taxCollected = taxCollected.add(b.getTaxAmount());
        }

        // Sum successful refunds in this period
        List<BillRefund> refunds = billRefundRepository.findByRefundedAtBetween(startDateTime, endDateTime);
        BigDecimal totalRefunds = refunds.stream()
                .filter(r -> r.getStatus() == BillRefund.RefundStatus.SUCCESS)
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Net sales: grossSales - totalDiscounts - totalRefunds (subtotal already excludes tax)
        BigDecimal netSales = grossSales.subtract(totalDiscounts).subtract(totalRefunds).setScale(2, RoundingMode.HALF_UP);

        // 2. COGS: Cost of ingredients actually consumed for fulfilled/sold food and waste
        List<InventoryTransaction> transactions = inventoryTransactionRepository.findByCreatedAtBetween(startDateTime, endDateTime);
        BigDecimal ingredientConsumptionCost = BigDecimal.ZERO;
        BigDecimal wasteCost = BigDecimal.ZERO;

        for (InventoryTransaction t : transactions) {
            BigDecimal qty = t.getQuantity() != null ? BigDecimal.valueOf(Math.abs(t.getQuantity())) : BigDecimal.ZERO;
            BigDecimal unitPrice = t.getUnitPrice() != null ? t.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal lineCost = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            if ("SALE_CONSUMPTION".equals(t.getTransactionType())) {
                ingredientConsumptionCost = ingredientConsumptionCost.add(lineCost);
            } else if ("WASTE".equals(t.getTransactionType())) {
                wasteCost = wasteCost.add(lineCost);
            } else if ("REFUND_REVERSAL".equals(t.getTransactionType())) {
                ingredientConsumptionCost = ingredientConsumptionCost.subtract(lineCost);
            }
        }
        ingredientConsumptionCost = ingredientConsumptionCost.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        wasteCost = wasteCost.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCogs = ingredientConsumptionCost.add(wasteCost).setScale(2, RoundingMode.HALF_UP);

        // 3. Gross Profit = Net Sales - COGS
        BigDecimal grossProfit = netSales.subtract(totalCogs).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossProfitMargin = netSales.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(netSales, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 4. Operating Expenses
        List<CafeExpense> expenses = cafeExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(start, end);
        BigDecimal operatingExpenses = expenses.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. Payroll Expense: Salary allocated to the month worked
        List<Salary> allSalaries = salaryRepository.findAll();
        BigDecimal payrollExpense = BigDecimal.ZERO;
        LocalDate startMonthDate = start.withDayOfMonth(1);
        LocalDate endMonthDate = end.withDayOfMonth(1);

        for (Salary s : allSalaries) {
            if (s.getYear() != null && s.getMonth() != null) {
                LocalDate salMonth = LocalDate.of(s.getYear(), s.getMonth(), 1);
                if (!salMonth.isBefore(startMonthDate) && !salMonth.isAfter(endMonthDate)) {
                    if (s.getNetSalary() != null) {
                        payrollExpense = payrollExpense.add(s.getNetSalary());
                    }
                }
            }
        }
        payrollExpense = payrollExpense.setScale(2, RoundingMode.HALF_UP);

        // 6. Net Operating Profit = Gross Profit - Operating Expenses - Payroll
        BigDecimal netOperatingProfit = grossProfit.subtract(operatingExpenses).subtract(payrollExpense).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netOperatingProfitMargin = netSales.compareTo(BigDecimal.ZERO) > 0
                ? netOperatingProfit.multiply(BigDecimal.valueOf(100)).divide(netSales, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 7. Cash Flow Summary (Money Received - Money Paid)
        Map<Long, BigDecimal> cashPartOfSplit = new HashMap<>();
        List<Long> splitIds = paidBills.stream().filter(b -> b.getPaymentSummary() != null).map(Bill::getId).collect(Collectors.toList());
        if (!splitIds.isEmpty()) for (BillPayment p : billPaymentRepository.findByBillIdIn(splitIds)) if ("CASH".equals(p.getMode())) cashPartOfSplit.merge(p.getBillId(), p.getAmount(), BigDecimal::add);
        BigDecimal cashSales = paidBills.stream()
                .map(b -> b.getPaymentSummary() != null ? cashPartOfSplit.getOrDefault(b.getId(), BigDecimal.ZERO)
                        : (b.getPaymentMode() == Bill.PaymentMode.CASH ? (b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO) : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CashMovement> movements = cashMovementRepository.findByCreatedAtBetween(startDateTime, endDateTime);
        BigDecimal cashInMovements = movements.stream()
                .filter(m -> m.getMovementType() == CashMovement.MovementType.CASH_IN)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashOutMovements = movements.stream()
                .filter(m -> m.getMovementType() == CashMovement.MovementType.CASH_OUT)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashRefunds = refunds.stream()
                .filter(r -> r.getStatus() == BillRefund.RefundStatus.SUCCESS && r.getPaymentMode() == Bill.PaymentMode.CASH)
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashIn = cashSales.add(cashInMovements).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCashOut = cashOutMovements.add(cashRefunds).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netCashFlow = totalCashIn.subtract(totalCashOut).setScale(2, RoundingMode.HALF_UP);

        return com.neram.pos.dto.ComprehensiveProfitReportDTO.builder()
                .startDate(start)
                .endDate(end)
                .grossSales(grossSales.setScale(2, RoundingMode.HALF_UP))
                .totalDiscounts(totalDiscounts.setScale(2, RoundingMode.HALF_UP))
                .totalRefunds(totalRefunds.setScale(2, RoundingMode.HALF_UP))
                .taxCollected(taxCollected.setScale(2, RoundingMode.HALF_UP))
                .netSales(netSales)
                .ingredientConsumptionCost(ingredientConsumptionCost)
                .wasteCost(wasteCost)
                .totalCogs(totalCogs)
                .grossProfit(grossProfit)
                .grossProfitMarginPercent(grossProfitMargin)
                .operatingExpenses(operatingExpenses)
                .payrollExpense(payrollExpense)
                .netOperatingProfit(netOperatingProfit)
                .netOperatingProfitMarginPercent(netOperatingProfitMargin)
                .totalCashIn(totalCashIn)
                .totalCashOut(totalCashOut)
                .netCashFlow(netCashFlow)
                .build();
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
