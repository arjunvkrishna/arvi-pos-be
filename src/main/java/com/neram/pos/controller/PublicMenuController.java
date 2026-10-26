package com.neram.pos.controller;

import com.neram.pos.dto.PublicMenuResponseDTO;
import com.neram.pos.service.PublicMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMenuController {

    private final PublicMenuService publicMenuService;
    private final com.neram.pos.service.BrandingService brandingService;
    private final com.neram.pos.access.OutletScopeResolver outletScopeResolver;

    /** The cafe is named in the URL ({@code /api/public/{cafe}/menu}); JwtAuthenticationFilter selects its database. */
    @GetMapping("/{tenantCode}/menu")
    public ResponseEntity<?> getPublicMenu(@PathVariable String tenantCode, @RequestParam(required = false) String branch) {
        return com.neram.pos.access.OutletContext.callAs(branchId(branch), () -> publicMenuFor());
    }

    /** The public link may name a branch (?branch=CODE) so prices and sold-out items are that branch's own. */
    private Long branchId(String branch) {
        try {
            return outletScopeResolver.resolveByCode(branch);
        } catch (com.neram.pos.access.OutletAccessDeniedException e) {
            throw new java.util.NoSuchElementException("Unknown branch");
        }
    }

    private ResponseEntity<?> publicMenuFor() {
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

    @GetMapping("/{tenantCode}/menu/status")
    public ResponseEntity<Map<String, Object>> getPublicMenuStatus(@PathVariable String tenantCode) {
        boolean enabled = publicMenuService.isPublicMenuEnabled();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header("Pragma", "no-cache")
                .body(Map.of("enabled", enabled));
    }

    /** The cafe's logo, for the public menu page and the login screen. */
    @GetMapping("/{tenantCode}/logo")
    public ResponseEntity<byte[]> getLogo(@PathVariable String tenantCode) {
        return brandingService.effective(com.neram.pos.entity.BrandingAsset.Kind.LOGO, null)
                .map(img -> ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType(img.contentType()))
                        .cacheControl(CacheControl.maxAge(1, java.util.concurrent.TimeUnit.HOURS).cachePublic())
                        .body(img.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
