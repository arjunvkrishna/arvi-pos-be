package com.neram.pos.controller;

import com.neram.pos.dto.LoginRequest;
import com.neram.pos.dto.LoginResponse;
import com.neram.pos.service.AuthService;
import com.neram.pos.service.LoginAttemptService;
import com.neram.pos.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
        try {
            return ResponseEntity.ok(authService.login(request, com.neram.pos.util.ClientIp.of(http)));
        } catch (LoginAttemptService.AccountLockedException e) {
            log.warn("Login refused for {}: locked ({}s left)", request.getUsername(), e.getRetryAfterSeconds());
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                    .body(Map.of("message", e.getMessage(), "retryAfterSeconds", e.getRetryAfterSeconds()));
        } catch (Exception e) {
            log.warn("Login failed for {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password: " + e.getMessage()));
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(authService.refresh(body == null ? null : body.get("refreshToken")));
        } catch (RefreshTokenService.InvalidRefreshTokenException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) Map<String, String> body) {
        authService.logout(body == null ? null : body.get("refreshToken"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "NERAM POS"));
    }
}
