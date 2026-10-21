package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "print_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, length = 100)
    private String jobId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "bill_number", length = 100)
    private String billNumber;

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType;

    @Column(name = "target_printer_host", nullable = false, length = 150)
    private String targetPrinterHost;

    @Column(name = "target_printer_port", nullable = false)
    private Integer targetPrinterPort;

    @Column(name = "payload")
    private byte[] payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private PrintJobStatus status = PrintJobStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "next_eligible_at")
    private LocalDateTime nextEligibleAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "claimed_by_bridge_id", length = 100)
    private String claimedByBridgeId;

    @Column(name = "last_error_message", length = 2000)
    private String lastErrorMessage;

    @Column(name = "idempotency_key", length = 150)
    private String idempotencyKey;

    @Column(name = "bytes_printed")
    @Builder.Default
    private Integer bytesPrinted = 0;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (status == null) status = PrintJobStatus.PENDING;
        if (attemptCount == null) attemptCount = 0;
        if (bytesPrinted == null) bytesPrinted = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
