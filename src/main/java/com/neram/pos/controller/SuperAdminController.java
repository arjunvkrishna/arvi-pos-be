package com.neram.pos.controller;

import com.neram.pos.dto.CafeSummaryDto;
import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.service.SuperAdminService;
import com.neram.pos.tenancy.PlatformAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final PlatformAdminService platformAdminService;

    @GetMapping("/cafes")
    public ResponseEntity<List<CafeSummaryDto>> getAllCafes() {
        return ResponseEntity.ok(superAdminService.getAllCafes());
    }

    @GetMapping("/cafes/{id}")
    public ResponseEntity<?> getCafe(@PathVariable Long id) {
        return guarded(() -> superAdminService.getCafe(id));
    }

    @PostMapping("/cafes")
    public ResponseEntity<?> createCafe(@RequestBody CreateCafeRequest request, Authentication auth) {
        return guarded(() -> superAdminService.createCafe(request, auth.getName()));
    }

    @PutMapping("/cafes/{id}")
    public ResponseEntity<?> updateCafe(@PathVariable Long id, @RequestBody Map<String, Object> updates, Authentication auth) {
        return guarded(() -> superAdminService.updateCafe(id, updates, auth.getName()));
    }

    /** Applies pending schema migrations to one cafe's database (they also run automatically on startup). */
    @PostMapping("/cafes/{id}/migrate")
    public ResponseEntity<?> migrate(@PathVariable Long id) {
        return guarded(() -> superAdminService.migrate(id));
    }

    @GetMapping("/cafes/{id}/events")
    public ResponseEntity<?> events(@PathVariable Long id) {
        return guarded(() -> superAdminService.events(id));
    }

    @GetMapping("/cafes/{id}/db-credentials")
    public ResponseEntity<?> dbCredentials(@PathVariable Long id, Authentication auth) {
        return guarded(() -> superAdminService.revealDbCredentials(id, auth.getName()));
    }

    @PostMapping("/cafes/{id}/db-credentials/rotate")
    public ResponseEntity<?> rotateDbCredentials(@PathVariable Long id, Authentication auth) {
        return guarded(() -> superAdminService.rotateDbPassword(id, auth.getName()));
    }

    @GetMapping("/cafes/{id}/bridge-token")
    public ResponseEntity<?> bridgeToken(@PathVariable Long id, Authentication auth) {
        return guarded(() -> superAdminService.revealBridgeToken(id, auth.getName()));
    }

    @PostMapping("/cafes/{id}/bridge-token/rotate")
    public ResponseEntity<?> rotateBridgeToken(@PathVariable Long id, Authentication auth) {
        return guarded(() -> superAdminService.rotateBridgeToken(id, auth.getName()));
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getPlatformAnalytics() {
        return ResponseEntity.ok(superAdminService.getPlatformAnalytics());
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, Authentication auth) {
        try {
            platformAdminService.changePassword(auth.getName(), body.get("currentPassword"), body.get("newPassword"));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<?> guarded(java.util.function.Supplier<Object> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Super admin operation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }
}
