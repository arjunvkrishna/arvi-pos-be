package com.neram.pos.controller;

import com.neram.pos.dto.*;
import com.neram.pos.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    // ============================================================
    // EXPENSES ENDPOINTS
    // ============================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CafeExpenseDTO>> getAllExpenses(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(expenseService.getExpensesByType(type));
        }
        return ResponseEntity.ok(expenseService.getAllExpenses());
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CafeExpenseDTO>> getMonthlyExpenses(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(expenseService.getExpensesByMonth(month, year));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CafeExpenseDTO> createExpense(@RequestBody CafeExpenseRequestDTO request) {
        return ResponseEntity.ok(expenseService.createExpense(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CafeExpenseDTO> updateExpense(@PathVariable Long id, @RequestBody CafeExpenseRequestDTO request) {
        return ResponseEntity.ok(expenseService.updateExpense(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CafeExpenseDTO> updateExpenseStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "PAID");
        String paymentMethod = body.get("paymentMethod");
        return ResponseEntity.ok(expenseService.updateExpenseStatus(id, status, paymentMethod));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // RECURRING EXPENSE TEMPLATES ENDPOINTS
    // ============================================================

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<ExpenseTemplateDTO>> getAllTemplates(
            @RequestParam(required = false) Boolean onlyActive) {
        return ResponseEntity.ok(expenseService.getAllTemplates(onlyActive));
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ExpenseTemplateDTO> getTemplateById(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getTemplateById(id));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ExpenseTemplateDTO> createTemplate(@RequestBody ExpenseTemplateRequestDTO request) {
        return ResponseEntity.ok(expenseService.createTemplate(request));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ExpenseTemplateDTO> updateTemplate(
            @PathVariable Long id,
            @RequestBody ExpenseTemplateRequestDTO request) {
        return ResponseEntity.ok(expenseService.updateTemplate(id, request));
    }

    @PostMapping("/templates/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ExpenseTemplateDTO> toggleTemplateActive(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.toggleTemplateActive(id));
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        expenseService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // RECURRING GENERATION & CHECKLIST ENDPOINTS
    // ============================================================

    @PostMapping("/templates/{id}/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CafeExpenseDTO> generateExpenseFromTemplate(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(expenseService.generateExpenseFromTemplate(id, expenseDate, status));
    }

    @PostMapping("/templates/generate-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CafeExpenseDTO>> generateAllDueRecurring(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate) {
        return ResponseEntity.ok(expenseService.generateAllDueRecurring(month, year, expenseDate));
    }

    @GetMapping("/templates/monthly-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<RecurringExpenseStatusDTO>> getMonthlyRecurringStatus(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(expenseService.getRecurringStatusForMonth(month, year));
    }
}
