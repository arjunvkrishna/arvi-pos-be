package com.neram.pos.service;

import com.neram.pos.access.CafeUserDetails;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.Salary;
import com.neram.pos.entity.SalaryAdvance;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.SalaryAdvanceRepository;
import com.neram.pos.repository.SalaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PayrollAccessTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private SalaryRepository salaryRepository;
    @Mock private SalaryAdvanceRepository advanceRepository;
    @InjectMocks private PayrollAccess access;

    private static Employee employee(long id, Employee.Role role) {
        Employee e = new Employee();
        e.setId(id);
        e.setRole(role);
        return e;
    }

    @BeforeEach
    void data() {
        Employee owner = employee(1L, Employee.Role.ADMIN);
        Employee staff = employee(2L, Employee.Role.EMPLOYEE);
        Employee general = employee(3L, Employee.Role.GENERAL_MANAGER);
        Employee branch = employee(4L, Employee.Role.MANAGER);
        List<Employee> all = List.of(owner, staff, general, branch);
        for (Employee e : all) {
            lenient().when(employeeRepository.findById(e.getId())).thenReturn(Optional.of(e));
        }
        lenient().when(employeeRepository.findByRoleIn(anyCollection())).thenAnswer(inv -> {
            Collection<Employee.Role> roles = inv.getArgument(0);
            return all.stream().filter(e -> roles.contains(e.getRole())).toList();
        });

        Salary ownerPay = new Salary(); ownerPay.setId(10L); ownerPay.setEmployeeId(1L);
        Salary staffPay = new Salary(); staffPay.setId(20L); staffPay.setEmployeeId(2L);
        lenient().when(salaryRepository.findById(10L)).thenReturn(Optional.of(ownerPay));
        lenient().when(salaryRepository.findById(20L)).thenReturn(Optional.of(staffPay));

        SalaryAdvance onOwner = new SalaryAdvance(); onOwner.setId(100L); onOwner.setSalaryId(10L);
        SalaryAdvance onStaff = new SalaryAdvance(); onStaff.setId(200L); onStaff.setSalaryId(20L);
        lenient().when(advanceRepository.findById(100L)).thenReturn(Optional.of(onOwner));
        lenient().when(advanceRepository.findById(200L)).thenReturn(Optional.of(onStaff));
    }

    @Test
    @DisplayName("a branch manager is refused the owner's and the main manager's pay: by employee, salary record and advance")
    void branchManagerCannotTouchPayAboveThem() {
        assertThrows(AccessDeniedException.class, () -> access.assertEmployee(User.Role.MANAGER, 1L));
        assertThrows(AccessDeniedException.class, () -> access.assertEmployee(User.Role.MANAGER, 3L));
        assertThrows(AccessDeniedException.class, () -> access.assertSalary(User.Role.MANAGER, 10L));
        assertThrows(AccessDeniedException.class, () -> access.assertAdvance(User.Role.MANAGER, 100L));
    }

    @Test
    @DisplayName("a branch manager still runs payroll for staff and fellow managers")
    void branchManagerCanRunStaffPay() {
        assertDoesNotThrow(() -> access.assertEmployee(User.Role.MANAGER, 2L));
        assertDoesNotThrow(() -> access.assertEmployee(User.Role.MANAGER, 4L));
        assertDoesNotThrow(() -> access.assertSalary(User.Role.MANAGER, 20L));
        assertDoesNotThrow(() -> access.assertAdvance(User.Role.MANAGER, 200L));
    }

    @Test
    @DisplayName("a main manager sees everyone's pay except an owner's")
    void generalManagerCannotSeeOwnerPay() {
        assertThrows(AccessDeniedException.class, () -> access.assertEmployee(User.Role.GENERAL_MANAGER, 1L));
        assertDoesNotThrow(() -> access.assertEmployee(User.Role.GENERAL_MANAGER, 3L));
        assertDoesNotThrow(() -> access.assertEmployee(User.Role.GENERAL_MANAGER, 4L));
        assertEquals(Set.of(1L), access.hiddenEmployeeIds(User.Role.GENERAL_MANAGER));
    }

    @Test
    @DisplayName("an owner may open anyone's payroll, and nothing is hidden from the owner's lists")
    void ownerSeesAll() {
        for (User.Role owner : List.of(User.Role.ADMIN, User.Role.CAFE_ADMIN)) {
            assertDoesNotThrow(() -> access.assertEmployee(owner, 1L));
            assertDoesNotThrow(() -> access.assertSalary(owner, 10L));
            assertDoesNotThrow(() -> access.assertAdvance(owner, 100L));
            assertTrue(access.hiddenEmployeeIds(owner).isEmpty());
        }
    }

    @Test
    @DisplayName("for a branch manager the owner's and main manager's employee records are hidden from payroll lists")
    void higherRecordsAreHiddenFromBranchManagerLists() {
        assertEquals(Set.of(1L, 3L), access.hiddenEmployeeIds(User.Role.MANAGER));
    }

    @Test
    @DisplayName("ids that do not exist are left to the normal not-found handling, not turned into an access error")
    void unknownIdsPass() {
        assertDoesNotThrow(() -> access.assertEmployee(User.Role.MANAGER, 999L));
        assertDoesNotThrow(() -> access.assertSalary(User.Role.MANAGER, 999L));
        assertDoesNotThrow(() -> access.assertAdvance(User.Role.MANAGER, 999L));
        assertDoesNotThrow(() -> access.assertEmployee(User.Role.MANAGER, null));
    }

    @Test
    @DisplayName("the caller's role comes from the logged-in user; anything unrecognised is never treated as higher than a manager")
    void callerRoleIsNeverEscalated() {
        CafeUserDetails general = new CafeUserDetails("g", "x", true, List.of(), 1L, User.Role.GENERAL_MANAGER, null);
        assertEquals(User.Role.GENERAL_MANAGER,
                PayrollAccess.callerRole(new UsernamePasswordAuthenticationToken(general, null, general.getAuthorities())));
        assertEquals(User.Role.EMPLOYEE, PayrollAccess.callerRole(null));
        assertEquals(User.Role.MANAGER, PayrollAccess.callerRole(new UsernamePasswordAuthenticationToken(
                "plain", null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))));
    }
}
