package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "report_export_jobs")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class ReportExportJob implements com.neram.pos.access.OutletScoped {

    @Id
    private UUID id;

    /** The branch this record belongs to; null means the whole cafe. */
    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "report_id", nullable = false, length = 50)
    private String reportId;

    @Column(nullable = false, length = 10)
    private String format; // CSV, XLSX, PDF

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_params", columnDefinition = "JSONB")
    private String filterParams;

    @Column(name = "download_key")
    private String downloadKey;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "completed_at")
    private Instant completedAt;
}
