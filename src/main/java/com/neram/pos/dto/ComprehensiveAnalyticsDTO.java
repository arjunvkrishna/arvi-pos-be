package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprehensiveAnalyticsDTO {

    private PeriodInfo periodInfo;
    private KpiSummary kpis;
    private List<TimePoint> salesTrend;
    private List<CashFlowPoint> cashFlowTrend;
    private List<CategoryBreakdown> expenseBreakdown;
    private List<DishPerformance> topSellingDishes;
    private List<DishPerformance> lowSellingDishes;
    private List<PurchasedProductCost> topPurchasedProducts;
    private List<SupplierSpend> supplierSpending;
    private InventoryAnalytics inventory;
    private SourceRecords drillDown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodInfo {
        private String period; // TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, CUSTOM
        private String startDate;
        private String endDate;
        private String prevStartDate;
        private String prevEndDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiSummary {
        private Double totalSales;
        private Double prevTotalSales;
        private Double salesGrowthPercent;

        private Long totalOrders;
        private Double avgOrderValue;

        private Double totalOperatingExpenses;
        private Double totalSalariesPaid;
        private Double totalSupplierPurchases;
        private Double totalSupplierPayments;
        private Double totalSupplierOutstanding;
        private Double totalExpenses; // operating + salaries + purchases

        private Double grossProfit; // totalSales - product costs / supplier purchases
        private Double netProfit;   // grossProfit - (operating expenses + salaries)

        private Double inventoryValuation;
        private Long lowStockCount;
        private Long outOfStockCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePoint {
        private String label; // "YYYY-MM-DD" or "Month"
        private Double sales;
        private Long orders;
        private Double avgOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashFlowPoint {
        private String label;
        private Double moneyIn;   // Sales
        private Double moneyOut;  // Expenses + Purchases + Salaries
        private Double netCash;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private Double amount;
        private Long count;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DishPerformance {
        private Long menuItemId;
        private String dishName;
        private Long quantity;
        private Double revenue;
        private Double percentageOfSales;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchasedProductCost {
        private String productName;
        private String unit;
        private Double totalQuantity;
        private Double totalSpend;
        private Double avgUnitRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierSpend {
        private Long supplierId;
        private String supplierName;
        private Long invoiceCount;
        private Double totalSpend;
        private Double amountPaid;
        private Double outstandingBalance;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAnalytics {
        private Double totalValuation;
        private Long totalActiveItems;
        private Long lowStockCount;
        private Long outOfStockCount;
        private List<InventoryItemSummary> lowStockItems;
        private List<InventoryItemSummary> outOfStockItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryItemSummary {
        private Long id;
        private String name;
        private Double currentQuantity;
        private Double minQuantity;
        private String unit;
        private Double costPerUnit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRecords {
        private List<BillRecord> sales;
        private List<ExpenseRecord> expenses;
        private List<SupplierInvoiceRecord> purchases;
        private List<SupplierPaymentRecord> supplierPayments;
        private List<SalaryRecord> salaries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillRecord {
        private Long id;
        private String billNumber;
        private String tableNumber;
        private String customerName;
        private Double netAmount;
        private String paymentMode;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseRecord {
        private Long id;
        private String type;
        private String description;
        private Double amount;
        private String expenseDate;
        private String paidBy;
        private String receiptRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierInvoiceRecord {
        private Long id;
        private String invoiceNumber;
        private String supplierName;
        private String invoiceDate;
        private Double subtotal;
        private Double gstAmount;
        private Double totalAmount;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierPaymentRecord {
        private Long id;
        private String paymentDate;
        private Long supplierId;
        private String supplierName;
        private Long invoiceId;
        private String invoiceNumber;
        private Double amount;
        private String paymentMode;
        private String referenceNumber;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalaryRecord {
        private Long id;
        private String employeeName;
        private Integer month;
        private Integer year;
        private Double netSalary;
        private String paymentDate;
        private String paymentMode;
        private String status;
    }
}
