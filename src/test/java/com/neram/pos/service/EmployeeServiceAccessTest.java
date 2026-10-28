package com.neram.pos.service;

import com.neram.pos.dto.EmployeeRequestDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Staff records follow the chain of command: owner > main (general) manager > branch manager > staff.
 * A person may only create, change or deactivate people strictly below them, and deactivating an employee also switches
 * off the login linked to it, so that login counts too.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceAccessTest {

    @Mock private EmployeeRepository employees;
    @Mock private UserRepository users;
    @Mock private PasswordEncoder encoder;
    @Mock private AuditLogService audit;

    private EmployeeService service;

    private static Employee emp(long id, Employee.Role role) {
        Employee e = new Employee();
        e.setId(id);
        e.setName("n" + id);
        e.setRole(role);
        e.setActive(true);
        return e;
    }

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employees, users, encoder, audit);
        for (Employee.Role r : Employee.Role.values()) {
            lenient().when(employees.findById((long) (100 + r.ordinal()))).thenReturn(Optional.of(emp(100 + r.ordinal(), r)));
        }
        lenient().when(employees.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(employees.findByActiveTrue()).thenReturn(
                java.util.Arrays.stream(Employee.Role.values()).map(r -> emp(100 + r.ordinal(), r)).toList());
    }

    private static long idOf(Employee.Role r) {
        return 100L + r.ordinal();
    }

    private static EmployeeRequestDTO request(Employee.Role role) {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setName("New Person");
        dto.setRole(role.name());
        return dto;
    }

    @Test
    @DisplayName("a general manager cannot deactivate the owner's record - and with it the owner's login")
    void generalManagerCannotDeactivateOwner() {
        User ownerLogin = new User();
        ownerLogin.setRole(User.Role.ADMIN);
        when(users.findByEmployeeId(idOf(Employee.Role.ADMIN))).thenReturn(Optional.of(ownerLogin));

        assertThrows(AccessDeniedException.class, () -> service.deleteEmployee(idOf(Employee.Role.ADMIN), "GENERAL_MANAGER"));
        verify(employees, never()).save(any());
        verify(users, never()).save(any());
        assertTrue(Boolean.TRUE.equals(ownerLogin.getActive()) || ownerLogin.getActive() == null, "the owner's login must stay active");
    }

    @Test
    @DisplayName("a general manager cannot change or create an owner, but can run branch managers and staff")
    void generalManagerRunsEveryoneBelow() {
        assertThrows(AccessDeniedException.class, () -> service.updateEmployee(idOf(Employee.Role.ADMIN), new EmployeeRequestDTO(), "GENERAL_MANAGER"));
        assertThrows(AccessDeniedException.class, () -> service.createEmployee(request(Employee.Role.ADMIN), "GENERAL_MANAGER"));
        assertThrows(AccessDeniedException.class, () -> service.createEmployee(request(Employee.Role.GENERAL_MANAGER), "GENERAL_MANAGER"));

        assertDoesNotThrow(() -> service.createEmployee(request(Employee.Role.MANAGER), "GENERAL_MANAGER"));
        assertDoesNotThrow(() -> service.createEmployee(request(Employee.Role.CASHIER), "GENERAL_MANAGER"));
        assertDoesNotThrow(() -> service.updateEmployee(idOf(Employee.Role.MANAGER), new EmployeeRequestDTO(), "GENERAL_MANAGER"));
    }

    @Test
    @DisplayName("a branch manager runs cashiers, captains, kitchen and staff - but cannot create a peer, a general manager or an owner")
    void branchManagerRunsStaffOnly() {
        for (Employee.Role ok : List.of(Employee.Role.CASHIER, Employee.Role.CAPTAIN, Employee.Role.KITCHEN, Employee.Role.EMPLOYEE)) {
            assertDoesNotThrow(() -> service.createEmployee(request(ok), "MANAGER"), "should be allowed to create " + ok);
        }
        for (Employee.Role no : List.of(Employee.Role.MANAGER, Employee.Role.GENERAL_MANAGER, Employee.Role.ADMIN)) {
            assertThrows(AccessDeniedException.class, () -> service.createEmployee(request(no), "MANAGER"), "must not create " + no);
            assertThrows(AccessDeniedException.class, () -> service.updateEmployee(idOf(no), new EmployeeRequestDTO(), "MANAGER"), "must not change " + no);
            assertThrows(AccessDeniedException.class, () -> service.deleteEmployee(idOf(no), "MANAGER"), "must not deactivate " + no);
        }
    }

    @Test
    @DisplayName("nobody can promote a person to or above their own role")
    void cannotPromoteAboveSelf() {
        EmployeeRequestDTO promote = new EmployeeRequestDTO();
        promote.setRole(Employee.Role.MANAGER.name());
        assertThrows(AccessDeniedException.class, () -> service.updateEmployee(idOf(Employee.Role.EMPLOYEE), promote, "MANAGER"));
        promote.setRole(Employee.Role.ADMIN.name());
        assertThrows(AccessDeniedException.class, () -> service.updateEmployee(idOf(Employee.Role.MANAGER), promote, "GENERAL_MANAGER"));
    }

    @Test
    @DisplayName("owners (ADMIN and CAFE_ADMIN) may manage everyone, including other owners")
    void ownersManageEveryone() {
        for (String owner : List.of("ADMIN", "CAFE_ADMIN")) {
            for (Employee.Role r : Employee.Role.values()) {
                assertDoesNotThrow(() -> service.updateEmployee(idOf(r), new EmployeeRequestDTO(), owner), owner + " -> " + r);
            }
        }
    }

    @Test
    @DisplayName("who sees whom: owner all, general manager up to general manager, branch manager up to manager, staff only staff")
    void visibility() {
        assertEquals(Employee.Role.values().length, service.getAllEmployees("ADMIN").size());
        assertEquals(Employee.Role.values().length, service.getAllEmployees("CAFE_ADMIN").size());

        List<String> general = service.getAllEmployees("GENERAL_MANAGER").stream().map(d -> d.getRole()).toList();
        assertFalse(general.contains("ADMIN"), "a general manager must not see the owner");
        assertTrue(general.contains("GENERAL_MANAGER") && general.contains("MANAGER") && general.contains("CASHIER"));

        List<String> branch = service.getAllEmployees("MANAGER").stream().map(d -> d.getRole()).toList();
        assertFalse(branch.contains("ADMIN") || branch.contains("GENERAL_MANAGER"), "a branch manager must not see owners or main managers");
        assertTrue(branch.contains("MANAGER") && branch.contains("EMPLOYEE"));

        assertEquals(List.of("EMPLOYEE"), service.getAllEmployees("EMPLOYEE").stream().map(d -> d.getRole()).toList());
        assertThrows(AccessDeniedException.class, () -> service.getEmployeeById(idOf(Employee.Role.ADMIN), "GENERAL_MANAGER"));
        assertThrows(AccessDeniedException.class, () -> service.getEmployeesByRole("GENERAL_MANAGER", "MANAGER"));
    }

    @Test
    @DisplayName("an unknown or missing role is treated as the lowest tier, never as an administrator")
    void unknownIsLowest() {
        for (String weird : new String[]{null, "", "ROOT", "SUPERUSER"}) {
            assertThrows(AccessDeniedException.class, () -> service.createEmployee(request(Employee.Role.EMPLOYEE), weird), "role=" + weird);
            assertThrows(AccessDeniedException.class, () -> service.deleteEmployee(idOf(Employee.Role.EMPLOYEE), weird), "role=" + weird);
        }
    }
}
