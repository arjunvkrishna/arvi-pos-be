package com.neram.pos.service;

import com.neram.pos.dto.EmployeeDTO;
import com.neram.pos.dto.EmployeeRequestDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public List<EmployeeDTO> getAllEmployees(String callerRole) {
        if ("ADMIN".equalsIgnoreCase(callerRole)) {
            // Admin sees all users/employees
            return employeeRepository.findByActiveTrue()
                    .stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } else if ("MANAGER".equalsIgnoreCase(callerRole)) {
            // Manager can see other managers and staff (excluding ADMIN)
            return employeeRepository.findByActiveTrue()
                    .stream()
                    .filter(emp -> emp.getRole() != Employee.Role.ADMIN)
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } else {
            // Regular employees only see staff
            return employeeRepository.findByRoleAndActiveTrue(Employee.Role.EMPLOYEE)
                    .stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
    }

    public List<EmployeeDTO> getEmployeesByRole(String roleStr, String callerRole) {
        Employee.Role role = Employee.Role.valueOf(roleStr.toUpperCase());
        if ("MANAGER".equalsIgnoreCase(callerRole) && role == Employee.Role.ADMIN) {
            throw new RuntimeException("Access denied: Managers cannot view Admin accounts");
        }
        return employeeRepository.findByRoleAndActiveTrue(role)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public EmployeeDTO getEmployeeById(Long id, String callerRole) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        if ("MANAGER".equalsIgnoreCase(callerRole) && emp.getRole() == Employee.Role.ADMIN) {
            throw new RuntimeException("Access denied: Managers cannot view Admin accounts");
        }
        return toDTO(emp);
    }

    @Transactional
    public EmployeeDTO createEmployee(EmployeeRequestDTO dto, String creatorRole) {
        Employee.Role targetRole = Employee.Role.valueOf(dto.getRole().toUpperCase());
        if ("MANAGER".equalsIgnoreCase(creatorRole) && targetRole != Employee.Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only create Staff/Employee accounts. Only Admin can create Manager or Admin accounts.");
        }

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

        if ("MANAGER".equalsIgnoreCase(updaterRole) && emp.getRole() != Employee.Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only modify Staff/Employee accounts");
        }

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
            if ("MANAGER".equalsIgnoreCase(updaterRole) && newRole != Employee.Role.EMPLOYEE) {
                throw new RuntimeException("Access denied: Managers cannot elevate roles to Manager or Admin");
            }
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

        if ("MANAGER".equalsIgnoreCase(deleterRole) && emp.getRole() != Employee.Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only deactivate Staff/Employee accounts");
        }

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
