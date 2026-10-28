package com.neram.pos.service;

import com.neram.pos.access.AccessPolicy;
import com.neram.pos.dto.EmployeeDTO;
import com.neram.pos.dto.EmployeeRequestDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    // ---- Chain of command (the same rule UserService applies to login accounts, see AccessPolicy) ----

    /** The caller's real role. Anything unrecognised is the lowest tier: unknown never means more access. */
    static User.Role callerOf(String callerRole) {
        if (callerRole == null) return User.Role.EMPLOYEE;
        try {
            return User.Role.valueOf(callerRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return User.Role.EMPLOYEE;
        }
    }

    private static User.Role asUserRole(Employee.Role role) {
        return User.Role.valueOf(role.name());
    }

    /** May the caller create, change or deactivate someone of this role? Only people strictly below them; owners may add owners. */
    static boolean outranks(User.Role caller, Employee.Role target) {
        return outranks(caller, asUserRole(target));
    }

    static boolean outranks(User.Role caller, User.Role target) {
        return AccessPolicy.rank(caller) > AccessPolicy.rank(target) || (AccessPolicy.isOwner(caller) && AccessPolicy.isOwner(target));
    }

    /** Management sees everyone up to their own tier; anyone else sees plain staff only. */
    static boolean canSee(User.Role caller, Employee.Role target) {
        if (!AccessPolicy.isManagement(caller)) return target == Employee.Role.EMPLOYEE;
        return AccessPolicy.rank(asUserRole(target)) <= AccessPolicy.rank(caller);
    }

    private static List<Employee.Role> visibleRoles(User.Role caller) {
        return Arrays.stream(Employee.Role.values()).filter(r -> canSee(caller, r)).toList();
    }

    private static void requireOutranks(User.Role caller, Employee.Role target, String doing) {
        if (!outranks(caller, target)) {
            throw new AccessDeniedException("Access denied: you cannot " + doing + " someone at or above your own role.");
        }
    }

    public List<EmployeeDTO> getAllEmployees(String callerRole) {
        User.Role caller = callerOf(callerRole);
        return employeeRepository.findByActiveTrue()
                .stream()
                .filter(emp -> canSee(caller, emp.getRole()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EmployeeDTO> getEmployeesByRole(String roleStr, String callerRole) {
        Employee.Role role = Employee.Role.valueOf(roleStr.toUpperCase());
        if (!canSee(callerOf(callerRole), role)) {
            throw new AccessDeniedException("Access denied: you cannot view accounts above your own role.");
        }
        return employeeRepository.findByRoleAndActiveTrue(role)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** One page of {@link #getAllEmployees(String)} (same visibility rules), ordered by id. */
    public org.springframework.data.domain.Page<EmployeeDTO> getAllEmployees(String callerRole, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Pageable sorted = com.neram.pos.util.PagedResponse.withSort(pageable, org.springframework.data.domain.Sort.by("id"));
        User.Role caller = callerOf(callerRole);
        org.springframework.data.domain.Page<Employee> page = AccessPolicy.isOwner(caller)
                ? employeeRepository.findByActiveTrue(sorted)
                : employeeRepository.findByActiveTrueAndRoleIn(visibleRoles(caller), sorted);
        return page.map(this::toDTO);
    }

    /** One page of {@link #getEmployeesByRole(String, String)}, ordered by id. */
    public org.springframework.data.domain.Page<EmployeeDTO> getEmployeesByRole(String roleStr, String callerRole, org.springframework.data.domain.Pageable pageable) {
        Employee.Role role = Employee.Role.valueOf(roleStr.toUpperCase());
        if (!canSee(callerOf(callerRole), role)) {
            throw new AccessDeniedException("Access denied: you cannot view accounts above your own role.");
        }
        return employeeRepository.findByRoleAndActiveTrue(role, com.neram.pos.util.PagedResponse.withSort(pageable, org.springframework.data.domain.Sort.by("id")))
                .map(this::toDTO);
    }

    public EmployeeDTO getEmployeeById(Long id, String callerRole) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        if (!canSee(callerOf(callerRole), emp.getRole())) {
            throw new AccessDeniedException("Access denied: you cannot view accounts above your own role.");
        }
        return toDTO(emp);
    }

    @Transactional
    public EmployeeDTO createEmployee(EmployeeRequestDTO dto, String creatorRole) {
        Employee.Role targetRole = Employee.Role.valueOf(dto.getRole().toUpperCase());
        requireOutranks(callerOf(creatorRole), targetRole, "create");

        Employee emp = new Employee();
        emp.setName(dto.getName());
        emp.setPhone(dto.getPhone());
        emp.setEmail(dto.getEmail());
        emp.setRole(targetRole);
        emp.setDepartment(dto.getDepartment());
        emp.setDesignation(dto.getDesignation());
        emp.setBasicSalary(dto.getBasicSalary() != null ? dto.getBasicSalary() : 0.0);
        emp.setJoinedDate(dto.getJoinedDate() != null ? dto.getJoinedDate() : LocalDate.now());
        emp.setAddress(dto.getAddress());
        emp.setPaidLeavesQuota(dto.getPaidLeavesQuota() != null ? dto.getPaidLeavesQuota() : 12);
        emp.setCasualLeavesQuota(dto.getCasualLeavesQuota() != null ? dto.getCasualLeavesQuota() : 12);
        emp.setBankName(dto.getBankName());
        emp.setBankAccountNumber(dto.getBankAccountNumber());
        emp.setBankIfsc(dto.getBankIfsc());
        emp.setBankBranch(dto.getBankBranch());
        emp.setBankAccountHolderName(dto.getBankAccountHolderName());
        emp.setUpiId(dto.getUpiId());
        emp.setActive(true);
        emp.setCreatedAt(LocalDateTime.now());
        emp.setUpdatedAt(LocalDateTime.now());

        Employee saved = employeeRepository.save(emp);

        // Optionally create User account for login
        if (dto.getUsername() != null && !dto.getUsername().isBlank()
                && dto.getPassword() != null && !dto.getPassword().isBlank()) {
            com.neram.pos.util.PasswordValidator.validate(dto.getPassword());
            if (!userRepository.existsByUsername(dto.getUsername())) {
                User user = new User();
                user.setUsername(dto.getUsername());
                user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
                user.setRole(User.Role.valueOf(targetRole.name()));
                user.setEmployeeId(saved.getId());
                user.setOutletId(saved.getOutletId());
                user.setMustChangePassword(true);
                user.setActive(true);
                userRepository.save(user);
            }
        }

        auditLogService.log("CREATE", "EMPLOYEE", saved.getId(), null, "Created " + saved.getName() + " (" + saved.getRole() + ")");
        return toDTO(saved);
    }

    @Transactional
    public EmployeeDTO updateEmployee(Long id, EmployeeRequestDTO dto, String updaterRole) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

        User.Role updater = callerOf(updaterRole);
        requireOutranks(updater, emp.getRole(), "change");

        String oldValue = emp.toString();
        if (dto.getName() != null && !dto.getName().isBlank()) {
            emp.setName(dto.getName());
        }
        if (dto.getPhone() != null) {
            emp.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            emp.setEmail(dto.getEmail());
        }
        if (dto.getRole() != null) {
            Employee.Role newRole = Employee.Role.valueOf(dto.getRole().toUpperCase());
            requireOutranks(updater, newRole, "promote anyone to");
            emp.setRole(newRole);
        }
        if (dto.getDepartment() != null) emp.setDepartment(dto.getDepartment());
        if (dto.getDesignation() != null) emp.setDesignation(dto.getDesignation());
        if (dto.getBasicSalary() != null) emp.setBasicSalary(dto.getBasicSalary());
        if (dto.getJoinedDate() != null) emp.setJoinedDate(dto.getJoinedDate());
        if (dto.getAddress() != null) emp.setAddress(dto.getAddress());
        if (dto.getPaidLeavesQuota() != null) emp.setPaidLeavesQuota(dto.getPaidLeavesQuota());
        if (dto.getCasualLeavesQuota() != null) emp.setCasualLeavesQuota(dto.getCasualLeavesQuota());
        if (dto.getBankName() != null) emp.setBankName(dto.getBankName());
        if (dto.getBankAccountNumber() != null) emp.setBankAccountNumber(dto.getBankAccountNumber());
        if (dto.getBankIfsc() != null) emp.setBankIfsc(dto.getBankIfsc());
        if (dto.getBankBranch() != null) emp.setBankBranch(dto.getBankBranch());
        if (dto.getBankAccountHolderName() != null) emp.setBankAccountHolderName(dto.getBankAccountHolderName());
        if (dto.getUpiId() != null) emp.setUpiId(dto.getUpiId());
        if (dto.getActive() != null) emp.setActive(dto.getActive());
        emp.setUpdatedAt(LocalDateTime.now());

        Employee updated = employeeRepository.save(emp);
        auditLogService.log("UPDATE", "EMPLOYEE", updated.getId(), oldValue, updated.toString());
        return toDTO(updated);
    }

    @Transactional
    public void deleteEmployee(Long id, String deleterRole) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

        User.Role deleter = callerOf(deleterRole);
        requireOutranks(deleter, emp.getRole(), "deactivate");
        // Deactivating the employee also switches off the login linked to it: that login's own role must be below the caller's too.
        userRepository.findByEmployeeId(id).ifPresent(u -> {
            if (!outranks(deleter, u.getRole())) {
                throw new AccessDeniedException("Access denied: you cannot deactivate someone at or above your own role.");
            }
        });

        emp.setActive(false);
        emp.setUpdatedAt(LocalDateTime.now());
        employeeRepository.save(emp);

        userRepository.findByEmployeeId(id).ifPresent(u -> {
            u.setActive(false);
            userRepository.save(u);
        });

        auditLogService.log("DELETE", "EMPLOYEE", id, emp.getName(), "Deactivated employee " + emp.getName());
    }

    public EmployeeDTO toDTO(Employee emp) {
        return EmployeeDTO.builder()
                .id(emp.getId())
                .name(emp.getName())
                .phone(emp.getPhone())
                .email(emp.getEmail())
                .role(emp.getRole().name())
                .department(emp.getDepartment())
                .designation(emp.getDesignation())
                .basicSalary(emp.getBasicSalary())
                .joinedDate(emp.getJoinedDate())
                .address(emp.getAddress())
                .paidLeavesQuota(emp.getPaidLeavesQuota() != null ? emp.getPaidLeavesQuota() : 12)
                .casualLeavesQuota(emp.getCasualLeavesQuota() != null ? emp.getCasualLeavesQuota() : 12)
                .bankName(emp.getBankName())
                .bankAccountNumber(emp.getBankAccountNumber())
                .bankIfsc(emp.getBankIfsc())
                .bankBranch(emp.getBankBranch())
                .bankAccountHolderName(emp.getBankAccountHolderName())
                .upiId(emp.getUpiId())
                .active(emp.getActive())
                .createdAt(emp.getCreatedAt())
                .build();
    }
}
