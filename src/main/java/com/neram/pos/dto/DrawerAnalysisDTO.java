package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawerAnalysisDTO {

    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long filterCashierId;
    private String filterRegisterId;
    private String filterStatus;

    private AnomalyThresholdsDTO thresholds;
    private DrawerSummaryDTO summary;
    private List<ShiftReconciliationItemDTO> shifts;
    private List<DailyDrawerSummaryDTO> dailySummaries;
    private List<MonthlyDrawerSummaryDTO> monthlySummaries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnomalyThresholdsDTO {
        private BigDecimal varianceThreshold;
        private BigDecimal largeMovementThreshold;
        private Boolean flagMissingCount;
        private Boolean flagTransfersWithoutRef;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DrawerSummaryDTO {
        private int totalShifts;
        private int openShifts;
        private int closedShifts;

        private BigDecimal totalOpeningFloat;
        private BigDecimal totalCashSales;
        private BigDecimal totalUpiSales;
        private BigDecimal totalCardSales;
        private BigDecimal totalSales;

        private BigDecimal totalCashRefunds;
        private BigDecimal totalUpiRefunds;
        private BigDecimal totalCardRefunds;
        private BigDecimal totalRefunds;

        private BigDecimal totalCashAdded;
        private BigDecimal totalCashPaidOut; // Petty cash & other payouts
        private BigDecimal totalTransfersToBankSafe;
        private BigDecimal totalTransfersFromBankSafe;

        private BigDecimal totalExpectedCash;
        private BigDecimal totalCountedCash;
        private BigDecimal netVariance;

        private int shortageCount;
        private BigDecimal totalShortageAmount;
        private int surplusCount;
        private BigDecimal totalSurplusAmount;
        private int balancedCount;
        private int anomalousShiftsCount;

        private FinancialDistinctionsDTO distinctions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FinancialDistinctionsDTO {
        private BigDecimal drawerCashFlow;     // Net cash through drawer
        private BigDecimal bankSafeTransfers;   // Funds relocated to bank or vault
        private BigDecimal businessExpenses;    // Operating expenses from CafeExpense
        private BigDecimal accountingProfit;     // Total Revenue - Business Expenses
        private String explanation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftReconciliationItemDTO {
        private CashierShiftDTO.ShiftResponse shift;
        private Long durationMinutes;
        private String reconciliationEquation;
        private List<AnomalyFlagDTO> anomalyFlags;
        private int billCount;
        private int refundCount;
        private int movementsCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnomalyFlagDTO {
        private String flagType;
        private String severity; // HIGH, MEDIUM, WARNING
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyDrawerSummaryDTO {
        private LocalDate date;
        private int shiftCount;
        private BigDecimal openingTotal;
        private BigDecimal cashSales;
        private BigDecimal upiSales;
        private BigDecimal cardSales;
        private BigDecimal cashRefunds;
        private BigDecimal cashIn;
        private BigDecimal cashOut;
        private BigDecimal transfersOut;
        private BigDecimal transfersIn;
        private BigDecimal expectedTotal;
        private BigDecimal countedTotal;
        private BigDecimal netVariance;
        private int shortageCount;
        private int surplusCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyDrawerSummaryDTO {
        private String monthYear; // "2026-10"
        private int shiftCount;
        private BigDecimal openingTotal;
        private BigDecimal cashSales;
        private BigDecimal expectedTotal;
        private BigDecimal countedTotal;
        private BigDecimal netVariance;
        private int shortageCount;
        private int surplusCount;
    }
}
