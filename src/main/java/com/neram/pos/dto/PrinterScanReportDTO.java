package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What the print bridge reports while (and when) it scans the network it sits on. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrinterScanReportDTO {
    private String bridgeId;
    private String subnet;
    private Integer scanned;
    private Integer total;
    /** true on the last report of the scan */
    private Boolean done;
    /** a failure the bridge wants to show (no Wi-Fi, no address ...) */
    private String error;
    private List<PrinterScanDTO.Found> printers;
}
