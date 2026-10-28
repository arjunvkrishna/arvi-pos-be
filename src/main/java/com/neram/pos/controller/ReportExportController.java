package com.neram.pos.controller;

import com.neram.pos.dto.ReportCatalogDTO.*;
import com.neram.pos.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ReportExportController {

    private final ReportExportService reportExportService;
    private final com.neram.pos.service.ReportExportWorker reportExportWorker;

    @GetMapping("/catalog")
    public ResponseEntity<List<ReportMetadata>> getCatalog() {
        return ResponseEntity.ok(reportExportService.getCatalog());
    }

    @PostMapping("/export")
    public ResponseEntity<ExportJobResponse> submitExportJob(@RequestBody ExportJobRequest request,
                                                             org.springframework.security.core.Authentication auth) {
        if (request == null || request.getReportId() == null || request.getReportId().isBlank()) {
            throw new IllegalArgumentException("reportId is required");
        }
        Long userId = auth != null && auth.getPrincipal() instanceof com.neram.pos.access.CafeUserDetails user ? user.getUserId() : null;
        ExportJobResponse submitted = reportExportService.submitExportJob(request, userId);
        // The job is committed by now; start it straight away instead of waiting for a poll.
        reportExportWorker.dispatchForCurrentCafe();
        return ResponseEntity.ok(submitted);
    }

    @GetMapping("/export/{jobId}/status")
    public ResponseEntity<ExportJobResponse> getExportStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(reportExportService.getJobStatus(jobId));
    }

    @GetMapping("/export/{jobId}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID jobId) {
        ExportJobResponse status = reportExportService.getJobStatus(jobId);
        if (!"COMPLETED".equalsIgnoreCase(status.getStatus())) {
            throw new IllegalStateException("Export job is not completed yet (status: " + status.getStatus() + ")");
        }

        byte[] data = reportExportService.getJobData(jobId);
        String format = status.getFormat() != null ? status.getFormat().toUpperCase() : "CSV";

        MediaType mediaType;
        String ext;
        switch (format) {
            case "XLSX":
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                ext = ".xlsx";
                break;
            case "PDF":
                mediaType = MediaType.APPLICATION_PDF;
                ext = ".pdf";
                break;
            case "CSV":
            default:
                mediaType = MediaType.parseMediaType("text/csv");
                ext = ".csv";
                break;
        }

        String filename = status.getReportId() + "_" + jobId.toString().substring(0, 8) + ext;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
    }
}
