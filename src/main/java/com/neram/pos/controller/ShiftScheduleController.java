package com.neram.pos.controller;

import com.neram.pos.dto.ShiftScheduleDTO;
import com.neram.pos.service.ShiftScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/shifts/schedules")
@RequiredArgsConstructor
public class ShiftScheduleController {

    private final ShiftScheduleService shiftScheduleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<ShiftScheduleDTO>> getSchedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Instant start = from != null ? from : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = to != null ? to : Instant.now().plus(14, ChronoUnit.DAYS);

        return ResponseEntity.ok(shiftScheduleService.getSchedulesBetween(start, end));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<ShiftScheduleDTO>> getEmployeeSchedules(@PathVariable Long employeeId) {
        return ResponseEntity.ok(shiftScheduleService.getEmployeeSchedules(employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ShiftScheduleDTO> createSchedule(@RequestBody ShiftScheduleDTO dto) {
        return ResponseEntity.ok(shiftScheduleService.createSchedule(dto, null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ShiftScheduleDTO> updateSchedule(@PathVariable Long id, @RequestBody ShiftScheduleDTO dto) {
        return ResponseEntity.ok(shiftScheduleService.updateSchedule(id, dto, null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        shiftScheduleService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
