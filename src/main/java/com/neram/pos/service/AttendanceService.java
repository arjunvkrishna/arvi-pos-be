package com.neram.pos.service;

import com.neram.pos.dto.AttendanceAuditDTO;
import com.neram.pos.dto.AttendanceDTO;
import com.neram.pos.dto.AttendanceMonthlySummaryDTO;
import com.neram.pos.dto.AttendanceRequestDTO;
import com.neram.pos.dto.PunchStatusDTO;
import com.neram.pos.entity.Attendance;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.AttendanceRepository;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                return userRepository.findByUsername(auth.getName()).orElse(null);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Long getAuthenticatedEmployeeId(User user) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required to determine employee identity.");
        }
        if (user.getEmployeeId() != null && user.getEmployeeId() > 0) {
            return user.getEmployeeId();
        }
        Employee emp = employeeRepository.findByNameIgnoreCase(user.getUsername())
                .orElseGet(() -> {
                    Employee newEmp = new Employee();
                    newEmp.setName(user.getUsername());
                    newEmp.setRole(user.getRole() == User.Role.ADMIN ? Employee.Role.ADMIN
                            : (user.getRole() == User.Role.MANAGER ? Employee.Role.MANAGER : Employee.Role.EMPLOYEE));
                    newEmp.setDepartment("Staff");
                    newEmp.setDesignation(user.getRole() == User.Role.MANAGER ? "Shift Manager" : user.getRole().name());
                    newEmp.setJoinedDate(LocalDate.now());
                    newEmp.setActive(true);
                    return employeeRepository.save(newEmp);
                });
        user.setEmployeeId(emp.getId());
        userRepository.save(user);
        return emp.getId();
    }

    public List<AttendanceAuditDTO> getMyAttendanceAudit(LocalDate date, LocalDate startDate, LocalDate endDate) {
        User user = getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Authentication required to access personal attendance audit");
        }

        Long employeeId = getAuthenticatedEmployeeId(user);
        String employeeName = user.getUsername();
        if (employeeId != null) {
            Employee emp = employeeRepository.findById(employeeId).orElse(null);
            if (emp != null && emp.getName() != null) {
                employeeName = emp.getName();
            }
        }

        if (employeeId == null) {
            return Collections.emptyList();
        }

        List<Attendance> records;
        if (date != null) {
            records = attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(employeeId, date);
        } else if (startDate != null && endDate != null) {
            records = attendanceRepository.findByEmployeeIdAndDateBetweenOrderByDateDescIdDesc(employeeId, startDate, endDate);
        } else if (startDate != null) {
            records = attendanceRepository.findByEmployeeIdAndDateBetweenOrderByDateDescIdDesc(employeeId, startDate, LocalDate.now());
        } else {
            records = attendanceRepository.findByEmployeeIdOrderByDateDescIdDesc(employeeId);
        }

        final String finalName = employeeName;
        return records.stream()
                .map(a -> AttendanceAuditDTO.builder()
                        .date(a.getDate())
                        .name(finalName)
                        .checkIn(a.getCheckIn())
                        .checkOut(a.getCheckOut())
                        .build())
                .collect(Collectors.toList());
    }

    public List<AttendanceDTO> getAttendanceByDate(LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        return attendanceRepository.findByDate(queryDate).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<AttendanceDTO> getAttendanceByEmployee(Long employeeId) {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (employeeId != null && !employeeId.equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees may only view their own attendance.");
            }
            employeeId = myEmployeeId;
        } else if (employeeId == null && currentUser != null) {
            employeeId = getAuthenticatedEmployeeId(currentUser);
        }
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID is required.");
        }
        return attendanceRepository.findByEmployeeId(employeeId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** One page of the attendance records of a day (default today), in the order they were recorded. */
    public org.springframework.data.domain.Page<AttendanceDTO> getAttendanceByDate(LocalDate date, org.springframework.data.domain.Pageable pageable) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        return attendanceRepository.findByDate(queryDate, com.neram.pos.util.PagedResponse.withSort(pageable, org.springframework.data.domain.Sort.by("id")))
                .map(this::toDTO);
    }

    /** One page of an employee's attendance records, newest day first (same access rules as {@link #getAttendanceByEmployee(Long)}). */
    public org.springframework.data.domain.Page<AttendanceDTO> getAttendanceByEmployee(Long employeeId, org.springframework.data.domain.Pageable pageable) {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (employeeId != null && !employeeId.equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees may only view their own attendance.");
            }
            employeeId = myEmployeeId;
        } else if (employeeId == null && currentUser != null) {
            employeeId = getAuthenticatedEmployeeId(currentUser);
        }
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID is required.");
        }
        org.springframework.data.domain.Sort newestDayFirst = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("date"), org.springframework.data.domain.Sort.Order.desc("id"));
        return attendanceRepository.findByEmployeeId(employeeId, com.neram.pos.util.PagedResponse.withSort(pageable, newestDayFirst)).map(this::toDTO);
    }

    /** One page of the active employees' monthly summaries (ordered by employee id); only the page's employees are computed. */
    public org.springframework.data.domain.Page<AttendanceMonthlySummaryDTO> getAllEmployeesMonthlySummary(int year, int month, org.springframework.data.domain.Pageable pageable) {
        return employeeRepository.findByActiveTrue(com.neram.pos.util.PagedResponse.withSort(pageable, org.springframework.data.domain.Sort.by("id")))
                .map(emp -> getMonthlySummary(emp.getId(), year, month));
    }

    public List<AttendanceDTO> getAttendanceRange(Long employeeId, LocalDate start, LocalDate end) {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (employeeId != null && !employeeId.equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees may only view their own attendance.");
            }
            employeeId = myEmployeeId;
        } else if (employeeId == null && currentUser != null) {
            employeeId = getAuthenticatedEmployeeId(currentUser);
        }
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID is required.");
        }
        return attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, start, end).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceDTO markAttendance(AttendanceRequestDTO dto) {
        LocalDate date = dto.getDate() != null ? dto.getDate() : LocalDate.now();
        Attendance att = attendanceRepository.findTopByEmployeeIdAndDateOrderByIdDesc(dto.getEmployeeId(), date)
                .orElse(new Attendance());

        att.setEmployeeId(dto.getEmployeeId());
        att.setDate(date);
        if (dto.getCheckIn() != null) att.setCheckIn(dto.getCheckIn().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        if (dto.getCheckOut() != null) att.setCheckOut(dto.getCheckOut().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        if (dto.getStatus() != null) {
            att.setStatus(Attendance.Status.valueOf(dto.getStatus().toUpperCase()));
        }
        att.setNotes(dto.getNotes());

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> att.setMarkedBy(u.getId()));
            }
        } catch (Exception ignored) {}

        if (att.getId() == null) {
            att.setCreatedAt(LocalDateTime.now());
        }

        Attendance saved = attendanceRepository.save(att);
        auditLogService.log("MARK_ATTENDANCE", "ATTENDANCE", saved.getId(), null, "Employee " + dto.getEmployeeId() + " marked " + saved.getStatus() + " on " + saved.getDate());
        return toDTO(saved);
    }

    public Long resolveCurrentEmployeeId(Long providedId) {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (providedId != null && providedId > 0 && !providedId.equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees may not act on other employees' attendance.");
            }
            return myEmployeeId;
        }
        if (providedId != null && providedId > 0) {
            return providedId;
        }
        if (currentUser != null) {
            return getAuthenticatedEmployeeId(currentUser);
        }
        return providedId != null ? providedId : 1L;
    }

    /**
     * Is a shift open right now? Same idea as check-out: an open record today counts; with no record at all today, an open record
     * from yesterday counts (a shift that runs past midnight). A read only: it never creates an employee record.
     */
    @Transactional(readOnly = true)
    public PunchStatusDTO getPunchStatus(Long providedId) {
        Long employeeId = lookupEmployeeIdWithoutCreating(providedId);
        if (employeeId == null) return PunchStatusDTO.builder().checkedIn(false).build();

        LocalDate today = LocalDate.now();
        List<Attendance> todayRecords = attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(employeeId, today);
        Attendance open = todayRecords.stream().filter(a -> a.getCheckIn() != null && a.getCheckOut() == null).findFirst().orElse(null);
        if (open == null && todayRecords.isEmpty()) {
            open = attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(employeeId, today.minusDays(1)).stream()
                    .filter(a -> a.getCheckIn() != null && a.getCheckOut() == null).findFirst().orElse(null);
        }
        return open == null
                ? PunchStatusDTO.builder().checkedIn(false).build()
                : PunchStatusDTO.builder().checkedIn(true).since(open.getCheckIn()).shiftDate(open.getDate()).build();
    }

    private Long lookupEmployeeIdWithoutCreating(Long providedId) {
        User currentUser = getCurrentUser();
        Long mine = null;
        if (currentUser != null) {
            mine = (currentUser.getEmployeeId() != null && currentUser.getEmployeeId() > 0) ? currentUser.getEmployeeId()
                    : employeeRepository.findByNameIgnoreCase(currentUser.getUsername()).map(Employee::getId).orElse(null);
        }
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            if (providedId != null && providedId > 0 && !providedId.equals(mine)) {
                throw new AccessDeniedException("Employees may not look at other employees' attendance.");
            }
            return mine;
        }
        return (providedId != null && providedId > 0) ? providedId : mine;
    }

    @Transactional
    public AttendanceDTO employeeCheckIn(Long employeeId) {
        employeeId = resolveCurrentEmployeeId(employeeId);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        List<Attendance> todayRecords = attendanceRepository.findByEmployeeIdAndDateOrderByIdAsc(employeeId, today);

        if (todayRecords.isEmpty()) {
            Attendance att = new Attendance();
            att.setEmployeeId(employeeId);
            att.setDate(today);
            att.setCheckIn(now);
            att.setStatus(Attendance.Status.PRESENT);
            att.setCreatedAt(LocalDateTime.now());
            Attendance saved = attendanceRepository.save(att);
            auditLogService.log("CHECK_IN", "ATTENDANCE", saved.getId(), null, "Employee " + employeeId + " checked in at " + saved.getCheckIn());
            return toDTO(saved);
        }

        Attendance lastEntry = todayRecords.get(todayRecords.size() - 1);

        if (lastEntry.getCheckIn() == null) {
            lastEntry.setCheckIn(now);
            lastEntry.setStatus(Attendance.Status.PRESENT);
            Attendance saved = attendanceRepository.save(lastEntry);
            auditLogService.log("CHECK_IN", "ATTENDANCE", saved.getId(), null, "Employee " + employeeId + " checked in at " + saved.getCheckIn());
            return toDTO(saved);
        }

        // Already a check-in value is there for today:
        // If last entry has no checkout value, set its checkout time to the new entry's start time
        if (lastEntry.getCheckOut() == null) {
            lastEntry.setCheckOut(now);
            attendanceRepository.save(lastEntry);
            auditLogService.log("CHECK_OUT", "ATTENDANCE", lastEntry.getId(), null, "Employee " + employeeId + " auto checked out at " + now + " due to subsequent check-in");
        }

        Attendance newEntry = new Attendance();
        newEntry.setEmployeeId(employeeId);
        newEntry.setDate(today);
        newEntry.setCheckIn(now);
        newEntry.setCheckOut(null);
        newEntry.setStatus(Attendance.Status.PRESENT);
        newEntry.setCreatedAt(LocalDateTime.now());

        Attendance saved = attendanceRepository.save(newEntry);
        auditLogService.log("CHECK_IN", "ATTENDANCE", saved.getId(), null, "Employee " + employeeId + " checked in again at " + saved.getCheckIn());
        return toDTO(saved);
    }

    @Transactional
    public AttendanceDTO employeeCheckOut(Long employeeId) {
        employeeId = resolveCurrentEmployeeId(employeeId);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        List<Attendance> todayRecords = attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(employeeId, today);
        Attendance att = null;

        // 1. Check for open (un-checked-out) shift on today
        if (!todayRecords.isEmpty()) {
            att = todayRecords.stream()
                    .filter(a -> a.getCheckOut() == null)
                    .findFirst()
                    .orElse(null);
        }

        // 2. Overnight shift support (e.g. 3 PM to 3 AM): if no open shift today, check yesterday's open shift
        if (att == null) {
            List<Attendance> yesterdayRecords = attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(employeeId, today.minusDays(1));
            att = yesterdayRecords.stream()
                    .filter(a -> a.getCheckOut() == null)
                    .findFirst()
                    .orElse(null);
        }

        // 3. Fallback: if today has any record, use the latest
        if (att == null) {
            if (!todayRecords.isEmpty()) {
                att = todayRecords.get(0);
            } else {
                throw new RuntimeException("No check-in record found for today or yesterday's overnight shift");
            }
        }

        att.setCheckOut(now);
        Attendance saved = attendanceRepository.save(att);
        auditLogService.log("CHECK_OUT", "ATTENDANCE", saved.getId(), null,
                "Employee " + employeeId + " checked out at " + saved.getCheckOut() + " (Shift Date: " + saved.getDate() + ")");
        return toDTO(saved);
    }

    public AttendanceMonthlySummaryDTO getMonthlySummary(Long employeeId, int year, int month) {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (employeeId != null && !employeeId.equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees may only view their own attendance summary.");
            }
            employeeId = myEmployeeId;
        } else if (employeeId == null && currentUser != null) {
            employeeId = getAuthenticatedEmployeeId(currentUser);
        }
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID is required.");
        }

        final Long targetEmpId = employeeId;
        Employee employee = employeeRepository.findById(targetEmpId)
                .orElseThrow(() -> new RuntimeException("Employee not found with ID: " + targetEmpId));

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        int totalDaysInMonth = ym.lengthOfMonth();

        LocalDate joinedDate = employee.getJoinedDate();
        int eligibleDays = 0;

        if (joinedDate == null) {
            eligibleDays = totalDaysInMonth;
        } else if (joinedDate.isAfter(monthEnd)) {
            eligibleDays = 0;
        } else {
            LocalDate effectiveStart = joinedDate.isAfter(monthStart) ? joinedDate : monthStart;
            eligibleDays = (int) ChronoUnit.DAYS.between(effectiveStart, monthEnd) + 1;
        }

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, monthStart, monthEnd);

        long presentDays = attendances.stream().filter(a -> a.getStatus() == Attendance.Status.PRESENT)
                .map(Attendance::getDate).distinct().count();
        long halfDays = attendances.stream().filter(a -> a.getStatus() == Attendance.Status.HALF_DAY)
                .map(Attendance::getDate).distinct().count();
        long absentDays = attendances.stream().filter(a -> a.getStatus() == Attendance.Status.ABSENT)
                .map(Attendance::getDate).distinct().count();
        long leaveDays = attendances.stream().filter(a -> a.getStatus() == Attendance.Status.LEAVE)
                .map(Attendance::getDate).distinct().count();

        double effectivePresentDays = presentDays + (0.5 * halfDays);
        double attendancePercentage = eligibleDays > 0 ? Math.round((effectivePresentDays / eligibleDays) * 1000.0) / 10.0 : 0.0;

        return AttendanceMonthlySummaryDTO.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .role(employee.getRole() != null ? employee.getRole().name() : "")
                .joinedDate(joinedDate)
                .year(year)
                .month(month)
                .totalDaysInMonth(totalDaysInMonth)
                .eligibleDays(eligibleDays)
                .presentDays(presentDays)
                .halfDays(halfDays)
                .absentDays(absentDays)
                .leaveDays(leaveDays)
                .effectivePresentDays(effectivePresentDays)
                .attendancePercentage(attendancePercentage)
                .build();
    }

    public List<AttendanceMonthlySummaryDTO> getAllEmployeesMonthlySummary(int year, int month) {
        List<Employee> activeEmployees = employeeRepository.findByActiveTrue();
        return activeEmployees.stream()
                .map(emp -> getMonthlySummary(emp.getId(), year, month))
                .collect(Collectors.toList());
    }

    public AttendanceDTO toDTO(Attendance a) {
        String empName = employeeRepository.findById(a.getEmployeeId())
                .map(Employee::getName)
                .orElse("Unknown Employee");

        Double hours = null;
        if (a.getCheckIn() != null && a.getCheckOut() != null) {
            long minutes = java.time.Duration.between(a.getCheckIn(), a.getCheckOut()).toMinutes();
            hours = Math.round((minutes / 60.0) * 100.0) / 100.0;
        }

        return AttendanceDTO.builder()
                .id(a.getId())
                .employeeId(a.getEmployeeId())
                .employeeName(empName)
                .date(a.getDate())
                .checkIn(a.getCheckIn())
                .checkOut(a.getCheckOut())
                .workHours(hours)
                .status(a.getStatus().name())
                .notes(a.getNotes())
                .markedBy(a.getMarkedBy())
                .createdAt(a.getCreatedAt())
                .build();
    }
}

