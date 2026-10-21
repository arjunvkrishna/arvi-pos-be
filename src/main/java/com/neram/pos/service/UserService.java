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
    private final com.neram.pos.repository.TenantRepository tenantRepository;
    private final com.neram.pos.repository.OutletRepository outletRepository;
    private final TenantSecurityService tenantSecurityService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Get users visible to the caller based on their role:
     * - SUPER_ADMIN: sees all users across platform
     * - ADMIN / CAFE_ADMIN / GENERAL_MANAGER: sees all managers and staff across all outlets under current tenant
     * - MANAGER: sees only staff belonging to their specific assigned outlet
     */
    public List<UserDTO> getUsers(String callerRole) {
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        java.util.Optional<User> callerOpt = tenantSecurityService.getCurrentUser();
        List<User> users;

        if ("SUPER_ADMIN".equalsIgnoreCase(callerRole)) {
            users = userRepository.findAll();
        } else if ("ADMIN".equalsIgnoreCase(callerRole) || "CAFE_ADMIN".equalsIgnoreCase(callerRole) || "GENERAL_MANAGER".equalsIgnoreCase(callerRole)) {
            users = userRepository.findAll().stream()
                    .filter(u -> tenantId.equals(u.getTenantId()))
                    .filter(u -> u.getRole() != User.Role.SUPER_ADMIN && ("ADMIN".equalsIgnoreCase(callerRole) || u.getRole() != User.Role.ADMIN))
                    .collect(Collectors.toList());
        } else {
            Long callerOutletId = callerOpt.map(User::getOutletId).orElse(null);
            users = userRepository.findAll().stream()
                    .filter(u -> tenantId.equals(u.getTenantId()))
                    .filter(u -> callerOutletId == null || callerOutletId.equals(u.getOutletId()))
                    .filter(u -> u.getRole() != User.Role.ADMIN && u.getRole() != User.Role.CAFE_ADMIN && u.getRole() != User.Role.GENERAL_MANAGER)
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
     * Create a new user account:
     * - SUPER_ADMIN / ADMIN / CAFE_ADMIN: can create GENERAL_MANAGER, MANAGER, and staff across any outlet
     * - GENERAL_MANAGER: can create branch MANAGERs and staff across any outlet under the cafe brand
     * - MANAGER: can only create staff for their specific assigned outlet
     */
    @Transactional
    public UserDTO createUser(CreateUserRequestDTO dto, String creatorRole) {
        User.Role targetRole = User.Role.valueOf(dto.getRole().toUpperCase());
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        java.util.Optional<User> callerOpt = tenantSecurityService.getCurrentUser();

        if ("MANAGER".equalsIgnoreCase(creatorRole)) {
            if (targetRole == User.Role.ADMIN || targetRole == User.Role.CAFE_ADMIN || targetRole == User.Role.GENERAL_MANAGER || targetRole == User.Role.MANAGER) {
                throw new RuntimeException("Access denied: Outlet Managers cannot create Manager or Admin accounts");
            }
        } else if ("GENERAL_MANAGER".equalsIgnoreCase(creatorRole)) {
            if (targetRole == User.Role.ADMIN || targetRole == User.Role.CAFE_ADMIN || targetRole == User.Role.SUPER_ADMIN) {
                throw new RuntimeException("Access denied: General Managers cannot create Admin accounts");
            }
        }

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("Username already exists: " + dto.getUsername());
        }

        Long assignedOutletId = dto.getOutletId();
        if ("MANAGER".equalsIgnoreCase(creatorRole)) {
            assignedOutletId = callerOpt.map(User::getOutletId).orElse(assignedOutletId);
        }

        if (targetRole == User.Role.MANAGER && assignedOutletId == null) {
            throw new IllegalArgumentException("Outlet assignment is required when creating an Outlet Branch Manager");
        }

        if (dto.getEmployeeId() != null) {
            Employee emp = employeeRepository.findById(dto.getEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Employee not found with id: " + dto.getEmployeeId()));
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
        user.setTenantId(tenantId);
        user.setOutletId(assignedOutletId);

        boolean isElevated = targetRole == User.Role.ADMIN || 
                             targetRole == User.Role.CAFE_ADMIN || 
                             targetRole == User.Role.GENERAL_MANAGER || 
                             targetRole == User.Role.MANAGER;

        user.setCanBill(isElevated || Boolean.TRUE.equals(dto.getCanBill()) || targetRole == User.Role.CASHIER);
        user.setCanTakeOrders(isElevated || Boolean.TRUE.equals(dto.getCanTakeOrders()) || targetRole == User.Role.CAPTAIN || targetRole == User.Role.CASHIER);
        user.setCanManageInventory(isElevated || Boolean.TRUE.equals(dto.getCanManageInventory()));
        user.setCanManageExpenses(isElevated || Boolean.TRUE.equals(dto.getCanManageExpenses()));
        user.setCanViewReports(isElevated || Boolean.TRUE.equals(dto.getCanViewReports()));

        user.setMustChangePassword(true);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        auditLogService.log("CREATE", "USER", saved.getId(), null,
                "Created user: " + saved.getUsername() + " with role " + saved.getRole() + " for outlet " + saved.getOutletId());
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
     * Update feature permissions for a user:
     * - SUPER_ADMIN / ADMIN / CAFE_ADMIN / GENERAL_MANAGER: can update permissions for branch managers and staff
     * - MANAGER: can update permissions for staff in their outlet only
     */
    @Transactional
    public UserDTO updateUserPermissions(Long id, com.neram.pos.dto.UpdateUserPermissionsRequestDTO dto, String callerRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if ("MANAGER".equalsIgnoreCase(callerRole) && user.getRole() != User.Role.EMPLOYEE && user.getRole() != User.Role.CASHIER && user.getRole() != User.Role.CAPTAIN && user.getRole() != User.Role.KITCHEN) {
            throw new RuntimeException("Access denied: Outlet Managers can only manage permissions for staff accounts");
        }

        if (dto.getCanBill() != null) user.setCanBill(dto.getCanBill());
        if (dto.getCanTakeOrders() != null) user.setCanTakeOrders(dto.getCanTakeOrders());
        if (dto.getCanManageInventory() != null) user.setCanManageInventory(dto.getCanManageInventory());
        if (dto.getCanManageExpenses() != null) user.setCanManageExpenses(dto.getCanManageExpenses());
        if (dto.getCanViewReports() != null) user.setCanViewReports(dto.getCanViewReports());
        if (dto.getOutletId() != null) user.setOutletId(dto.getOutletId());

        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        auditLogService.log("UPDATE", "USER", id, null,
                "Updated permissions for " + user.getUsername() + ": canBill=" + user.getCanBill() +
                ", canTakeOrders=" + user.getCanTakeOrders() + ", canManageInventory=" + user.getCanManageInventory() +
                ", canManageExpenses=" + user.getCanManageExpenses() + ", canViewReports=" + user.getCanViewReports());
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

        boolean isElevated = user.getRole() == User.Role.SUPER_ADMIN ||
                             user.getRole() == User.Role.ADMIN ||
                             user.getRole() == User.Role.CAFE_ADMIN ||
                             user.getRole() == User.Role.GENERAL_MANAGER ||
                             user.getRole() == User.Role.MANAGER;

        boolean canBill = isElevated || Boolean.TRUE.equals(user.getCanBill());
        boolean canTakeOrders = isElevated || Boolean.TRUE.equals(user.getCanTakeOrders());
        boolean canManageInventory = isElevated || Boolean.TRUE.equals(user.getCanManageInventory());
        boolean canManageExpenses = isElevated || Boolean.TRUE.equals(user.getCanManageExpenses());
        boolean canViewReports = isElevated || Boolean.TRUE.equals(user.getCanViewReports());

        String tenantName = null;
        String logoUrl = null;
        if (user.getTenantId() != null) {
            var tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            if (tenant != null) {
                tenantName = tenant.getName();
                logoUrl = tenant.getLogoUrl();
            }
        }

        String outletName = null;
        if (user.getOutletId() != null) {
            var outlet = outletRepository.findById(user.getOutletId()).orElse(null);
            if (outlet != null) {
                outletName = outlet.getName();
            }
        }

        return com.neram.pos.dto.UserProfileDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .active(user.getActive())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .canBill(canBill)
                .canTakeOrders(canTakeOrders)
                .canManageInventory(canManageInventory)
                .canManageExpenses(canManageExpenses)
                .canViewReports(canViewReports)
                .tenantId(user.getTenantId())
                .tenantName(tenantName)
                .outletId(user.getOutletId())
                .outletName(outletName)
                .cafeLogoUrl(logoUrl)
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

    /**
     * Update profile details for the currently logged-in user:
     * Full Name, Phone, Email, and Address.
     */
    @Transactional
    public com.neram.pos.dto.UserProfileDTO updateMyProfile(String username, com.neram.pos.dto.UpdateProfileRequestDTO dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));

        Employee emp = null;
        if (user.getEmployeeId() != null) {
            emp = employeeRepository.findById(user.getEmployeeId()).orElse(null);
        }
        if (emp == null) {
            emp = new Employee();
            emp.setName(dto.getName() != null && !dto.getName().isBlank() ? dto.getName().trim() : user.getUsername());
            emp.setRole(Employee.Role.valueOf(user.getRole().name().equals("SUPER_ADMIN") ? "ADMIN" : user.getRole().name()));
            emp.setTenantId(user.getTenantId());
            emp.setOutletId(user.getOutletId());
            emp = employeeRepository.save(emp);
            user.setEmployeeId(emp.getId());
            userRepository.save(user);
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            emp.setName(dto.getName().trim());
        }
        if (dto.getPhone() != null) {
            emp.setPhone(dto.getPhone().trim());
        }
        if (dto.getEmail() != null) {
            emp.setEmail(dto.getEmail().trim());
        }
        if (dto.getAddress() != null) {
            emp.setAddress(dto.getAddress().trim());
        }
        emp.setUpdatedAt(LocalDateTime.now());
        employeeRepository.save(emp);

        auditLogService.log("UPDATE", "USER_PROFILE", user.getId(), null, "User updated profile: " + username);
        return getMyProfile(username);
    }

    private UserDTO toDTO(User u) {
        String employeeName = u.getUsername();
        if (u.getEmployeeId() != null) {
            Employee emp = employeeRepository.findById(u.getEmployeeId()).orElse(null);
            if (emp != null) employeeName = emp.getName();
        }

        boolean isElevated = u.getRole() == User.Role.SUPER_ADMIN ||
                             u.getRole() == User.Role.ADMIN ||
                             u.getRole() == User.Role.CAFE_ADMIN ||
                             u.getRole() == User.Role.GENERAL_MANAGER ||
                             u.getRole() == User.Role.MANAGER;

        boolean canBill = isElevated || Boolean.TRUE.equals(u.getCanBill());
        boolean canTakeOrders = isElevated || Boolean.TRUE.equals(u.getCanTakeOrders());
        boolean canManageInventory = isElevated || Boolean.TRUE.equals(u.getCanManageInventory());
        boolean canManageExpenses = isElevated || Boolean.TRUE.equals(u.getCanManageExpenses());
        boolean canViewReports = isElevated || Boolean.TRUE.equals(u.getCanViewReports());

        String outletName = null;
        if (u.getOutletId() != null) {
            var outlet = outletRepository.findById(u.getOutletId()).orElse(null);
            if (outlet != null) outletName = outlet.getName();
        }

        return UserDTO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .role(u.getRole().name())
                .employeeId(u.getEmployeeId())
                .employeeName(employeeName)
                .active(u.getActive())
                .canBill(canBill)
                .canTakeOrders(canTakeOrders)
                .canManageInventory(canManageInventory)
                .canManageExpenses(canManageExpenses)
                .canViewReports(canViewReports)
                .outletId(u.getOutletId())
                .outletName(outletName)
                .tenantId(u.getTenantId())
                .mustChangePassword(Boolean.TRUE.equals(u.getMustChangePassword()))
                .createdAt(u.getCreatedAt())
                .build();
    }
}
