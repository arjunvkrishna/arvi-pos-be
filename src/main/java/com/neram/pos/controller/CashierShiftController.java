package com.neram.pos.controller;

import com.neram.pos.dto.CashierShiftDTO;
import com.neram.pos.dto.DrawerAnalysisDTO;
import com.neram.pos.service.CashierShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class CashierShiftController {

    private final CashierShiftService shiftService;

    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.ShiftResponse> openShift(@RequestBody CashierShiftDTO.OpenShiftRequest req) {
        return ResponseEntity.ok(shiftService.openShift(req));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.ShiftResponse> getCurrentShift() {
        return ResponseEntity.ok(shiftService.getCurrentShiftDetails());
    }

    @PostMapping("/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.CashMovementResponse> recordMovement(
            @PathVariable Long id,
            @RequestBody CashierShiftDTO.CashMovementRequest req) {
        return ResponseEntity.ok(shiftService.recordCashMovement(id, req));
    }

    @PostMapping("/{id}/transfers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.CashMovementResponse> recordTransfer(
            @PathVariable Long id,
            @RequestBody CashierShiftDTO.TransferRequest req) {
        return ResponseEntity.ok(shiftService.recordTransfer(id, req));
    }

    @PostMapping("/{id}/correct-variance")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.ShiftResponse> correctVariance(
            @PathVariable Long id,
            @RequestBody CashierShiftDTO.CorrectVarianceRequest req) {
        return ResponseEntity.ok(shiftService.correctVariance(id, req));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.ShiftResponse> closeShift(
            @PathVariable Long id,
            @RequestBody CashierShiftDTO.CloseShiftRequest req) {
        return ResponseEntity.ok(shiftService.closeShift(id, req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CashierShiftDTO.ShiftResponse>> getAllShifts() {
        return ResponseEntity.ok(shiftService.getAllShifts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.ShiftResponse> getShiftById(@PathVariable Long id) {
        return ResponseEntity.ok(shiftService.getShiftById(id));
    }

    @GetMapping("/analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DrawerAnalysisDTO> getDrawerAnalysis(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long cashierId,
            @RequestParam(required = false) String registerId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(shiftService.getDrawerAnalysis(period, startDate, endDate, month, year, cashierId, registerId, status));
    }

    /** One page of a drawer analysis table (table = shifts | daily | monthly) for the same period and filters as /analysis. */
    @GetMapping("/analysis/table")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Object>> getDrawerAnalysisTable(
            @RequestParam(defaultValue = "shifts") String table,
            @RequestParam(defaultValue = "false") boolean anomaliesOnly,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long cashierId,
            @RequestParam(required = false) String registerId,
            @RequestParam(required = false) String status,
            org.springframework.data.domain.Pageable pageable) {
        return com.neram.pos.util.PagedResponse.ofAny(shiftService.getDrawerAnalysisTable(
                table, anomaliesOnly, period, startDate, endDate, month, year, cashierId, registerId, status, pageable));
    }

    @GetMapping("/analysis/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportDrawerAnalysisCsv(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long cashierId,
            @RequestParam(required = false) String registerId,
            @RequestParam(required = false) String status) {
        byte[] csvData = shiftService.exportDrawerAnalysisCsv(period, startDate, endDate, month, year, cashierId, registerId, status);
        String filename = "NERAM_Drawer_Analysis_" + (period != null ? period : "CUSTOM") + "_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CashierShiftDTO.ShiftSettingsDTO> getSettings() {
        return ResponseEntity.ok(shiftService.getSettingsDTO());
    }

    /** Managers may tune the drawer-anomaly thresholds; switching the whole shift module on or off is the admin's call. */
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<CashierShiftDTO.ShiftSettingsDTO> updateSettings(@RequestBody CashierShiftDTO.ShiftSettingsDTO req, Authentication auth) {
        boolean owner = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (req.getEnabled() != null && !owner) {
            throw new AccessDeniedException("Only the cafe owner can switch the Cashier Shift module on or off.");
        }
        return ResponseEntity.ok(shiftService.updateSettings(req));
    }
}
