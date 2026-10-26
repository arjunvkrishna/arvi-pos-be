package com.neram.pos.controller;

import com.neram.pos.dto.PrintBridgeHeartbeatDTO;
import com.neram.pos.dto.PrintJobStatusDTO;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.dto.PrinterScanDTO;
import com.neram.pos.dto.PrinterSettingDTO;
import com.neram.pos.service.PrintBridgeService;
import com.neram.pos.service.ThermalPrinterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/printer")
@RequiredArgsConstructor
public class PrinterController {

    private final ThermalPrinterService thermalPrinterService;
    private final PrintBridgeService printBridgeService;
    private final com.neram.pos.service.PrinterScanService printerScanService;

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrinterSettingDTO> getPrinterConfig() {
        return ResponseEntity.ok(thermalPrinterService.getSettingsDTO());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('PERM_MANAGE_OUTLET_SETTINGS')")
    public ResponseEntity<PrinterSettingDTO> updatePrinterConfig(@RequestBody PrinterSettingDTO dto) {
        return ResponseEntity.ok(thermalPrinterService.updateSettings(dto));
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrintResultDTO> checkStatus(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "cached", required = false, defaultValue = "false") boolean cached) {
        // Default is a live probe. Automatic checks may pass cached=1 to accept a direct-socket result up to 15s old.
        return ResponseEntity.ok(thermalPrinterService.testConnection(type, cached));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('PERM_MANAGE_OUTLET_SETTINGS')")
    public ResponseEntity<PrintResultDTO> printTestSlip(
            @RequestParam(value = "type", required = false) String type,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader) {
        String key = idempotencyKeyHeader != null ? idempotencyKeyHeader : xIdempotencyKeyHeader;
        return ResponseEntity.ok(thermalPrinterService.printTestSlip(type, key));
    }

    @PostMapping("/print-bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_PRINT_BILL')")
    public ResponseEntity<PrintResultDTO> printBill(
            @PathVariable Long billId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader) {
        String key = idempotencyKeyHeader != null ? idempotencyKeyHeader : xIdempotencyKeyHeader;
        return ResponseEntity.ok(thermalPrinterService.printBill(billId, key));
    }

    @PostMapping("/print-kot/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrintResultDTO> printKot(
            @PathVariable Long billId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader) {
        String key = idempotencyKeyHeader != null ? idempotencyKeyHeader : xIdempotencyKeyHeader;
        return ResponseEntity.ok(thermalPrinterService.printKot(billId, key));
    }

    /**
     * Prints the last kitchen ticket of an order that is being prepared once more (marked REPRINT). Anyone who can take orders may do it;
     * it changes nothing on the order.
     */
    @PostMapping("/reprint-kot/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrintResultDTO> reprintKot(@PathVariable Long billId) {
        return ResponseEntity.ok(thermalPrinterService.reprintKot(billId));
    }

    @PostMapping("/print-kot-update/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrintResultDTO> printKotUpdate(
            @PathVariable Long billId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader) {
        String key = idempotencyKeyHeader != null ? idempotencyKeyHeader : xIdempotencyKeyHeader;
        return ResponseEntity.ok(thermalPrinterService.printUpdatedKot(billId, null, key));
    }

    @PostMapping("/print-kot-cancel/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrintResultDTO> printKotCancel(
            @PathVariable Long billId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader) {
        String key = idempotencyKeyHeader != null ? idempotencyKeyHeader : xIdempotencyKeyHeader;
        return ResponseEntity.ok(thermalPrinterService.printCancelledKot(billId, null, key));
    }

    /**
     * Query detailed status of a persistent print job (for POS polling)
     */
    @GetMapping("/jobs/{jobId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrintJobStatusDTO> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(printBridgeService.getJobStatus(jobId));
    }

    /**
     * Deliberately retry a failed or ambiguous print job (operator override)
     */
    @PostMapping("/jobs/{jobId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PrintJobStatusDTO> retryJob(@PathVariable String jobId) {
        return ResponseEntity.ok(printBridgeService.retryJob(jobId));
    }

    /**
     * "Scan for printers": asks the print bridge to look for devices listening on the thermal-printer port on the shop network
     * and report their addresses (the bridge picks the request up on its next poll). Poll {@code GET /scan/{id}} for the result.
     */
    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('PERM_MANAGE_OUTLET_SETTINGS')")
    public ResponseEntity<?> startScan(@RequestParam(value = "port", defaultValue = "9100") int port) {
        if (!printBridgeService.isBridgeOnline()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of("message",
                    "The print bridge is offline, so it cannot scan the network. Check it is powered and on Wi-Fi."));
        }
        PrintBridgeHeartbeatDTO hb = printBridgeService.getLatestHeartbeat();
        if (hb == null || !Boolean.TRUE.equals(hb.getScanSupported())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of("message",
                    "The print bridge firmware is too old to scan the network. Update the ESP32 to firmware v2.1.0 or later."));
        }
        try {
            return ResponseEntity.ok(printerScanService.start(port));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/scan/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PrinterScanDTO> latestScan() {
        return printerScanService.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/scan/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PrinterScanDTO> getScan(@PathVariable String id) {
        return printerScanService.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
