package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReportCatalogDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReportMetadata {
        private String id;
        private String title;
        private String category; // SALES, FINANCIAL, INVENTORY, CUSTOMERS, STAFF
        private String description;
        private List<String> supportedFormats; // CSV, XLSX, PDF
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExportJobRequest {
        private String reportId;
        private String format; // CSV, XLSX, PDF
        private Map<String, Object> filterParams;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExportJobResponse {
        private UUID jobId;
        private String reportId;
        private String format;
        private String status;
        private String downloadUrl;
        private String errorMessage;
        private Instant createdAt;
        private Instant completedAt;
    }
}
