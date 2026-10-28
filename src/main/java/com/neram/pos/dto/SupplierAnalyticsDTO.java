package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierAnalyticsDTO {

    // --- Legacy / Overview Metrics (used by SupplierService.getAnalytics) ---
    private Double totalOutstanding;
    private Double totalPaidThisMonth;
    private Double totalInvoicedThisMonth;
    private Integer overdueInvoiceCount;
    private Integer activeSupplierCount;
    private List<Map<String, Object>> outstandingBySupplier;
    private List<Map<String, Object>> monthlyPaymentTrend;
    private Map<String, Double> paymentModeBreakdown;
    private List<Map<String, Object>> topSuppliersByInvoice;

    // --- Supplier Performance Analytics Metrics (Phase 2) ---
    private Long supplierId;
    private String supplierName;
    private Integer totalInvoices;
    private BigDecimal totalSpend;
    private BigDecimal totalPaid;
    private BigDecimal outstandingBalance;
    private Double onTimeDeliveryRatePercent;
    private Double averageLeadTimeDays;
    private Double fulfillmentRatePercent;
    private List<InvoiceSummaryDTO> recentInvoices;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceSummaryDTO {
        private Long id;
        private String invoiceNumber;
        private String invoiceDate;
        private BigDecimal totalAmount;
        private String status;
        private String expectedDeliveryDate;
        private String deliveryDate;
        private Boolean isOnTime;
    }
}
