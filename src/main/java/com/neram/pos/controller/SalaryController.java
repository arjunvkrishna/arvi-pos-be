package com.neram.pos.controller;

import com.neram.pos.dto.SalaryAdvanceRequestDTO;
import com.neram.pos.dto.SalaryDTO;
import com.neram.pos.dto.SalaryRequestDTO;
import com.neram.pos.service.PayrollAccess;
import com.neram.pos.service.SalaryService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salaries")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;
    private final PayrollAccess payrollAccess;

    private static com.neram.pos.entity.User.Role caller(Authentication auth) {
        return PayrollAccess.callerRole(auth);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<SalaryDTO>> getAllSalaries(Pageable pageable, Authentication auth) {
        return PagedResponse.of(salaryService.getAllSalaries(pageable, payrollAccess.hiddenEmployeeIds(caller(auth))));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<SalaryDTO>> getSalariesByEmployee(@PathVariable Long employeeId, Pageable pageable, Authentication auth) {
        payrollAccess.assertEmployee(caller(auth), employeeId);
        return PagedResponse.of(salaryService.getSalariesByEmployee(employeeId, pageable));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<SalaryDTO>> getSalariesByMonthYear(@RequestParam int month, @RequestParam int year, Authentication auth) {
        return ResponseEntity.ok(salaryService.getSalariesByMonthYear(month, year, payrollAccess.hiddenEmployeeIds(caller(auth))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SalaryDTO> createOrUpdateSalary(@RequestBody SalaryRequestDTO request, Authentication auth) {
        payrollAccess.assertEmployee(caller(auth), request.getEmployeeId());
        return ResponseEntity.ok(salaryService.createOrUpdateSalary(request));
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SalaryDTO> markAsPaid(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload, Authentication auth) {
        payrollAccess.assertSalary(caller(auth), id);
        String paymentMode = payload != null ? payload.get("paymentMode") : "CASH";
        return ResponseEntity.ok(salaryService.markAsPaid(id, paymentMode));
    }

    /** Give an advance against a month's salary; the month's record is created when there is none yet. */
    @PostMapping("/advance")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SalaryDTO> giveAdvance(@RequestBody SalaryAdvanceRequestDTO request, Authentication auth) {
        payrollAccess.assertEmployee(caller(auth), request.getEmployeeId());
        return ResponseEntity.ok(salaryService.giveAdvance(request, auth != null ? auth.getName() : null));
    }

    @DeleteMapping("/advance/{advanceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SalaryDTO> deleteAdvance(@PathVariable Long advanceId, Authentication auth) {
        payrollAccess.assertAdvance(caller(auth), advanceId);
        return ResponseEntity.ok(salaryService.deleteAdvance(advanceId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteSalary(@PathVariable Long id, Authentication auth) {
        payrollAccess.assertSalary(caller(auth), id);
        salaryService.deleteSalary(id);
        return ResponseEntity.noContent().build();
    }
}
