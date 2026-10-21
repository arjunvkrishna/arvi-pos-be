package com.neram.pos.controller;

import com.neram.pos.dto.AttendanceAuditDTO;
import com.neram.pos.dto.AttendanceDTO;
import com.neram.pos.dto.AttendanceMonthlySummaryDTO;
import com.neram.pos.dto.AttendanceRequestDTO;
import com.neram.pos.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping({"/my-audit", "/audit"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttendanceAuditDTO>> getMyAttendanceAudit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(attendanceService.getMyAttendanceAudit(date, startDate, endDate));
    }

    @GetMapping("/date")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AttendanceDTO>> getAttendanceByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getAttendanceByDate(date));
    }

    @GetMapping({"/employee/{employeeId}", "/employee"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<AttendanceDTO>> getAttendanceByEmployee(@PathVariable(required = false) Long employeeId) {
        return ResponseEntity.ok(attendanceService.getAttendanceByEmployee(employeeId));
    }

    @PostMapping("/mark")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AttendanceDTO> markAttendance(@RequestBody AttendanceRequestDTO request) {
        return ResponseEntity.ok(attendanceService.markAttendance(request));
    }

    @PostMapping({"/check-in", "/check-in/{employeeId}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<AttendanceDTO> checkIn(@PathVariable(required = false) Long employeeId) {
        return ResponseEntity.ok(attendanceService.employeeCheckIn(employeeId));
    }

    @PostMapping({"/check-out", "/check-out/{employeeId}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<AttendanceDTO> checkOut(@PathVariable(required = false) Long employeeId) {
        return ResponseEntity.ok(attendanceService.employeeCheckOut(employeeId));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<AttendanceMonthlySummaryDTO> getMonthlySummary(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = (year != null && year > 0) ? year : now.getYear();
        int m = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();
        return ResponseEntity.ok(attendanceService.getMonthlySummary(employeeId, y, m));
    }

    @GetMapping("/monthly-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AttendanceMonthlySummaryDTO>> getAllMonthlySummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = (year != null && year > 0) ? year : now.getYear();
        int m = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();
        return ResponseEntity.ok(attendanceService.getAllEmployeesMonthlySummary(y, m));
    }
}
