package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintJobStatusDTO {
    private String jobId;
    private Long billId;
    private String billNumber;
    private String jobType;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextEligibleAt;
    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;
    private LocalDateTime sentAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
    private String claimedByBridgeId;
    private String lastErrorMessage;
    private Integer bytesPrinted;
    private boolean retryable;
}
