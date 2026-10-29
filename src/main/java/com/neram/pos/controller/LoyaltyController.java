package com.neram.pos.controller;

import com.neram.pos.dto.LoyaltySettingsDTO;
import com.neram.pos.service.LoyaltySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
@com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.CRM)
public class LoyaltyController {

    private final LoyaltySettingsService settings;

    /** Managers can see the rules (the payment screen needs them); only an admin changes them. */
    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<LoyaltySettingsDTO> getSettings() {
        return ResponseEntity.ok(settings.get());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSettings(@RequestBody LoyaltySettingsDTO dto, Authentication auth) {
        try {
            return ResponseEntity.ok(settings.update(dto, auth != null ? auth.getName() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "error", e.getMessage()));
        }
    }
}
