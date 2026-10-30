package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AccountingDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfitAndLossReport {
        private String period;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal grossSales;
        private BigDecimal gstCollected;
        private BigDecimal tipsCollected;
        private BigDecimal netSalesRevenue; // Payments - GST - Tips
        private BigDecimal cogs; // Cost of Goods Sold
        private BigDecimal loyaltyPointsRedeemed;
        private BigDecimal grossProfit; // netSalesRevenue - cogs - loyaltyPointsRedeemed
        private BigDecimal operatingExpenses;
        private BigDecimal salariesPaid;
        private BigDecimal netProfit; // grossProfit - operatingExpenses - salariesPaid
        private List<ExpenseCategoryRow> expenseBreakdown;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExpenseCategoryRow {
        private String category;
        private BigDecimal amount;
        private Double percentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BalanceSheetReport {
        private LocalDate asOfDate;
        private Assets assets;
        private Liabilities liabilities;
        private BigDecimal netWorthOrEquity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Assets {
        private BigDecimal cashInDrawerAndBank;
        private BigDecimal inventoryValuationAtCost;
        private BigDecimal totalAssets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Liabilities {
        private BigDecimal unpaidSupplierInvoices;
        private BigDecimal unpaidTips;
        private BigDecimal totalLiabilities;
    }
}
