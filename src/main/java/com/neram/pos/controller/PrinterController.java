package com.neram.pos.controller;

import com.neram.pos.dto.PrintJobStatusDTO;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.dto.PrinterSettingDTO;
import com.neram.pos.service.PrintBridgeService;
import com.neram.pos.service.ThermalPrinterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/printer")
@RequiredArgsConstructor
public class PrinterController {

    private final ThermalPrinterService thermalPrinterService;
    private final PrintBridgeService printBridgeService;

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PrinterSettingDTO> getPrinterConfig() {
        return ResponseEntity.ok(thermalPrinterService.getSettingsDTO());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PrintResultDTO> printTestSlip(
            @RequestParam(value = "type", required = false) String type,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader) {
        String key = idempotencyKeyHeader != null ? idempotencyKeyHeader : xIdempotencyKeyHeader;
        return ResponseEntity.ok(thermalPrinterService.printTestSlip(type, key));
    }

    @PostMapping("/print-bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
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
}
