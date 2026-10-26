package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintBridgeHeartbeatDTO {
    private String bridgeId;
    /** the ESP32 firmware sends this as "ipAddress" */
    @com.fasterxml.jackson.annotation.JsonAlias("ipAddress")
    private String bridgeIp;
    private String printerHost;
    private Integer printerPort;
    private Boolean printerReachable;
    private Long printerLatencyMs;
    private String version;
    private Integer unsyncedJobsCount;

    // Triple Printer Support (Bill, Kitchen KOT & Juice KOT)
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

    // ESP32 Hardware Telemetry
    private Long freeHeap;
    private Long freeHeapBytes;
    private Long freePsram;
    private Long freePsramBytes;
    private Long uptimeSeconds;
    private String lastError;
    private String hardware; // e.g. "ESP32-S3-N16R8"
    private String hardwareModel;

    /** true when the firmware can scan the shop network for printers (v2.1.0 and later) */
    private Boolean scanSupported;
}
