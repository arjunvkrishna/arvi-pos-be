package com.neram.pos.service;

import com.neram.pos.controller.AuthController;
import com.neram.pos.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthLockoutControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(authService);

    private LoginRequest login(String u, String p) {
        LoginRequest r = new LoginRequest();
        r.setUsername(u); r.setPassword(p);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void lockedAccountGets429WithRetryAfterAndAClearMessage() {
        when(authService.login(any(), any())).thenThrow(new LoginAttemptService.AccountLockedException(840));

        ResponseEntity<?> res = controller.login(login("admin", "Admin@2026"), new MockHttpServletRequest());

        assertEquals(429, res.getStatusCode().value());
        assertEquals("840", res.getHeaders().getFirst("Retry-After"));
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertEquals("Too many failed login attempts. Try again in 14 minutes.", body.get("message"));
        assertEquals(840L, ((Number) body.get("retryAfterSeconds")).longValue());
    }

    @Test
    void wrongPasswordStillGets401() {
        when(authService.login(any(), any())).thenThrow(new BadCredentialsException("Bad credentials"));
        ResponseEntity<?> res = controller.login(login("admin", "nope"), new MockHttpServletRequest());
        assertEquals(401, res.getStatusCode().value());
    }

    @Test
    void theRealClientAddressIsUsedNotTheProxy() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.5");                      // nginx / tunnel container
        req.addHeader("X-Real-IP", "172.18.0.1");
        req.addHeader("CF-Connecting-IP", "203.0.113.7");     // the real visitor behind Cloudflare
        when(authService.login(any(), eq("203.0.113.7"))).thenReturn(null);

        controller.login(login("admin", "x"), req);

        verify(authService).login(any(), eq("203.0.113.7"));
    }

    @Test
    void withoutCloudflareTheNginxRealIpIsUsed() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.5");
        req.addHeader("X-Real-IP", "192.168.1.20");
        when(authService.login(any(), eq("192.168.1.20"))).thenReturn(null);
        controller.login(login("admin", "x"), req);
        verify(authService).login(any(), eq("192.168.1.20"));
    }
}
