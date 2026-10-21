package com.neram.pos.service;

import com.neram.pos.dto.CreateUserRequestDTO;
import com.neram.pos.dto.UserDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Get users visible to the caller based on their role:
     * - ADMIN: sees all users
     * - MANAGER: sees MANAGERs and EMPLOYEEs only (not ADMINs)
     * - EMPLOYEE: not allowed (handled at controller level)
     */
    public List<UserDTO> getUsers(String callerRole) {
        List<User> users;
        if ("ADMIN".equalsIgnoreCase(callerRole)) {
            users = userRepository.findAll();
        } else {
            // MANAGER sees MANAGER and EMPLOYEE accounts only
            users = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != User.Role.ADMIN)
                    .collect(Collectors.toList());
        }
        return users.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id, String callerRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        if ("MANAGER".equalsIgnoreCase(callerRole) && user.getRole() == User.Role.ADMIN) {
            throw new RuntimeException("Access denied: Managers cannot view Admin accounts");
        }
        return toDTO(user);
    }

    /**
     * Create a new user account.
     * - ADMIN: can create users of any role (ADMIN, MANAGER, EMPLOYEE)
     * - MANAGER: can only create EMPLOYEE accounts
     */
    @Transactional
    public UserDTO createUser(CreateUserRequestDTO dto, String creatorRole) {
        User.Role targetRole = User.Role.valueOf(dto.getRole().toUpperCase());

        if ("MANAGER".equalsIgnoreCase(creatorRole) && targetRole != User.Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only create Employee accounts");
        }

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("Username already exists: " + dto.getUsername());
        }

        if (dto.getEmployeeId() != null) {
            Employee emp = employeeRepository.findById(dto.getEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Employee not found with id: " + dto.getEmployeeId()));
            if (!emp.getRole().name().equals(targetRole.name())) {
                throw new IllegalArgumentException(String.format(
                        "Role mismatch: Cannot link user account with role %s to employee '%s' with role %s",
                        targetRole, emp.getName(), emp.getRole()));
            }
            userRepository.findByEmployeeId(dto.getEmployeeId()).ifPresent(existing -> {
                throw new IllegalArgumentException(String.format(
                        "Employee '%s' is already linked to user account '%s'",
                        emp.getName(), existing.getUsername()));
            });
        }

        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        com.neram.pos.util.PasswordValidator.validate(dto.getPassword());

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRole(targetRole);
        user.setEmployeeId(dto.getEmployeeId());

        if (targetRole == User.Role.ADMIN || targetRole == User.Role.MANAGER) {
            user.setCanTakeOrders(true);
            user.setCanManageInventory(true);
        } else {
            user.setCanTakeOrders(dto.getCanTakeOrders() != null ? dto.getCanTakeOrders() : true);
            user.setCanManageInventory(dto.getCanManageInventory() != null ? dto.getCanManageInventory() : false);
        }

        user.setMustChangePassword(true);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        auditLogService.log("CREATE", "USER", saved.getId(), null,
                "Created user: " + saved.getUsername() + " with role " + saved.getRole());
        return toDTO(saved);
    }

    /**
     * Reset/update a user's password.
     * - ADMIN: can reset any user's password
     * - MANAGER: can reset EMPLOYEE passwords only
     */
    @Transactional
    public UserDTO resetPassword(Long id, String newPassword, String callerRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if ("MANAGER".equalsIgnoreCase(callerRole) && user.getRole() != User.Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only reset passwords for Employee accounts");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password cannot be empty");
        }
        com.neram.pos.util.PasswordValidator.validate(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log("UPDATE", "USER", id, null, "Password reset for user: " + user.getUsername());
        return toDTO(user);
    }

    /**
     * Change own password — any authenticated user can call this.
     * Requires current password verification before allowing the change.
     */
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (currentPassword.equals(newPassword)) {
            throw new RuntimeException("New password cannot be the same as your current password");
        }

        com.neram.pos.util.PasswordValidator.validate(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log("UPDATE", "USER", user.getId(), null,
                "User changed own password: " + username);
    }

    /**
     * Toggle active status of a user.
     * - ADMIN: can toggle any non-self user
     * - MANAGER: can only toggle EMPLOYEE accounts
     */
    @Transactional
    public UserDTO toggleUserActive(Long id, String callerRole, String callerUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (callerUsername != null && callerUsername.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("You cannot activate or deactivate your own account");
        }
        if (Boolean.TRUE.equals(user.getActive())) {
            assertNotLastActiveAdmin(user);
        }

        if ("MANAGER".equalsIgnoreCase(callerRole) && user.getRole() != User.Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only manage Employee accounts");
        }

        user.setActive(!user.getActive());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        if (!Boolean.TRUE.equals(saved.getActive())) {
            refreshTokenService.revokeAllForUser(saved.getId());
        }

        auditLogService.log("UPDATE", "USER", id, null,
                "User " + user.getUsername() + " set active=" + user.getActive());
        return toDTO(saved);
    }

    /**
     * Update feature permissions for a user.
     * - ADMIN: can update permissions for any user (MANAGER, EMPLOYEE)
     * - MANAGER: can update permissions for EMPLOYEE users only
     */
    @Transactional
    public UserDTO updateUserPermissions(Long id, com.neram.pos.dto.UpdateUserPermissionsRequestDTO dto, String callerRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if ("MANAGER".equalsIgnoreCase(callerRole) && user.getRole() != User.Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only manage permissions for Employee accounts");
        }

        if (dto.getCanTakeOrders() != null) {
            user.setCanTakeOrders(dto.getCanTakeOrders());
        }
        if (dto.getCanManageInventory() != null) {
            user.setCanManageInventory(dto.getCanManageInventory());
        }
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        auditLogService.log("UPDATE", "USER", id, null,
                "Updated permissions for " + user.getUsername() + ": canTakeOrders=" + user.getCanTakeOrders() +
                ", canManageInventory=" + user.getCanManageInventory());
        return toDTO(saved);
    }

    /**
     * Delete a user (only ADMIN can do this).
     */
    @Transactional
    public void deleteUser(Long id, String callerUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        if (callerUsername != null && callerUsername.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        assertNotLastActiveAdmin(user);
        refreshTokenService.revokeAllForUser(user.getId());
        userRepository.delete(user);
        auditLogService.log("DELETE", "USER", id, user.getUsername(), "Deleted user: " + user.getUsername());
    }

    /** Guards against locking everyone out: the last active ADMIN can be neither deleted nor deactivated. */
    private void assertNotLastActiveAdmin(User user) {
        if (user.getRole() == User.Role.ADMIN
                && userRepository.findByRoleAndActiveTrue(User.Role.ADMIN).size() <= 1) {
            throw new IllegalArgumentException("Cannot remove or deactivate the last active admin account");
        }
    }

    /**
     * Get profile for the currently logged-in user.
     * Accessible by all authenticated users (Admin, Manager, Employee, etc.)
     * Returns personal employee details, roles, permissions, and bank account information (view-only).
     */
    public com.neram.pos.dto.UserProfileDTO getMyProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));

        Employee emp = null;
        if (user.getEmployeeId() != null) {
            emp = employeeRepository.findById(user.getEmployeeId()).orElse(null);
        }

        boolean canTakeOrders = user.getRole() == User.Role.ADMIN ||
                user.getRole() == User.Role.MANAGER ||
                Boolean.TRUE.equals(user.getCanTakeOrders());

        boolean canManageInventory = user.getRole() == User.Role.ADMIN ||
                user.getRole() == User.Role.MANAGER ||
                Boolean.TRUE.equals(user.getCanManageInventory());

        return com.neram.pos.dto.UserProfileDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .active(user.getActive())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .canTakeOrders(canTakeOrders)
                .canManageInventory(canManageInventory)
                .accountCreatedAt(user.getCreatedAt())
                .employeeId(emp != null ? emp.getId() : null)
                .employeeName(emp != null ? emp.getName() : user.getUsername())
                .phone(emp != null ? emp.getPhone() : null)
                .email(emp != null ? emp.getEmail() : null)
                .department(emp != null ? emp.getDepartment() : null)
                .designation(emp != null ? emp.getDesignation() : null)
                .basicSalary(emp != null ? emp.getBasicSalary() : null)
                .joinedDate(emp != null ? emp.getJoinedDate() : null)
                .address(emp != null ? emp.getAddress() : null)
                .paidLeavesQuota(emp != null ? emp.getPaidLeavesQuota() : null)
                .casualLeavesQuota(emp != null ? emp.getCasualLeavesQuota() : null)
                .bankName(emp != null ? emp.getBankName() : null)
                .bankAccountNumber(emp != null ? emp.getBankAccountNumber() : null)
                .bankIfsc(emp != null ? emp.getBankIfsc() : null)
                .bankBranch(emp != null ? emp.getBankBranch() : null)
                .bankAccountHolderName(emp != null ? emp.getBankAccountHolderName() : null)
                .upiId(emp != null ? emp.getUpiId() : null)
                .build();
    }

    private UserDTO toDTO(User u) {
        String employeeName = u.getUsername();
        if (u.getEmployeeId() != null) {
            Employee emp = employeeRepository.findById(u.getEmployeeId()).orElse(null);
            if (emp != null) employeeName = emp.getName();
        }

        boolean canTakeOrders = u.getRole() == User.Role.ADMIN ||
                                u.getRole() == User.Role.MANAGER ||
                                Boolean.TRUE.equals(u.getCanTakeOrders());

        boolean canManageInventory = u.getRole() == User.Role.ADMIN ||
                                     u.getRole() == User.Role.MANAGER ||
                                     Boolean.TRUE.equals(u.getCanManageInventory());

        return UserDTO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .role(u.getRole().name())
                .employeeId(u.getEmployeeId())
                .employeeName(employeeName)
                .active(u.getActive())
                .canTakeOrders(canTakeOrders)
                .canManageInventory(canManageInventory)
                .mustChangePassword(Boolean.TRUE.equals(u.getMustChangePassword()))
                .createdAt(u.getCreatedAt())
                .build();
    }
}
