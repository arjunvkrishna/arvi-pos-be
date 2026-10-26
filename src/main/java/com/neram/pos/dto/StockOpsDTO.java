package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Reorder list, cost alerts and stock counts. */
public final class StockOpsDTO {
    private StockOpsDTO() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReorderRow {
        private Long itemId; private String name; private String unit; private String category;
        private Double quantity; private Double minQuantity;
        /** OUT, BELOW_MIN or RUNS_OUT_SOON */
        private String reason;
        private Double avgDailyUse; private Double daysLeft;
        private Double suggestedUnits; private Double suggestedPacks; private String packName; private Double packSize;
        private Long supplierId; private String supplier;
        private Double lastUnitCost; private Double estimatedCost;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Reorder {
        private int usageDays; private int coverDays;
        private List<ReorderRow> items; private double estimatedTotal;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CostChange {
        private Long itemId; private String name; private String unit; private String supplier;
        private Double previousCost; private Double latestCost; private Double changePercent;
        /** UP or DOWN */
        private String direction;
        private LocalDate previousDate; private LocalDate latestDate;
        private List<String> dishes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DishMargin {
        private Long menuItemId; private String name; private Double price; private Double cost; private Double profit; private Double marginPercent;
        /** NEGATIVE (sells below cost), LOW (under the floor) */
        private String status;
        private List<String> affectedBy;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CostAlerts {
        private double changeThresholdPercent; private double marginFloorPercent;
        private List<CostChange> changes; private List<DishMargin> lowMargin;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CostAlertSettings {
        private Double changeThresholdPercent; private Double marginFloorPercent;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CountLine {
        private Long id; private Long itemId; private String name; private String unit;
        private Double bookQty; private Double countedQty; private Double variance; private Double varianceValue; private Double costPerUnit;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Count {
        private Long id; private String status; private String scope; private String note;
        private String createdBy; private LocalDateTime createdAt; private String appliedBy; private LocalDateTime appliedAt;
        private int itemCount; private int countedCount; private int differenceCount;
        /** money value of all differences (negative = stock is missing) */
        private double varianceValue;
        private List<CountLine> lines;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StartCountRequest { private String category; private String note; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SaveLines { private List<LineEntry> lines; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LineEntry { private Long lineId; private Double countedQty; }
}
