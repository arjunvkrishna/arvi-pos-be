package com.neram.pos.controller;

import com.neram.pos.dto.BankReconciliationDTO.*;
import com.neram.pos.service.BankReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/banking")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
@com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.ACCOUNTING)
public class BankReconciliationController {

    private final BankReconciliationService reconciliationService;

    @GetMapping("/statements")
    public ResponseEntity<List<StatementSummary>> getAllStatements() {
        return ResponseEntity.ok(reconciliationService.getAllStatements());
    }

    @GetMapping("/statements/{id}/rows")
    public ResponseEntity<List<StatementRow>> getStatementRows(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(reconciliationService.getStatementRows(id, status));
    }

    @PostMapping(value = "/statements/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importStatementFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        try {
            return ResponseEntity.ok(reconciliationService.importCsv(description, file.getInputStream(), null));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to upload statement: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/statements/import-raw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImportResult> importStatementRaw(@RequestBody Map<String, String> body) {
        String description = body.get("description");
        String csvData = body.get("csvData");
        if (csvData == null || csvData.isBlank()) {
            throw new IllegalArgumentException("csvData is required");
        }
        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok(reconciliationService.importCsv(description, is, null));
    }

    @PostMapping("/rows/{id}/match")
    public ResponseEntity<StatementRow> matchRow(
            @PathVariable Long id,
            @RequestBody MatchRequest request) {
        if (request == null || request.getExpenseId() == null) {
            throw new IllegalArgumentException("expenseId is required");
        }
        return ResponseEntity.ok(reconciliationService.manualMatch(id, request.getExpenseId()));
    }

    @PostMapping("/rows/{id}/unmatch")
    public ResponseEntity<StatementRow> unmatchRow(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationService.unmatch(id));
    }

    @PostMapping("/rows/{id}/ignore")
    public ResponseEntity<StatementRow> ignoreRow(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationService.ignoreRow(id));
    }

    @PostMapping("/auto-match")
    public ResponseEntity<Map<String, Object>> autoMatch() {
        int matched = reconciliationService.autoMatchAllUnmatched();
        return ResponseEntity.ok(Map.of("autoMatchedCount", matched));
    }
}
