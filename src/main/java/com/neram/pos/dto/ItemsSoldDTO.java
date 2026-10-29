package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What was sold between two business dates: per dish for the whole range, and per day or per week with the dishes of each. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemsSoldDTO {
    private LocalDate from;
    private LocalDate to;
    /** DAY or WEEK */
    private String groupBy;
    private String query;
    private long totalQuantity;
    private BigDecimal totalRevenue;
    private int dishCount;
    private List<DishDTO> items;
    private List<PeriodDTO> periods;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DishDTO {
        private Long menuItemId;
        private String itemName;
        private String categoryName;
        private long quantity;
        private BigDecimal revenue;
        /** Number of bills the dish appeared on. */
        private long bills;
        /** Share of all quantity sold in the range, 0-100. */
        private double sharePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodDTO {
        /** The first day of the period inside the range (the day itself for DAY). */
        private LocalDate start;
        /** The last day of the period inside the range. */
        private LocalDate end;
        private long quantity;
        private BigDecimal revenue;
        private List<PeriodDishDTO> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodDishDTO {
        private String itemName;
        private long quantity;
        private BigDecimal revenue;
    }
}
