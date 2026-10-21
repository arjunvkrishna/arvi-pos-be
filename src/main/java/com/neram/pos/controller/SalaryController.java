package com.neram.pos.controller;

import com.neram.pos.dto.SalaryDTO;
import com.neram.pos.dto.SalaryRequestDTO;
import com.neram.pos.service.SalaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salaries")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<SalaryDTO>> getAllSalaries() {
        return ResponseEntity.ok(salaryService.getAllSalaries());
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<SalaryDTO>> getSalariesByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(salaryService.getSalariesByEmployee(employeeId));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<SalaryDTO>> getSalariesByMonthYear(@RequestParam int month, @RequestParam int year) {
        return ResponseEntity.ok(salaryService.getSalariesByMonthYear(month, year));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SalaryDTO> createOrUpdateSalary(@RequestBody SalaryRequestDTO request) {
        return ResponseEntity.ok(salaryService.createOrUpdateSalary(request));
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SalaryDTO> markAsPaid(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload) {
        String paymentMode = payload != null ? payload.get("paymentMode") : "CASH";
        return ResponseEntity.ok(salaryService.markAsPaid(id, paymentMode));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteSalary(@PathVariable Long id) {
        salaryService.deleteSalary(id);
        return ResponseEntity.noContent().build();
    }
}
