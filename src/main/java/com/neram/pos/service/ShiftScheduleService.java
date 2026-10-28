package com.neram.pos.service;

import com.neram.pos.dto.ShiftScheduleDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.ShiftSchedule;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.ShiftScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftScheduleService {

    private final ShiftScheduleRepository shiftScheduleRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<ShiftScheduleDTO> getSchedulesBetween(Instant from, Instant to) {
        return shiftScheduleRepository.findSchedulesBetween(from, to).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftScheduleDTO> getEmployeeSchedules(Long employeeId) {
        return shiftScheduleRepository.findByEmployeeIdOrderByStartsAtAsc(employeeId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShiftScheduleDTO createSchedule(ShiftScheduleDTO dto, Long userId) {
        validateTimes(dto.getStartsAt(), dto.getEndsAt());
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with id: " + dto.getEmployeeId()));

        checkOverlap(employee.getId(), dto.getStartsAt(), dto.getEndsAt(), null);

        ShiftSchedule.Status status = ShiftSchedule.Status.SCHEDULED;
        if (dto.getStatus() != null) {
            try {
                status = ShiftSchedule.Status.valueOf(dto.getStatus().toUpperCase());
            } catch (Exception ignored) {}
        }

        ShiftSchedule schedule = ShiftSchedule.builder()
                .employee(employee)
                .startsAt(dto.getStartsAt())
                .endsAt(dto.getEndsAt())
                .roleNote(dto.getRoleNote())
                .status(status)
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        ShiftSchedule saved = shiftScheduleRepository.save(schedule);
        return toDTO(saved);
    }

    @Transactional
    public ShiftScheduleDTO updateSchedule(Long id, ShiftScheduleDTO dto, Long userId) {
        ShiftSchedule schedule = shiftScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found with id: " + id));

        validateTimes(dto.getStartsAt(), dto.getEndsAt());

        if (!schedule.getEmployee().getId().equals(dto.getEmployeeId())) {
            Employee employee = employeeRepository.findById(dto.getEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Employee not found with id: " + dto.getEmployeeId()));
            schedule.setEmployee(employee);
        }

        checkOverlap(schedule.getEmployee().getId(), dto.getStartsAt(), dto.getEndsAt(), schedule.getId());

        schedule.setStartsAt(dto.getStartsAt());
        schedule.setEndsAt(dto.getEndsAt());
        schedule.setRoleNote(dto.getRoleNote());

        if (dto.getStatus() != null) {
            try {
                schedule.setStatus(ShiftSchedule.Status.valueOf(dto.getStatus().toUpperCase()));
            } catch (Exception ignored) {}
        }

        return toDTO(shiftScheduleRepository.save(schedule));
    }

    @Transactional
    public void deleteSchedule(Long id) {
        ShiftSchedule schedule = shiftScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found with id: " + id));
        shiftScheduleRepository.delete(schedule);
    }

    private void validateTimes(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("Start time and end time are required.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("End time must be strictly after start time.");
        }
    }

    private void checkOverlap(Long employeeId, Instant startsAt, Instant endsAt, Long currentScheduleId) {
        List<ShiftSchedule> overlaps = shiftScheduleRepository.findOverlappingShifts(employeeId, startsAt, endsAt, currentScheduleId);
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("Employee already has an overlapping shift scheduled between " + startsAt + " and " + endsAt);
        }
    }

    public ShiftScheduleDTO toDTO(ShiftSchedule s) {
        return ShiftScheduleDTO.builder()
                .id(s.getId())
                .employeeId(s.getEmployee() != null ? s.getEmployee().getId() : null)
                .employeeName(s.getEmployee() != null ? s.getEmployee().getName() : null)
                .employeeRole(s.getEmployee() != null && s.getEmployee().getRole() != null ? s.getEmployee().getRole().name() : null)
                .startsAt(s.getStartsAt())
                .endsAt(s.getEndsAt())
                .roleNote(s.getRoleNote())
                .status(s.getStatus() != null ? s.getStatus().name() : null)
                .createdBy(s.getCreatedBy())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
