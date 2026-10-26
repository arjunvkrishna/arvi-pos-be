package com.neram.pos.service;

import com.neram.pos.config.JwtTokenProvider;
import com.neram.pos.dto.LoginRequest;
import com.neram.pos.dto.LoginResponse;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.OutletRepository;
import com.neram.pos.repository.TenantRepository;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.tenancy.PlatformAdminService;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTenantTest {

    private AuthenticationManager authManager;
    private JwtTokenProvider jwt;
    private TenantRegistry registry;
    private PlatformAdminService platform;
    private RefreshTokenService refreshTokens;
    private UserRepository users;
    private AuthService service;

    @BeforeEach
    void setUp() {
        authManager = mock(AuthenticationManager.class);
        jwt = mock(JwtTokenProvider.class);
        registry = mock(TenantRegistry.class);
        platform = mock(PlatformAdminService.class);
        refreshTokens = mock(RefreshTokenService.class);
        users = mock(UserRepository.class);
        service = new AuthService(authManager, jwt, users, mock(EmployeeRepository.class), mock(AuditLogService.class),
                refreshTokens, mock(TenantRepository.class), mock(OutletRepository.class), mock(LoginAttemptService.class), registry, platform);
    }

    private static TenantRecord cafe(String status) {
        return new TenantRecord(5, "bluedoor", "Blue", status, "PRO", 1, null, null, null, "h", 5432, "db", "u", "e",
                "e", null, null, null, null);
    }

    private LoginRequest request(String code, String user, String pw) {
        LoginRequest r = new LoginRequest();
        r.setTenantCode(code);
        r.setUsername(user);
        r.setPassword(pw);
        return r;
    }

    @Test
    void cafeLoginAuthenticatesInsideThatCafeAndBakesItIntoTheToken() {
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(cafe("ACTIVE")));
        AtomicLong cafeDuringAuth = new AtomicLong();
        Authentication auth = new UsernamePasswordAuthenticationToken("owner", "pw");
        when(authManager.authenticate(any())).thenAnswer(i -> {
            cafeDuringAuth.set(TenantContext.requireId());
            return auth;
        });
        when(jwt.generateToken(auth, 5L, "bluedoor")).thenReturn("JWT");
        com.neram.pos.entity.User user = new com.neram.pos.entity.User();
        user.setId(1L);
        user.setUsername("owner");
        user.setRole(com.neram.pos.entity.User.Role.ADMIN);
        when(users.findByUsername("owner")).thenReturn(Optional.of(user));
        when(refreshTokens.issue(user)).thenReturn("RAW");

        LoginResponse res = service.login(request("  BlueDoor ", "owner", "pw"));

        assertEquals(5L, cafeDuringAuth.get());
        assertEquals("JWT", res.getToken());
        assertEquals("bluedoor~RAW", res.getRefreshToken());
        assertTrue(TenantContext.current().isEmpty());
    }

    @Test
    void unknownAndInactiveCafesCannotLogIn() {
        when(registry.findByCode("ghost")).thenReturn(Optional.empty());
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(cafe("SUSPENDED")));

        assertThrows(BadCredentialsException.class, () -> service.login(request("ghost", "u", "p")));
        assertThrows(BadCredentialsException.class, () -> service.login(request("bluedoor", "u", "p")));
        verifyNoInteractions(authManager);
    }

    @Test
    void noCafeCodeMeansAPlatformLoginAgainstTheControlDatabase() {
        PlatformAdminService.PlatformAdmin admin = new PlatformAdminService.PlatformAdmin(1, "superadmin", "h", "Owner", true, false);
        when(platform.authenticate("superadmin", "pw")).thenReturn(Optional.of(admin));
        when(jwt.generatePlatformToken("superadmin")).thenReturn("PLATFORM-JWT");

        LoginResponse res = service.login(request("", "superadmin", "pw"));

        assertEquals("PLATFORM-JWT", res.getToken());
        assertEquals("SUPER_ADMIN", res.getRole());
        assertNull(res.getTenantId());
        verifyNoInteractions(authManager);
    }

    @Test
    void platformLoginWithWrongPasswordFails() {
        when(platform.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> service.login(request(null, "superadmin", "bad")));
    }

    @Test
    void refreshTokensCarryTheirCafeAndAreRotatedInThatCafe() {
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(cafe("ACTIVE")));
        com.neram.pos.entity.User user = new com.neram.pos.entity.User();
        user.setUsername("owner");
        user.setRole(com.neram.pos.entity.User.Role.ADMIN);
        AtomicLong cafeDuringRotate = new AtomicLong();
        when(refreshTokens.rotate("RAW")).thenAnswer(i -> {
            cafeDuringRotate.set(TenantContext.requireId());
            return new RefreshTokenService.Rotation(user, "RAW2");
        });
        when(jwt.generateTokenFromUsername("owner", 5L, "bluedoor")).thenReturn("JWT2");

        LoginResponse res = service.refresh("bluedoor~RAW");

        assertEquals(5L, cafeDuringRotate.get());
        assertEquals("JWT2", res.getToken());
        assertEquals("bluedoor~RAW2", res.getRefreshToken());
    }

    @Test
    void malformedOrForeignRefreshTokensAreRejected() {
        when(registry.findByCode("ghost")).thenReturn(Optional.empty());
        for (String bad : new String[]{null, "", "RAWONLY", "~RAW", "bluedoor~", "ghost~RAW"}) {
            assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.refresh(bad), String.valueOf(bad));
        }
        verify(refreshTokens, never()).rotate(anyString());
    }

    @Test
    void logoutRevokesInsideTheTokensOwnCafeAndIgnoresGarbage() {
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(cafe("ACTIVE")));
        AtomicLong cafeDuringRevoke = new AtomicLong();
        doAnswer(i -> {
            cafeDuringRevoke.set(TenantContext.requireId());
            return null;
        }).when(refreshTokens).revoke("RAW");

        service.logout("bluedoor~RAW");
        service.logout("garbage");
        service.logout(null);

        assertEquals(5L, cafeDuringRevoke.get());
        verify(refreshTokens, times(1)).revoke(anyString());
    }
}
