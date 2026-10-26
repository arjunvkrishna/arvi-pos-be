package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A "find my printers" scan of the shop network, as the POS screen sees it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrinterScanDTO {
    private String id;
    /** PENDING (waiting for the bridge), RUNNING, DONE, FAILED, TIMED_OUT */
    private String status;
    private String message;
    private Integer port;
    private String subnet;
    private Integer scanned;
    private Integer total;
    private Long elapsedSeconds;
    private String bridgeId;
    private List<Found> printers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Found {
        private String ip;
        private Integer port;
        private Long latencyMs;
        /** The bridge could not probe it (it was printing at that moment) but it is a configured printer. */
        private Boolean busy;
    }
}
