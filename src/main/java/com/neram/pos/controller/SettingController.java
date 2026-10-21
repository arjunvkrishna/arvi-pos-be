package com.neram.pos.controller;

import com.neram.pos.service.AppSettingService;
import com.neram.pos.service.PublicMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final AppSettingService appSettingService;
    private final PublicMenuService publicMenuService;

    @GetMapping("/public-menu")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> getPublicMenuStatus() {
        boolean enabled = publicMenuService.isPublicMenuEnabled();
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @RequestMapping(value = "/public-menu", method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> togglePublicMenu(@RequestBody(required = false) Map<String, Object> payload, Principal principal) {
        boolean enabled = false;
        if (payload != null && payload.containsKey("enabled")) {
            Object val = payload.get("enabled");
            if (val instanceof Boolean b) {
                enabled = b;
            } else if (val instanceof String s) {
                enabled = "true".equalsIgnoreCase(s);
            } else if (val instanceof Number n) {
                enabled = n.intValue() == 1;
            }
        }
        String username = principal != null ? principal.getName() : "ADMIN";
        appSettingService.setSetting("PUBLIC_MENU_ENABLED", String.valueOf(enabled), username);
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "message", "Public menu has been " + (enabled ? "enabled" : "disabled") + " successfully."
        ));
    }

    @PostMapping("/public-menu/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> togglePublicMenuDirect(Principal principal) {
        boolean currentState = publicMenuService.isPublicMenuEnabled();
        boolean newState = !currentState;
        String username = principal != null ? principal.getName() : "ADMIN";
        appSettingService.setSetting("PUBLIC_MENU_ENABLED", String.valueOf(newState), username);
        return ResponseEntity.ok(Map.of(
                "enabled", newState,
                "message", "Public menu has been " + (newState ? "enabled" : "disabled") + " successfully."
        ));
    }

    @GetMapping("/parcel-charge")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<Map<String, Object>> getParcelChargeSetting() {
        java.math.BigDecimal defaultCharge = appSettingService.getDefaultParcelCharge();
        return ResponseEntity.ok(Map.of("defaultParcelCharge", defaultCharge));
    }

    @RequestMapping(value = "/parcel-charge", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> updateParcelChargeSetting(@RequestBody(required = false) Map<String, Object> payload, Principal principal) {
        java.math.BigDecimal amount = new java.math.BigDecimal("10.00");
        if (payload != null && payload.containsKey("defaultParcelCharge")) {
            try {
                amount = new java.math.BigDecimal(String.valueOf(payload.get("defaultParcelCharge")));
                if (amount.compareTo(java.math.BigDecimal.ZERO) < 0) amount = java.math.BigDecimal.ZERO;
            } catch (Exception ignored) {}
        }
        amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        String username = principal != null ? principal.getName() : "ADMIN";
        appSettingService.setSetting("PARCEL_CHARGE_DEFAULT", amount.toString(), username);
        return ResponseEntity.ok(Map.of(
                "defaultParcelCharge", amount,
                "message", "Default parcel charge updated to ₹" + amount + " successfully."
        ));
    }
}
