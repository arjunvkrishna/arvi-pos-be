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
public class PrintResultDTO {
    private boolean success;
    private String message;
    private String host;
    private Integer port;
    private Long billId;
    private String billNumber;
    private String printType;
    private LocalDateTime printedAt;
    private String jobId;
    private String status;
    private Integer attemptCount;
    private boolean retryable;
}
