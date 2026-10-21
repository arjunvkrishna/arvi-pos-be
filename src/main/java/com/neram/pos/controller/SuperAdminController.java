package com.neram.pos.controller;

import com.neram.pos.dto.CafeSummaryDto;
import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @GetMapping("/cafes")
    public ResponseEntity<List<CafeSummaryDto>> getAllCafes() {
        return ResponseEntity.ok(superAdminService.getAllCafes());
    }

    @PostMapping("/cafes")
    public ResponseEntity<?> createCafe(@RequestBody CreateCafeRequest request) {
        try {
            return ResponseEntity.ok(superAdminService.createCafe(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/cafes/{id}")
    public ResponseEntity<?> updateCafe(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        try {
            return ResponseEntity.ok(superAdminService.updateCafeStatus(id, updates));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getPlatformAnalytics() {
        return ResponseEntity.ok(superAdminService.getPlatformAnalytics());
    }
}
