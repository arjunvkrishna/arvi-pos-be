package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerDashboardDTO {
    private LocalDate businessDate;

    // Today metrics
    private BigDecimal grossSalesToday;
    private BigDecimal netSalesToday;
    private BigDecimal totalDiscountsToday;
    private BigDecimal totalTaxToday;
    private Long billsCountToday;
    private BigDecimal avgOrderValueToday;

    // Yesterday comparison
    private BigDecimal grossSalesYesterday;
    private BigDecimal netSalesYesterday;
    private Long billsCountYesterday;
    private BigDecimal avgOrderValueYesterday;

    // Percentage changes (positive or negative)
    private Double netSalesGrowthPercent;
    private Double billsCountGrowthPercent;

    // Tender breakdown today
    private Map<String, BigDecimal> tenderBreakdown;

    // Live table occupancy
    private Integer activeTablesOccupied;
    private Integer totalTables;
    private Double tableOccupancyPercent;

    // Low stock alerts
    private Integer lowStockItemsCount;
    private List<LowStockItemDTO> lowStockAlerts;

    // Top selling items today
    private List<TopSellingDishDTO> topSellingDishes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LowStockItemDTO {
        private Long id;
        private String name;
        private Double currentStock;
        private Double reorderLevel;
        private String unit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopSellingDishDTO {
        private String itemName;
        private Long quantitySold;
        private BigDecimal totalSales;
    }
}
