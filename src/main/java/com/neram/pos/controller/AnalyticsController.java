package com.neram.pos.controller;

import com.neram.pos.dto.MovingDishDTO;
import com.neram.pos.service.AnalyticsExportService;
import com.neram.pos.service.AnalyticsService;
import com.neram.pos.service.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsExportService analyticsExportService;
    private final InsightsService insightsService;

    @GetMapping("/moving-dishes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<MovingDishDTO>> getWeeklyMovingDishes() {
        return ResponseEntity.ok(analyticsService.getWeeklyTopMovingDishes());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(analyticsService.getDashboardStats());
    }

    @GetMapping("/comprehensive")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<com.neram.pos.dto.ComprehensiveAnalyticsDTO> getComprehensiveAnalytics(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String period,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            // records=false leaves out the report tables (drill-down, supplier spend, purchased products): the screen
            // loads those one page at a time from /drill-down
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "true") boolean records) {
        com.neram.pos.dto.ComprehensiveAnalyticsDTO report = analyticsService.getComprehensiveAnalytics(period, startDate, endDate);
        if (!records) {
            report.setDrillDown(null);
            report.setSupplierSpending(java.util.List.of());
            report.setTopPurchasedProducts(java.util.List.of());
        }
        return ResponseEntity.ok(report);
    }

    /** One page of a report table (sales, expenses, purchases, supplierPayments, salaries, supplierSpending, purchasedProducts). */
    @GetMapping("/drill-down")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<java.util.List<Object>> getDrillDown(
            @org.springframework.web.bind.annotation.RequestParam String section,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String period,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            org.springframework.data.domain.Pageable pageable) {
        return com.neram.pos.util.PagedResponse.ofAny(analyticsService.getDrillDownPage(section, period, startDate, endDate, pageable));
    }

    /** How the period compares with the one before, when and how people buy, what earns money and where money is lost. */
    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<com.neram.pos.dto.InsightsDTO> getInsights(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String period,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        return ResponseEntity.ok(insightsService.getInsights(period, startDate, endDate));
    }

    @GetMapping("/comprehensive-profit")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<com.neram.pos.dto.ComprehensiveProfitReportDTO> getComprehensiveProfitReport(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        return ResponseEntity.ok(analyticsService.getComprehensiveProfitReport(startDate, endDate));
    }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.EXPORTS)
    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportAnalyticsExcel(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        com.neram.pos.dto.ComprehensiveAnalyticsDTO data = analyticsService.getComprehensiveAnalytics(period, startDate, endDate);
        byte[] excelBytes = analyticsExportService.exportToExcel(data);
        String safePeriod = (period != null && !period.isBlank()) ? period : "CUSTOM";
        String filename = "NERAM_Analytics_" + safePeriod + "_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.EXPORTS)
    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportAnalyticsPdf(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        com.neram.pos.dto.ComprehensiveAnalyticsDTO data = analyticsService.getComprehensiveAnalytics(period, startDate, endDate);
        byte[] pdfBytes = analyticsExportService.exportToPdf(data);
        String safePeriod = (period != null && !period.isBlank()) ? period : "CUSTOM";
        String filename = "NERAM_Analytics_" + safePeriod + "_" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
