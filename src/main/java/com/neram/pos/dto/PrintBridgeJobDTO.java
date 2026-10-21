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
public class PrintBridgeJobDTO {
    private String jobId;
    private String printType;      // BILL, KOT, TEST_SLIP
    private Long billId;
    private String billNumber;
    private String targetHost;
    private Integer targetPort;
    private String dataBase64;     // Base64-encoded ESC/POS bytes
    private String status;
    private Integer attemptCount;
    private LocalDateTime createdAt;
}
