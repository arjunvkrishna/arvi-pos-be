package com.neram.pos.service;

import com.neram.pos.access.AccessPolicy;
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
        tenantSecurityService.resolveTenantId(null);
        Caller caller = caller(callerRole);
        return userRepository.findAll().stream()
                .filter(u -> caller.role() == User.Role.SUPER_ADMIN
                        || AccessPolicy.canSee(caller.role(), caller.outletId(), u.getRole(), u.getOutletId()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** One page of {@link #getUsers(String)}: the same visibility rules, ordered by id. */
    public org.springframework.data.domain.Page<UserDTO> getUsers(String callerRole, org.springframework.data.domain.Pageable pageable) {
        List<UserDTO> visible = getUsers(callerRole).stream()
                .sorted(java.util.Comparator.comparing(UserDTO::getId))
                .collect(Collectors.toList());
        int from = pageable.isPaged() ? (int) Math.min(pageable.getOffset(), visible.size()) : 0;
        int to = pageable.isPaged() ? Math.min(from + pageable.getPageSize(), visible.size()) : visible.size();
        return new org.springframework.data.domain.PageImpl<>(visible.subList(from, to), pageable, visible.size());
    }

    /** Who is asking: the logged-in user's role and branch, or just the role name when no user can be resolved. */
    private record Caller(User.Role role, Long outletId, boolean known) {}

    private Caller caller(String callerRole) {
        java.util.Optional<User> current = tenantSecurityService.getCurrentUser();
        if (current.isPresent()) {
            return new Caller(current.get().getRole(), current.get().getOutletId(), true);
        }
        User.Role role = callerRole == null ? User.Role.EMPLOYEE : User.Role.valueOf(callerRole.toUpperCase());
        return new Caller(role, null, false);
    }

    /** Public entry for controllers that act on another account (e.g. unlocking it). */
    public void assertCanManageUser(Long id, String callerRole) {
        assertCanManage(userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id)), callerRole);
    }

    /** Throws unless the caller outranks the target (and, for branch-level callers, shares its branch). */
    private void assertCanManage(User target, String callerRole) {
        Caller c = caller(callerRole);
        // Without a resolvable caller there is no branch to compare, so only the chain of command is enforced.
        Long compareOutlet = c.known() ? c.outletId() : target.getOutletId();
        if (!AccessPolicy.canManage(c.role(), compareOutlet, target.getRole(), target.getOutletId())) {
            throw new RuntimeException("Access denied: you cannot manage this account");
        }
    }

    public UserDTO getUserById(Long id, String callerRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        Caller c = caller(callerRole);
        if (c.role() != User.Role.SUPER_ADMIN && !AccessPolicy.canSee(c.role(), c.known() ? c.outletId() : user.getOutletId(),
                user.getRole(), user.getOutletId())) {
            throw new RuntimeException("Access denied: you cannot view this account");
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

        Caller creator = caller(creatorRole);
        if (!AccessPolicy.hasCafeWideAccess(creator.role()) && creator.known() && creator.outletId() != null) {
            // Branch-level creators can only add people to their own branch.
            dto.setOutletId(creator.outletId());
        }
        if (!AccessPolicy.canManage(creator.role(), creator.known() ? creator.outletId() : dto.getOutletId(),
                targetRole, dto.getOutletId() != null ? dto.getOutletId() : (creator.known() ? creator.outletId() : null))) {
            throw new RuntimeException(creator.role() == User.Role.MANAGER
                    ? "Access denied: Outlet Managers cannot create Manager or Admin accounts"
                    : creator.role() == User.Role.GENERAL_MANAGER
                    ? "Access denied: General Managers cannot create Admin accounts"
                    : "Access denied: you cannot create a " + targetRole + " account");
        }

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("Username already exists: " + dto.getUsername());
        }

        Long assignedOutletId = dto.getOutletId();

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
        user.setCanPrintBill(!isElevated && Boolean.TRUE.equals(dto.getCanPrintBill()));
        // Menu/settings rights are delegated by role level: owners and main managers always have them, so the toggle
        // only matters (and may be set at creation) for branch managers and staff.
        boolean cafeWide = AccessPolicy.hasCafeWideAccess(targetRole);
        user.setCanManageMenu(!cafeWide && Boolean.TRUE.equals(dto.getCanManageMenu()));
        user.setCanManageSettings(!cafeWide && Boolean.TRUE.equals(dto.getCanManageSettings()));

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

        assertCanManage(user, callerRole);

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
    public String changeOwnPassword(String username, String currentPassword, String newPassword) {
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
        // Other sessions end, but the caller keeps theirs: hand back a fresh refresh token.
        // Refresh tokens name their cafe so /auth/refresh can find the right database.
        String refreshToken = com.neram.pos.tenancy.TenantContext.require().code()
                + AuthService.REFRESH_SEPARATOR + refreshTokenService.issue(user);

        auditLogService.log("UPDATE", "USER", user.getId(), null,
                "User changed own password: " + username);
        return refreshToken;
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

        assertCanManage(user, callerRole);

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

        try {
            assertCanManage(user, callerRole);
        } catch (RuntimeException denied) {
            throw new RuntimeException(caller(callerRole).role() == User.Role.MANAGER
                    ? "Access denied: Outlet Managers can only manage permissions for staff accounts"
                    : denied.getMessage());
        }
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && auth.getName().equalsIgnoreCase(user.getUsername())) {
            throw new RuntimeException("Access denied: you cannot change your own permissions");
        }
        // You can only hand out what you hold yourself.
        if (dto.getCanManageMenu() != null && !holds(auth, AccessPolicy.PERM_MANAGE_MENU)) {
            throw new RuntimeException("Access denied: you cannot grant menu management");
        }
        if (dto.getCanManageSettings() != null && !holds(auth, AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS)) {
            throw new RuntimeException("Access denied: you cannot grant settings management");
        }
        if (dto.getOutletId() != null && !dto.getOutletId().equals(user.getOutletId())
                && !AccessPolicy.hasCafeWideAccess(caller(callerRole).role())) {
            throw new RuntimeException("Access denied: only the owner or main manager can move staff between branches");
        }

        if (dto.getCanBill() != null) user.setCanBill(dto.getCanBill());
        if (dto.getCanTakeOrders() != null) user.setCanTakeOrders(dto.getCanTakeOrders());
        if (dto.getCanManageInventory() != null) user.setCanManageInventory(dto.getCanManageInventory());
        if (dto.getCanManageExpenses() != null) user.setCanManageExpenses(dto.getCanManageExpenses());
        if (dto.getCanViewReports() != null) user.setCanViewReports(dto.getCanViewReports());
        if (dto.getCanPrintBill() != null) user.setCanPrintBill(dto.getCanPrintBill());
        if (dto.getCanManageMenu() != null) user.setCanManageMenu(dto.getCanManageMenu());
        if (dto.getCanManageSettings() != null) user.setCanManageSettings(dto.getCanManageSettings());
        if (dto.getOutletId() != null) user.setOutletId(dto.getOutletId());

        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        auditLogService.log("UPDATE", "USER", id, null,
                "Updated permissions for " + user.getUsername() + ": canBill=" + user.getCanBill() +
                ", canTakeOrders=" + user.getCanTakeOrders() + ", canManageInventory=" + user.getCanManageInventory() +
                ", canManageExpenses=" + user.getCanManageExpenses() + ", canViewReports=" + user.getCanViewReports() +
                ", canPrintBill=" + user.getCanPrintBill() + ", canManageMenu=" + user.getCanManageMenu()
                + ", canManageSettings=" + user.getCanManageSettings());
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
                .canPrintBill(isElevated || Boolean.TRUE.equals(user.getCanPrintBill()))
                .canManageMenu(AccessPolicy.managementAuthorities(user).contains(AccessPolicy.PERM_MANAGE_MENU))
                .canManageSettings(AccessPolicy.managementAuthorities(user).contains(AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS))
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

    private static boolean holds(org.springframework.security.core.Authentication auth, String authority) {
        return auth == null || auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static boolean isElevatedRole(User.Role role) {
        return role == User.Role.SUPER_ADMIN || role == User.Role.ADMIN || role == User.Role.CAFE_ADMIN
                || role == User.Role.GENERAL_MANAGER || role == User.Role.MANAGER;
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
                .canPrintBill(isElevatedRole(u.getRole()) || Boolean.TRUE.equals(u.getCanPrintBill()))
                .canManageMenu(AccessPolicy.managementAuthorities(u).contains(AccessPolicy.PERM_MANAGE_MENU))
                .canManageSettings(AccessPolicy.managementAuthorities(u).contains(AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS))
                .outletId(u.getOutletId())
                .outletName(outletName)
                .tenantId(u.getTenantId())
                .mustChangePassword(Boolean.TRUE.equals(u.getMustChangePassword()))
                .createdAt(u.getCreatedAt())
                .build();
    }
}
