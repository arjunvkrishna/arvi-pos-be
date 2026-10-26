package com.neram.pos.controller;

import com.neram.pos.dto.GstSettingDTO;
import com.neram.pos.service.GstSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gst")
@RequiredArgsConstructor
public class GstSettingController {

    private final GstSettingService gstSettingService;

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<GstSettingDTO> getGstConfig() {
        return ResponseEntity.ok(gstSettingService.getSettingsDTO());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<GstSettingDTO> updateGstConfig(@RequestBody GstSettingDTO dto) {
        return ResponseEntity.ok(gstSettingService.updateSettings(dto));
    }
}
