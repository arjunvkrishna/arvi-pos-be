package com.neram.pos.controller;

import com.neram.pos.dto.MovingDishDTO;
import com.neram.pos.service.AnalyticsExportService;
import com.neram.pos.service.AnalyticsService;
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
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        return ResponseEntity.ok(analyticsService.getComprehensiveAnalytics(period, startDate, endDate));
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
