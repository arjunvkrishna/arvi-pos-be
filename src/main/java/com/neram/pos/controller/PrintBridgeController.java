package com.neram.pos.controller;

import com.neram.pos.dto.*;
import com.neram.pos.entity.PrinterSetting;
import com.neram.pos.service.PrintBridgeService;
import com.neram.pos.service.ThermalPrinterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/printer/bridge")
@RequiredArgsConstructor
public class PrintBridgeController {

    private final PrintBridgeService printBridgeService;
    private final ThermalPrinterService thermalPrinterService;

    @Value("${neram.printer.bridge-token:neram-printer-bridge-secret-2024}")
    private String configuredBridgeToken;

    private boolean isAuthorized(String token) {
        if (configuredBridgeToken == null || configuredBridgeToken.isBlank()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        String clean = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
        return configuredBridgeToken.trim().equals(clean);
    }

    private boolean checkAuth(String bridgeToken, String printBridgeToken, String authHeader) {
        String token = (bridgeToken != null && !bridgeToken.isBlank()) ? bridgeToken
                : (printBridgeToken != null && !printBridgeToken.isBlank()) ? printBridgeToken
                : authHeader;
        return isAuthorized(token);
    }

    /**
     * Dynamic printer configuration endpoint for hardware / local bridge.
     * ESP32 or bridge container queries this to fetch Bill and KOT printer IPs, ports and flags.
     */
    @GetMapping("/config")
    public ResponseEntity<PrinterSettingDTO> getBridgePrinterConfig(
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(thermalPrinterService.getSettingsDTO());
    }

    /**
     * Long-polling endpoint for local bridge container or ESP32 to fetch and atomically claim queued print jobs
     */
    @RequestMapping(value = "/poll", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<PrintBridgeJobDTO> pollJob(
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "bridgeId", defaultValue = "neram-bridge-main") String bridgeId,
            @RequestParam(value = "timeout", defaultValue = "15") int timeoutSeconds) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrintBridgeJobDTO job = printBridgeService.pollJob(bridgeId, timeoutSeconds);
        if (job == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(job);
    }

    /**
     * Bridge reports it has received and persisted the job into its local journal
     */
    @PostMapping("/jobs/{jobId}/ack-receipt")
    public ResponseEntity<Map<String, Object>> ackReceipt(
            @PathVariable String jobId,
            @RequestBody(required = false) PrintBridgeReportDTO report,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String bridgeId = (report != null && report.getBridgeId() != null) ? report.getBridgeId() : "neram-bridge-main";
        boolean success = printBridgeService.acknowledgeJobReceipt(jobId, bridgeId);
        return ResponseEntity.ok(Map.of("jobId", jobId, "acknowledged", success));
    }

    /**
     * Bridge reports it has dispatched the ESC/POS payload to the thermal printer
     */
    @PostMapping("/jobs/{jobId}/sent")
    public ResponseEntity<Map<String, Object>> reportSent(
            @PathVariable String jobId,
            @RequestBody(required = false) PrintBridgeReportDTO report,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String bridgeId = (report != null && report.getBridgeId() != null) ? report.getBridgeId() : "neram-bridge-main";
        boolean success = printBridgeService.recordSentToPrinter(jobId, bridgeId);
        return ResponseEntity.ok(Map.of("jobId", jobId, "sentRecorded", success));
    }

    /**
     * Bridge reports successful completion of print job
     */
    @PostMapping("/jobs/{jobId}/complete")
    public ResponseEntity<Map<String, Object>> reportComplete(
            @PathVariable String jobId,
            @RequestBody(required = false) PrintBridgeReportDTO report,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String bridgeId = (report != null && report.getBridgeId() != null) ? report.getBridgeId() : "neram-bridge-main";
        String message = (report != null && report.getMessage() != null) ? report.getMessage() : "Print executed successfully";
        Integer bytes = (report != null && report.getBytesPrinted() != null) ? report.getBytesPrinted() : 0;

        boolean success = printBridgeService.recordJobCompleted(jobId, bridgeId, message, bytes);
        return ResponseEntity.ok(Map.of("jobId", jobId, "completed", success));
    }

    /**
     * Bridge reports print failure or ambiguous outcome
     */
    @PostMapping("/jobs/{jobId}/fail")
    public ResponseEntity<Map<String, Object>> reportFail(
            @PathVariable String jobId,
            @RequestBody PrintBridgeReportDTO report,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String bridgeId = (report != null && report.getBridgeId() != null) ? report.getBridgeId() : "neram-bridge-main";
        Boolean ambiguous = (report != null && Boolean.TRUE.equals(report.getAmbiguous()));
        String message = (report != null && report.getMessage() != null) ? report.getMessage() : "Bridge execution failed";
        Integer bytes = (report != null && report.getBytesPrinted() != null) ? report.getBytesPrinted() : 0;

        boolean success = printBridgeService.recordJobFailed(jobId, bridgeId, ambiguous, message, bytes);
        return ResponseEntity.ok(Map.of("jobId", jobId, "failureRecorded", success, "ambiguous", ambiguous));
    }

    /**
     * Sync results for jobs executed or queued while bridge was offline
     */
    @PostMapping("/jobs/sync-offline")
    public ResponseEntity<Map<String, Object>> syncOffline(
            @RequestBody List<PrintJobOfflineSyncDTO> batch,
            @RequestParam(value = "bridgeId", defaultValue = "neram-bridge-main") String bridgeId,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        int synced = printBridgeService.syncOfflineResults(batch, bridgeId);
        return ResponseEntity.ok(Map.of(
                "totalSubmitted", batch != null ? batch.size() : 0,
                "syncedCount", synced
        ));
    }

    /**
     * Query detailed state of a print job for bridge
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<PrintJobStatusDTO> getJob(
            @PathVariable String jobId,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(printBridgeService.getJobStatus(jobId));
    }

    /**
     * Backward-compatible legacy ACK endpoint
     */
    @PostMapping("/ack/{jobId}")
    public ResponseEntity<Void> acknowledgeJob(
            @PathVariable String jobId,
            @RequestBody PrintBridgeAckDTO ack,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (ack != null && ack.isSuccess()) {
            printBridgeService.recordJobCompleted(jobId, "neram-bridge-legacy", ack.getMessage(), ack.getBytesPrinted());
        } else {
            String msg = (ack != null && ack.getMessage() != null) ? ack.getMessage() : "Legacy print failed";
            printBridgeService.recordJobFailed(jobId, "neram-bridge-legacy", false, msg, ack != null ? ack.getBytesPrinted() : 0);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Periodic heartbeat from local bridge container or ESP32
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @RequestBody PrintBridgeHeartbeatDTO heartbeat,
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "X-Print-Bridge-Token", required = false) String printBridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!checkAuth(bridgeToken, printBridgeToken, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        printBridgeService.recordHeartbeat(heartbeat);
        return ResponseEntity.ok().build();
    }

    /**
     * Check health/status of the cloud-to-local print bridge
     */
    @GetMapping("/status")
    public ResponseEntity<PrintBridgeStatusDTO> getStatus() {
        PrinterSetting settings = thermalPrinterService.getSettings();
        PrintBridgeStatusDTO status = printBridgeService.getBridgeStatus(settings.getHost(), settings.getPort());
        return ResponseEntity.ok(status);
    }
}
