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
    /** Non-fatal heads-up for the operator, e.g. the printer is offline so the ticket will wait in the queue. */
    private String warning;
    /** Only when the app asked to print from the phone: the tickets it must send to the printers itself. */
    private java.util.List<DevicePrintJobDTO> deviceJobs;
}
