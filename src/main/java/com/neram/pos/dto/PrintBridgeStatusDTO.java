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
public class PrintBridgeStatusDTO {
    private boolean bridgeOnline;
    private Long lastHeartbeatSecondsAgo;
    private String bridgeId;
    private String bridgeIp;
    private String targetPrinterHost;
    private Integer targetPrinterPort;
    private Boolean printerReachable;
    private Long printerLatencyMs;
    private Integer pendingJobs;
    private Integer unsyncedJobsCount;
    private String mode; // "BRIDGE" or "DIRECT"
    private LocalDateTime lastHeartbeatAt;

    // Triple Printer Status (Bill, Kitchen KOT & Juice KOT)
    private String billPrinterHost;
    private Integer billPrinterPort;
    private Boolean billPrinterReachable;
    private Long billPrinterLatencyMs;

    private String kotPrinterHost;
    private Integer kotPrinterPort;
    private Boolean kotPrinterReachable;
    private Long kotPrinterLatencyMs;

    private String juiceKotPrinterHost;
    private Integer juiceKotPrinterPort;
    private Boolean juiceKotPrinterReachable;
    private Long juiceKotPrinterLatencyMs;

    // ESP32 Hardware Status
    private Long freeHeap;
    private Long freeHeapBytes;
    private Long freePsram;
    private Long freePsramBytes;
    private Long uptimeSeconds;
    private String lastError;
    private String hardware;
    private String hardwareModel;
}
