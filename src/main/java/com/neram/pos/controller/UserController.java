package com.neram.pos.controller;

import com.neram.pos.dto.CreateUserRequestDTO;
import com.neram.pos.dto.UserDTO;
import com.neram.pos.service.AuditLogService;
import com.neram.pos.service.LoginAttemptService;
import com.neram.pos.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;

    /**
     * GET /api/users/me
     * Available to ANY authenticated user (Admin, Manager, Employee, etc.).
     * Returns view-only user profile including employee and bank details.
     */
    @GetMapping("/me")
    public ResponseEntity<com.neram.pos.dto.UserProfileDTO> getMyProfile(Authentication auth) {
        String username = auth.getName();
        return ResponseEntity.ok(userService.getMyProfile(username));
    }

    /**
     * PUT /api/users/me
     * Allows any authenticated user to update their personal details (name, phone, email, address).
     */
    @PutMapping("/me")
    public ResponseEntity<com.neram.pos.dto.UserProfileDTO> updateMyProfile(@RequestBody com.neram.pos.dto.UpdateProfileRequestDTO body, Authentication auth) {
        String username = auth.getName();
        return ResponseEntity.ok(userService.updateMyProfile(username, body));
    }

    /**
     * PATCH /api/users/me/change-password or POST /api/users/me/change-password
     * Available to ANY authenticated user.
     * Body: { "currentPassword": "...", "newPassword": "..." }
     */
    @RequestMapping(value = "/me/change-password", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<?> changeOwnPassword(@RequestBody Map<String, String> body, Authentication auth) {
        String username = auth.getName();
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "currentPassword is required"));
        }
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "newPassword is required"));
        }
        try {
            String refreshToken = userService.changeOwnPassword(username, currentPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully", "refreshToken", refreshToken));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * GET /api/users
     * Admin & General Manager: all users in brand
     * Outlet Manager: staff in their assigned outlet only
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_STAFF')")
    public ResponseEntity<List<UserDTO>> getUsers(Authentication auth, Pageable pageable) {
        String callerRole = getRole(auth);
        Page<UserDTO> page = userService.getUsers(callerRole, pageable);
        List<UserDTO> users = page.getContent();
        // Mark accounts that are currently locked after repeated failed logins
        java.util.Map<String, java.time.LocalDateTime> locked = loginAttemptService.lockedUntil(
                users.stream().map(UserDTO::getUsername).toList());
        users.forEach(u -> {
            java.time.LocalDateTime until = locked.get(LoginAttemptService.keyFor(u.getUsername()));
            u.setLocked(until != null);
            u.setLockedUntil(until);
        });
        return PagedResponse.of(page);
    }

    /**
     * POST /api/users/{id}/unlock
     * Lifts a login lockout. Admin: any account. Manager: Employee accounts only (same rule as deactivate).
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('PERM_MANAGE_STAFF')")
    public ResponseEntity<?> unlock(@PathVariable Long id, Authentication auth) {
        String callerRole = getRole(auth);
        try {
            UserDTO target = userService.getUserById(id, callerRole);
            userService.assertCanManageUser(id, callerRole);
            loginAttemptService.unlock(target.getUsername());
            auditLogService.log("UNLOCK_ACCOUNT", "USER", id, null, "Login lock removed for " + target.getUsername());
            return ResponseEntity.ok(Map.of("message", "Account unlocked"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_STAFF')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id, Authentication auth) {
        String callerRole = getRole(auth);
        return ResponseEntity.ok(userService.getUserById(id, callerRole));
    }

    /**
     * POST /api/users
     * Admin: can create any role
     * Manager: can only create EMPLOYEE role
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_STAFF')")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequestDTO dto, Authentication auth) {
        String callerRole = getRole(auth);
        try {
            return ResponseEntity.ok(userService.createUser(dto, callerRole));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "error", e.getMessage()));
        }
    }

    /**
     * PATCH /api/users/{id}/reset-password
     * Body: { "newPassword": "..." }
     */
    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERM_MANAGE_STAFF')")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        String callerRole = getRole(auth);
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "newPassword is required"));
        }
        try {
            return ResponseEntity.ok(userService.resetPassword(id, newPassword, callerRole));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * PATCH /api/users/{id}/toggle-active
     * Toggle user active/inactive
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('PERM_MANAGE_STAFF')")
    public ResponseEntity<?> toggleActive(@PathVariable Long id, Authentication auth) {
        String callerRole = getRole(auth);
        try {
            return ResponseEntity.ok(userService.toggleUserActive(id, callerRole, auth != null ? auth.getName() : null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * PATCH /api/users/{id}/permissions
     * Body: { "canTakeOrders": true, "canManageInventory": false, "canCancelOrder": false }
     */
    @RequestMapping(value = "/{id}/permissions", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PERM_MANAGE_STAFF')")
    public ResponseEntity<?> updatePermissions(@PathVariable Long id,
                                                @RequestBody com.neram.pos.dto.UpdateUserPermissionsRequestDTO body,
                                                Authentication auth) {
        String callerRole = getRole(auth);
        try {
            return ResponseEntity.ok(userService.updateUserPermissions(id, body, callerRole));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/users/{id}
     * Admin & General Manager can delete staff
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAFE_ADMIN', 'GENERAL_MANAGER')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication auth) {
        try {
            userService.deleteUser(id, auth != null ? auth.getName() : null);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private String getRole(Authentication auth) {
        if (auth == null) return "UNKNOWN";
        return auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("UNKNOWN");
    }
}
