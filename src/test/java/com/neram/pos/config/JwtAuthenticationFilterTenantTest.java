package com.neram.pos.config;

import com.neram.pos.tenancy.PlatformAdminService;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The filter is what binds a request to exactly one cafe; these tests pin down how it decides which. */
class JwtAuthenticationFilterTenantTest {

    private JwtTokenProvider provider;
    private UserDetailsService users;
    private TenantRegistry registry;
    private PlatformAdminService platform;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        provider = mock(JwtTokenProvider.class);
        users = mock(UserDetailsService.class);
        registry = mock(TenantRegistry.class);
        platform = mock(PlatformAdminService.class);
        filter = new JwtAuthenticationFilter(provider, users, registry, platform, mock(com.neram.pos.access.OutletScopeResolver.class));
        when(users.loadUserByUsername("staff")).thenReturn(new User("staff", "x", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private TenantRecord cafe(long id, String code, String status) {
        return new TenantRecord(id, code, code, status, "PRO", 1, null, null, null, "h", 5432, "db", "u", "e", "e",
                null, null, null, null);
    }

    private AtomicReference<Long> run(MockHttpServletRequest req, MockHttpServletResponse res) throws Exception {
        AtomicReference<Long> cafeSeenByController = new AtomicReference<>();
        FilterChain chain = (rq, rs) -> cafeSeenByController.set(TenantContext.currentId().orElse(null));
        filter.doFilter(req, res, chain);
        return cafeSeenByController;
    }

    private MockHttpServletRequest authed(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.addHeader("Authorization", "Bearer tok");
        return req;
    }

    @Test
    void cafeComesFromTheSignedTokenNotFromAHeader() throws Exception {
        when(provider.validateToken("tok")).thenReturn(true);
        when(provider.getIdentity("tok")).thenReturn(new JwtTokenProvider.TokenIdentity("staff", 5L, "bluedoor", false));
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        MockHttpServletRequest req = authed("/api/categories");
        req.addHeader("X-Tenant-Code", "greenleaf");

        AtomicReference<Long> seen = run(req, new MockHttpServletResponse());

        assertEquals(5L, seen.get());
        verify(registry, never()).findByCode("greenleaf");
        assertTrue(TenantContext.current().isEmpty(), "context must be cleared after the request");
    }

    @Test
    void aTokenWhoseCafeCodeDoesNotMatchTheRegistryIsNotAuthenticated() throws Exception {
        when(provider.validateToken("tok")).thenReturn(true);
        when(provider.getIdentity("tok")).thenReturn(new JwtTokenProvider.TokenIdentity("staff", 5L, "greenleaf", false));
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));

        AtomicReference<Long> seen = run(authed("/api/categories"), new MockHttpServletResponse());

        assertNull(seen.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
    }

    @Test
    void suspendedCafeIsBlockedEvenWithAValidToken() throws Exception {
        when(provider.validateToken("tok")).thenReturn(true);
        when(provider.getIdentity("tok")).thenReturn(new JwtTokenProvider.TokenIdentity("staff", 5L, "bluedoor", false));
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "SUSPENDED")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<Long> seen = run(authed("/api/categories"), res);

        assertEquals(403, res.getStatus());
        assertNull(seen.get());
    }

    @Test
    void platformTokensGetNoCafeAtAll() throws Exception {
        when(provider.validateToken("tok")).thenReturn(true);
        when(provider.getIdentity("tok")).thenReturn(new JwtTokenProvider.TokenIdentity("owner", null, null, true));
        PlatformAdminService.PlatformAdmin admin = new PlatformAdminService.PlatformAdmin(1, "owner", "h", "Owner", true, false);
        when(platform.findByUsername("owner")).thenReturn(Optional.of(admin));
        when(platform.toUserDetails(admin)).thenReturn(new User("owner", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        AtomicReference<Long> seen = run(authed("/api/super-admin/cafes"), new MockHttpServletResponse());

        assertNull(seen.get());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
    }

    @Test
    void legacyTokensWithoutACafeAreNotAccepted() throws Exception {
        when(provider.validateToken("tok")).thenReturn(true);
        when(provider.getIdentity("tok")).thenReturn(new JwtTokenProvider.TokenIdentity("staff", null, null, false));

        run(authed("/api/categories"), new MockHttpServletResponse());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void publicMenuTakesTheCafeFromTheUrl() throws Exception {
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));

        AtomicReference<Long> seen = run(new MockHttpServletRequest("GET", "/api/public/bluedoor/menu"), new MockHttpServletResponse());

        assertEquals(5L, seen.get());
    }

    @Test
    void publicMenuOfAnUnknownOrInactiveCafeStopsTheRequest() throws Exception {
        when(registry.findByCode("ghost")).thenReturn(Optional.empty());
        when(registry.findByCode("paused")).thenReturn(Optional.of(cafe(7, "paused", "SUSPENDED")));
        MockHttpServletResponse unknown = new MockHttpServletResponse();
        MockHttpServletResponse inactive = new MockHttpServletResponse();

        AtomicReference<Long> a = run(new MockHttpServletRequest("GET", "/api/public/ghost/menu"), unknown);
        AtomicReference<Long> b = run(new MockHttpServletRequest("GET", "/api/public/paused/menu"), inactive);

        assertEquals(404, unknown.getStatus());
        assertEquals(403, inactive.getStatus());
        assertNull(a.get());
        assertNull(b.get());
    }

    @Test
    void printBridgeNamesItsCafeInAHeaderAndOtherPathsIgnoreThatHeader() throws Exception {
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        MockHttpServletRequest bridge = new MockHttpServletRequest("GET", "/api/printer/bridge/poll");
        bridge.addHeader("X-Tenant-Code", "bluedoor");
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/categories");
        other.addHeader("X-Tenant-Code", "bluedoor");

        assertEquals(5L, run(bridge, new MockHttpServletResponse()).get());
        assertNull(run(other, new MockHttpServletResponse()).get());
    }
}
