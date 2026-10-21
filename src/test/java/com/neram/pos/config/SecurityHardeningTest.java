package com.neram.pos.config;

import com.neram.pos.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityHardeningTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String method, String uri, boolean mustChange) throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        when(provider.validateToken("tok")).thenReturn(true);
        when(provider.getUsernameFromToken("tok")).thenReturn("staff");
        UserDetailsService uds = mock(UserDetailsService.class);
        List<SimpleGrantedAuthority> auth = mustChange
                ? List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority(CustomUserDetailsService.PASSWORD_CHANGE_REQUIRED))
                : List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        UserDetails details = new User("staff", "x", auth);
        when(uds.loadUserByUsername("staff")).thenReturn(details);

        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new JwtAuthenticationFilter(provider, uds).doFilter(req, res, chain);
        res.setCommitted(false);
        lastChain = chain;
        return res;
    }

    private FilterChain lastChain;

    @Test
    @DisplayName("A temporary password blocks every API except change-password, own profile, auth and health")
    void tempPasswordBlocksEverythingElse() throws Exception {
        for (String[] r : new String[][]{{"GET", "/api/tables"}, {"POST", "/api/bills/order"}, {"GET", "/api/users"},
                {"GET", "/api/bills/stream"}, {"PATCH", "/api/users/me/change-password-x"}, {"POST", "/api/users/me"}}) {
            MockHttpServletResponse res = run(r[0], r[1], true);
            assertEquals(403, res.getStatus(), r[0] + " " + r[1]);
            assertTrue(res.getContentAsString().contains("Password change required"));
            verify(lastChain, never()).doFilter(any(), any());
        }
    }

    @Test
    @DisplayName("The change-password flow itself stays usable with a temporary password")
    void changePasswordFlowStillWorks() throws Exception {
        for (String[] r : new String[][]{{"PATCH", "/api/users/me/change-password"}, {"GET", "/api/users/me"},
                {"POST", "/api/auth/logout"}, {"POST", "/api/auth/refresh"}, {"GET", "/api/health"}}) {
            MockHttpServletResponse res = run(r[0], r[1], true);
            assertEquals(200, res.getStatus(), r[0] + " " + r[1]);
            verify(lastChain).doFilter(any(), any());
        }
    }

    @Test
    @DisplayName("Accounts without a temporary password are unaffected")
    void normalAccountsUnaffected() throws Exception {
        MockHttpServletResponse res = run("GET", "/api/tables", false);
        assertEquals(200, res.getStatus());
        verify(lastChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Exceptions without a message no longer crash the error handler")
    void nullMessageHandled() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> r = h.handleRuntimeException(new NullPointerException());
        assertEquals(400, r.getStatusCode().value());
        assertNotNull(r.getBody().get("error"));
        assertNotNull(h.handleIllegalArgument(new IllegalArgumentException()).getBody().get("error"));
    }

    @Test
    @DisplayName("Constraint violations return a readable message with no SQL, table or column names")
    void constraintMessagesAreSafe() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();
        String leaky = "could not execute statement [ERROR: duplicate key value violates unique constraint \"categories_name_key\"] [insert into categories]";
        ConstraintViolationException cve = new ConstraintViolationException(leaky, new SQLException("dup", "23505"), "categories_name_key");
        ResponseEntity<Map<String, String>> r = h.handleDataIntegrity(new DataIntegrityViolationException(leaky, cve));
        assertEquals(400, r.getStatusCode().value());
        String msg = r.getBody().get("error");
        assertFalse(msg.toLowerCase().contains("insert") || msg.contains("categories") || msg.contains("constraint"), msg);
        assertTrue(msg.contains("already exists"));

        ConstraintViolationException nn = new ConstraintViolationException("x", new SQLException("nn", "23502"), "c");
        assertTrue(h.handleDataIntegrity(new DataIntegrityViolationException("x", nn)).getBody().get("error").contains("required"));
        assertNotNull(h.handleDataIntegrity(new DataIntegrityViolationException("no cause")).getBody().get("error"));
    }
}
