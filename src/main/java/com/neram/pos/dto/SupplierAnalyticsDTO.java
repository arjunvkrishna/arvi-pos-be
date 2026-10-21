package com.neram.pos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierAnalyticsDTO {
    private Double totalOutstanding;
    private Double totalPaidThisMonth;
    private Double totalInvoicedThisMonth;
    private Integer overdueInvoiceCount;
    private Integer activeSupplierCount;

    // For the outstanding payables chart (supplier name → outstanding amount)
    private List<Map<String, Object>> outstandingBySupplier;

    // Monthly payment trend (last 6 months)
    private List<Map<String, Object>> monthlyPaymentTrend;

    // Payment mode breakdown
    private Map<String, Double> paymentModeBreakdown;

    // Top suppliers by invoice value
    private List<Map<String, Object>> topSuppliersByInvoice;
}
