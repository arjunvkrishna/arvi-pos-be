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
public class PrintJobOfflineSyncDTO {
    private String jobId;
    private String status;           // SENT_TO_PRINTER, COMPLETED, FAILED, NEEDS_REVIEW
    private Boolean ambiguous;
    private String message;
    private Integer bytesPrinted;
    private LocalDateTime sentAt;
    private LocalDateTime completedAt;
}
