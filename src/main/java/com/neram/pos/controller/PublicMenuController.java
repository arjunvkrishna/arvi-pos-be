package com.neram.pos.controller;

import com.neram.pos.dto.PublicMenuResponseDTO;
import com.neram.pos.service.PublicMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMenuController {

    private final PublicMenuService publicMenuService;

    @GetMapping("/menu")
    public ResponseEntity<?> getPublicMenu() {
        if (!publicMenuService.isPublicMenuEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(Map.of(
                            "status", 403,
                            "error", "Forbidden",
                            "enabled", false,
                            "message", "Public customer menu is currently disabled by management."
                    ));
        }

        PublicMenuResponseDTO response = publicMenuService.getPublicMenu();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header("Pragma", "no-cache")
                .body(response);
    }

    @GetMapping("/menu/status")
    public ResponseEntity<Map<String, Object>> getPublicMenuStatus() {
        boolean enabled = publicMenuService.isPublicMenuEnabled();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header("Pragma", "no-cache")
                .body(Map.of("enabled", enabled));
    }
}
