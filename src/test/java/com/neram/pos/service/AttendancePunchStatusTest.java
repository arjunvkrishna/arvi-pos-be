package com.neram.pos.service;

import com.neram.pos.dto.PunchStatusDTO;
import com.neram.pos.entity.Attendance;
import com.neram.pos.entity.User;
import com.neram.pos.repository.AttendanceRepository;
import com.neram.pos.repository.EmployeeRepository;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendancePunchStatusTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AttendanceService attendanceService;

    private User staff;

    @BeforeEach
    void setUp() {
        staff = new User();
        staff.setId(3L);
        staff.setUsername("staff");
        staff.setRole(User.Role.EMPLOYEE);
        staff.setEmployeeId(3L);
        SecurityContext ctx = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("staff");
        when(auth.getName()).thenReturn("staff");
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        when(userRepository.findByUsername("staff")).thenReturn(Optional.of(staff));
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    private Attendance rec(LocalDate d, LocalTime in, LocalTime out) {
        Attendance a = new Attendance();
        a.setDate(d);
        a.setCheckIn(in);
        a.setCheckOut(out);
        return a;
    }

    @Test
    @DisplayName("no record today and none open yesterday: not checked in")
    void notCheckedIn() {
        when(attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(eq(3L), any())).thenReturn(List.of());
        assertFalse(attendanceService.getPunchStatus(null).isCheckedIn());
    }

    @Test
    @DisplayName("open record today: checked in since that time")
    void openToday() {
        LocalDate today = LocalDate.now();
        when(attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(3L, today)).thenReturn(List.of(rec(today, LocalTime.of(9, 0), null)));
        PunchStatusDTO s = attendanceService.getPunchStatus(null);
        assertTrue(s.isCheckedIn());
        assertEquals(LocalTime.of(9, 0), s.getSince());
    }

    @Test
    @DisplayName("checked out today: not checked in, even if yesterday was left open")
    void closedToday() {
        LocalDate today = LocalDate.now();
        when(attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(3L, today)).thenReturn(List.of(rec(today, LocalTime.of(9, 0), LocalTime.of(17, 0))));
        assertFalse(attendanceService.getPunchStatus(null).isCheckedIn());
    }

    @Test
    @DisplayName("shift left open yesterday and nothing today: still checked in (overnight)")
    void overnight() {
        LocalDate today = LocalDate.now();
        when(attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(3L, today)).thenReturn(List.of());
        when(attendanceRepository.findByEmployeeIdAndDateOrderByIdDesc(3L, today.minusDays(1))).thenReturn(List.of(rec(today.minusDays(1), LocalTime.of(22, 0), null)));
        PunchStatusDTO s = attendanceService.getPunchStatus(null);
        assertTrue(s.isCheckedIn());
        assertEquals(today.minusDays(1), s.getShiftDate());
    }

    @Test
    @DisplayName("staff cannot ask about another employee")
    void staffOtherDenied() {
        assertThrows(AccessDeniedException.class, () -> attendanceService.getPunchStatus(4L));
    }
}
