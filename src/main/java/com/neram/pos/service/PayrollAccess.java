package com.neram.pos.service;

import com.neram.pos.access.AccessPolicy;
import com.neram.pos.access.CafeUserDetails;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.Salary;
import com.neram.pos.entity.SalaryAdvance;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.SalaryAdvanceRepository;
import com.neram.pos.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may open whose payroll. Pay follows the chain of command: a person can run payroll for people at or below their
 * own tier, never for someone above. So a branch manager cannot see the main manager's or an owner's pay, a main manager
 * cannot see an owner's, and an owner sees everyone's. Which branch a person works in is handled separately by the
 * branch filter on the payroll tables.
 */
@Component
@RequiredArgsConstructor
public class PayrollAccess {

    private static final String DENIED = "You cannot access the payroll of someone above your own role.";

    private final EmployeeRepository employeeRepository;
    private final SalaryRepository salaryRepository;
    private final SalaryAdvanceRepository advanceRepository;

    /** The role the caller acts with. Anything unrecognised is treated as the lowest management tier, never higher. */
    public static User.Role callerRole(Authentication auth) {
        if (auth == null) return User.Role.EMPLOYEE;
        if (auth.getPrincipal() instanceof CafeUserDetails cafeUser && cafeUser.getRole() != null) {
            return cafeUser.getRole();
        }
        boolean owner = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return owner ? User.Role.ADMIN : User.Role.MANAGER;
    }

    /** Employees whose payroll this caller must not see: everyone ranked above the caller's own tier. */
    public Set<Long> hiddenEmployeeIds(User.Role caller) {
        if (AccessPolicy.isOwner(caller)) return Collections.emptySet();
        List<Employee.Role> above = Arrays.stream(Employee.Role.values()).filter(r -> isAbove(r, caller)).toList();
        if (above.isEmpty()) return Collections.emptySet();
        return employeeRepository.findByRoleIn(above).stream().map(Employee::getId).collect(Collectors.toSet());
    }

    public void assertEmployee(User.Role caller, Long employeeId) {
        if (AccessPolicy.isOwner(caller) || employeeId == null) return;
        if (employeeRepository.findById(employeeId).map(e -> isAbove(e.getRole(), caller)).orElse(false)) {
            throw new AccessDeniedException(DENIED);
        }
    }

    public void assertSalary(User.Role caller, Long salaryId) {
        if (AccessPolicy.isOwner(caller) || salaryId == null) return;
        salaryRepository.findById(salaryId).map(Salary::getEmployeeId).ifPresent(id -> assertEmployee(caller, id));
    }

    public void assertAdvance(User.Role caller, Long advanceId) {
        if (AccessPolicy.isOwner(caller) || advanceId == null) return;
        advanceRepository.findById(advanceId).map(SalaryAdvance::getSalaryId).ifPresent(id -> assertSalary(caller, id));
    }

    private static boolean isAbove(Employee.Role employeeRole, User.Role caller) {
        if (employeeRole == null) return false;
        return AccessPolicy.rank(User.Role.valueOf(employeeRole.name())) > AccessPolicy.rank(caller);
    }
}
