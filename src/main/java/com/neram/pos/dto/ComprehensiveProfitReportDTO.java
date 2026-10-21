package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprehensiveProfitReportDTO {
    private LocalDate startDate;
    private LocalDate endDate;

    // 1. Revenue
    private BigDecimal grossSales;
    private BigDecimal totalDiscounts;
    private BigDecimal totalRefunds;
    private BigDecimal taxCollected;
    private BigDecimal netSales;

    // 2. Cost of Goods Sold (COGS)
    private BigDecimal ingredientConsumptionCost;
    private BigDecimal wasteCost;
    private BigDecimal totalCogs;

    // 3. Gross Profit
    private BigDecimal grossProfit;
    private BigDecimal grossProfitMarginPercent;

    // 4. Operating Expenses
    private BigDecimal operatingExpenses;

    // 5. Payroll
    private BigDecimal payrollExpense;

    // 6. Net Operating Profit
    private BigDecimal netOperatingProfit;
    private BigDecimal netOperatingProfitMarginPercent;

    // 7. Cash Flow Summary (Separate from accounting period profit)
    private BigDecimal totalCashIn;
    private BigDecimal totalCashOut;
    private BigDecimal netCashFlow;
}
