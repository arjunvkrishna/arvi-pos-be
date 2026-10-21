package com.neram.pos.service;

import com.neram.pos.dto.AttendanceDTO;
import com.neram.pos.dto.AttendanceMonthlySummaryDTO;
import com.neram.pos.dto.LeaveApplicationRequestDTO;
import com.neram.pos.dto.LeaveResponseDTO;
import com.neram.pos.entity.Attendance;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.LeaveRequest;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.entity.User;
import com.neram.pos.repository.AttendanceRepository;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.LeaveRequestRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceAndLeaveSecurityTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private LeaveRequestRepository leaveRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SystemNotificationRepository notificationRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AttendanceService attendanceService;

    private LeaveService leaveService;

    private User employeeUser;
    private User otherEmployeeUser;
    private User managerUser;

    private Employee employeeRecord;
    private Employee otherEmployeeRecord;

    @BeforeEach
    void setUp() {
        leaveService = new LeaveService(leaveRepository, employeeRepository, userRepository, notificationRepository, auditLogService, org.mockito.Mockito.mock(com.neram.pos.service.NotificationStreamService.class));

        employeeUser = new User();
        employeeUser.setId(3L);
        employeeUser.setUsername("employee");
        employeeUser.setRole(User.Role.EMPLOYEE);
        employeeUser.setEmployeeId(3L);

        otherEmployeeUser = new User();
        otherEmployeeUser.setId(4L);
        otherEmployeeUser.setUsername("other_employee");
        otherEmployeeUser.setRole(User.Role.EMPLOYEE);
        otherEmployeeUser.setEmployeeId(4L);

        managerUser = new User();
        managerUser.setId(2L);
        managerUser.setUsername("manager");
        managerUser.setRole(User.Role.MANAGER);
        managerUser.setEmployeeId(2L);

        employeeRecord = new Employee();
        employeeRecord.setId(3L);
        employeeRecord.setName("Employee User");
        employeeRecord.setRole(Employee.Role.EMPLOYEE);
        employeeRecord.setJoinedDate(LocalDate.of(2026, 1, 1));

        otherEmployeeRecord = new Employee();
        otherEmployeeRecord.setId(4L);
        otherEmployeeRecord.setName("Other Staff");
        otherEmployeeRecord.setRole(Employee.Role.EMPLOYEE);
        otherEmployeeRecord.setJoinedDate(LocalDate.of(2026, 1, 1));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(User user) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(user.getUsername());
        when(authentication.getName()).thenReturn(user.getUsername());
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    // ==========================================
    // 1. ATTENDANCE IDOR TESTS
    // ==========================================

    @Test
    @DisplayName("Attendance IDOR: Employee can view own attendance")
    void testEmployeeCanViewOwnAttendance() {
        mockSecurityContext(employeeUser);
        when(attendanceRepository.findByEmployeeId(3L)).thenReturn(Collections.emptyList());

        List<AttendanceDTO> result = attendanceService.getAttendanceByEmployee(3L);
        assertNotNull(result);
        verify(attendanceRepository).findByEmployeeId(3L);
    }

    @Test
    @DisplayName("Attendance IDOR: Employee is denied viewing other employee attendance")
    void testEmployeeDeniedViewingOtherAttendance() {
        mockSecurityContext(employeeUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                attendanceService.getAttendanceByEmployee(4L));
        assertTrue(ex.getMessage().contains("own attendance"));
        verify(attendanceRepository, never()).findByEmployeeId(4L);
    }

    @Test
    @DisplayName("Attendance IDOR: Employee can view own monthly summary")
    void testEmployeeCanViewOwnMonthlySummary() {
        mockSecurityContext(employeeUser);
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employeeRecord));
        when(attendanceRepository.findByEmployeeIdAndDateBetween(eq(3L), any(), any())).thenReturn(Collections.emptyList());

        AttendanceMonthlySummaryDTO summary = attendanceService.getMonthlySummary(3L, 2026, 10);
        assertNotNull(summary);
        assertEquals(3L, summary.getEmployeeId());
    }

    @Test
    @DisplayName("Attendance IDOR: Employee is denied viewing other employee monthly summary")
    void testEmployeeDeniedViewingOtherMonthlySummary() {
        mockSecurityContext(employeeUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                attendanceService.getMonthlySummary(4L, 2026, 10));
        assertTrue(ex.getMessage().contains("own attendance summary"));
        verify(employeeRepository, never()).findById(4L);
    }

    @Test
    @DisplayName("Attendance IDOR: Employee can check in for self")
    void testEmployeeCanCheckInForSelf() {
        mockSecurityContext(employeeUser);
        when(attendanceRepository.findByEmployeeIdAndDateOrderByIdAsc(eq(3L), any())).thenReturn(Collections.emptyList());
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance a = invocation.getArgument(0);
            a.setId(101L);
            return a;
        });
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employeeRecord));

        AttendanceDTO dto = attendanceService.employeeCheckIn(3L);
        assertNotNull(dto);
        assertEquals(3L, dto.getEmployeeId());
    }

    @Test
    @DisplayName("Attendance IDOR: Employee is denied checking in for another employee")
    void testEmployeeDeniedCheckingInForOther() {
        mockSecurityContext(employeeUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                attendanceService.employeeCheckIn(4L));
        assertTrue(ex.getMessage().contains("may not act on other employees"));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Attendance IDOR: Employee is denied checking out for another employee")
    void testEmployeeDeniedCheckingOutForOther() {
        mockSecurityContext(employeeUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                attendanceService.employeeCheckOut(4L));
        assertTrue(ex.getMessage().contains("may not act on other employees"));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Attendance: Manager can view any employee attendance and summary")
    void testManagerCanViewOtherAttendanceAndSummary() {
        mockSecurityContext(managerUser);
        when(attendanceRepository.findByEmployeeId(4L)).thenReturn(Collections.emptyList());
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(otherEmployeeRecord));
        when(attendanceRepository.findByEmployeeIdAndDateBetween(eq(4L), any(), any())).thenReturn(Collections.emptyList());

        List<AttendanceDTO> list = attendanceService.getAttendanceByEmployee(4L);
        assertNotNull(list);

        AttendanceMonthlySummaryDTO summary = attendanceService.getMonthlySummary(4L, 2026, 10);
        assertNotNull(summary);
        assertEquals(4L, summary.getEmployeeId());
    }

    // ==========================================
    // 2. LEAVE IDOR TESTS
    // ==========================================

    @Test
    @DisplayName("Leave IDOR: Employee can apply for own leave")
    void testEmployeeCanApplyForOwnLeave() {
        mockSecurityContext(employeeUser);
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employeeRecord));
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });

        LeaveApplicationRequestDTO req = new LeaveApplicationRequestDTO();
        req.setEmployeeId(3L);
        req.setFromDate(LocalDate.now());
        req.setToDate(LocalDate.now().plusDays(1));
        req.setLeaveType("CASUAL");
        req.setReason("Personal emergency");

        LeaveResponseDTO resp = leaveService.applyForLeave(req);
        assertNotNull(resp);
        assertEquals(3L, resp.getEmployeeId());
        assertEquals("PENDING", resp.getStatus());
    }

    @Test
    @DisplayName("Leave IDOR: Employee cannot apply for leave on behalf of another employee")
    void testEmployeeDeniedApplyingLeaveForOther() {
        mockSecurityContext(employeeUser);

        LeaveApplicationRequestDTO req = new LeaveApplicationRequestDTO();
        req.setEmployeeId(4L); // Target another employee
        req.setFromDate(LocalDate.now());
        req.setToDate(LocalDate.now().plusDays(1));
        req.setLeaveType("CASUAL");
        req.setReason("Malicious cross-employee application");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                leaveService.applyForLeave(req));
        assertTrue(ex.getMessage().contains("Employees cannot apply for leave on behalf of other employees"));
        verify(leaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("Leave IDOR: Employee omitted employeeId derives own ID automatically")
    void testEmployeeOmittedEmployeeIdDerivesSelf() {
        mockSecurityContext(employeeUser);
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employeeRecord));
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            r.setId(51L);
            return r;
        });

        LeaveApplicationRequestDTO req = new LeaveApplicationRequestDTO();
        req.setEmployeeId(null); // Omitted
        req.setFromDate(LocalDate.now());
        req.setToDate(LocalDate.now().plusDays(1));
        req.setLeaveType("SICK");
        req.setReason("Doctor appointment");

        LeaveResponseDTO resp = leaveService.applyForLeave(req);
        assertNotNull(resp);
        assertEquals(3L, resp.getEmployeeId());
    }

    @Test
    @DisplayName("Leave IDOR: Employee can view own leaves")
    void testEmployeeCanViewOwnLeaves() {
        mockSecurityContext(employeeUser);
        when(leaveRepository.findByEmployeeId(3L)).thenReturn(Collections.emptyList());

        List<LeaveResponseDTO> leaves = leaveService.getLeavesByEmployee(3L);
        assertNotNull(leaves);
        verify(leaveRepository).findByEmployeeId(3L);
    }

    @Test
    @DisplayName("Leave IDOR: Employee is denied viewing other employee leaves")
    void testEmployeeDeniedViewingOtherLeaves() {
        mockSecurityContext(employeeUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                leaveService.getLeavesByEmployee(4L));
        assertTrue(ex.getMessage().contains("own leave requests"));
        verify(leaveRepository, never()).findByEmployeeId(4L);
    }

    @Test
    @DisplayName("Leave: Manager can apply leave for an employee and view other leaves")
    void testManagerCanApplyAndManageEmployeeLeaves() {
        mockSecurityContext(managerUser);
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(otherEmployeeRecord));
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            r.setId(52L);
            return r;
        });
        when(leaveRepository.findByEmployeeId(4L)).thenReturn(Collections.emptyList());

        LeaveApplicationRequestDTO req = new LeaveApplicationRequestDTO();
        req.setEmployeeId(4L);
        req.setFromDate(LocalDate.now());
        req.setToDate(LocalDate.now().plusDays(1));
        req.setLeaveType("CASUAL");
        req.setReason("Manager applied leave");

        LeaveResponseDTO resp = leaveService.applyForLeave(req);
        assertNotNull(resp);
        assertEquals(4L, resp.getEmployeeId());

        List<LeaveResponseDTO> leaves = leaveService.getLeavesByEmployee(4L);
        assertNotNull(leaves);
    }
}
