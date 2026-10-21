package com.neram.pos.service;

import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAccountGuardTest {

    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private RefreshTokenService refreshTokenService;
    @InjectMocks private UserService userService;

    private User user(long id, String name, User.Role role, boolean active) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setRole(role);
        u.setActive(active);
        return u;
    }

    @Test
    @DisplayName("A user cannot delete their own account")
    void cannotDeleteSelf() {
        User admin = user(1, "admin", User.Role.ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(1L, "admin"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("The last active admin cannot be deleted by anyone")
    void cannotDeleteLastAdmin() {
        User admin = user(1, "admin", User.Role.ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndActiveTrue(User.Role.ADMIN)).thenReturn(List.of(admin));
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(1L, "someone-else"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("An admin can delete another admin while a second active admin remains")
    void canDeleteAdminWhenAnotherRemains() {
        User a1 = user(1, "admin", User.Role.ADMIN, true);
        User a2 = user(7, "admin2", User.Role.ADMIN, true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(a2));
        when(userRepository.findByRoleAndActiveTrue(User.Role.ADMIN)).thenReturn(List.of(a1, a2));
        userService.deleteUser(7L, "admin");
        verify(userRepository).delete(a2);
    }

    @Test
    @DisplayName("Deactivating yourself or the last active admin is refused")
    void cannotDeactivateSelfOrLastAdmin() {
        User admin = user(1, "admin", User.Role.ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndActiveTrue(User.Role.ADMIN)).thenReturn(List.of(admin));
        assertThrows(IllegalArgumentException.class, () -> userService.toggleUserActive(1L, "ADMIN", "admin"));
        assertThrows(IllegalArgumentException.class, () -> userService.toggleUserActive(1L, "ADMIN", "other"));
        assertTrue(admin.getActive());
    }
}
