package com.neram.pos.controller;

import com.neram.pos.access.AccessPolicy;
import com.neram.pos.access.CafeUserDetails;
import com.neram.pos.access.OutletContext;
import com.neram.pos.entity.BrandingAsset.Kind;
import com.neram.pos.repository.TenantRepository;
import com.neram.pos.service.BrandingService;
import com.neram.pos.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

/**
 * Logo and payment QR uploads.
 * <ul>
 *   <li>{@code outletId} omitted: the cafe-wide image (owner and main manager, PERM_MANAGE_SETTINGS).</li>
 *   <li>{@code outletId} given: that branch's own image (PERM_MANAGE_OUTLET_SETTINGS; branch managers only for their own branch).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/branding")
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService brandingService;
    private final TenantRepository tenantRepository;
    private final com.neram.pos.service.PublicMenuCache publicMenuCache;

    /** Which images exist (cafe-wide and per branch). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(Map.of("assets", brandingService.overview()));
    }

    /** The image in effect for the caller's branch (or the named branch), for preview. */
    @GetMapping("/{kind}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> get(@PathVariable String kind, @RequestParam(required = false) Long outletId) {
        Kind k = parseKind(kind);
        Long outlet = outletId != null ? outletId : OutletContext.current().orElse(null);
        return brandingService.effective(k, outlet)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.contentType()))
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .body(img.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> upload(@PathVariable String kind,
                                    @RequestParam(required = false) Long outletId,
                                    @RequestParam("file") MultipartFile file,
                                    Authentication auth) throws IOException {
        Kind k = parseKind(kind);
        assertMayEdit(auth, outletId);
        BrandingService.Image saved = brandingService.save(k, outletId, file.getBytes(), file.getContentType(), auth.getName());
        if (k == Kind.LOGO && outletId == null) {
            publishCafeLogoUrl(saved);
            publicMenuCache.evictCurrentCafe();
        }
        return ResponseEntity.ok(Map.of("kind", k.name(), "outletId", outletId == null ? "" : outletId,
                "updatedAt", saved.updatedAt().toString()));
    }

    @DeleteMapping("/{kind}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> remove(@PathVariable String kind, @RequestParam(required = false) Long outletId, Authentication auth) {
        Kind k = parseKind(kind);
        assertMayEdit(auth, outletId);
        boolean removed = brandingService.delete(k, outletId);
        if (k == Kind.LOGO && outletId == null && removed) {
            tenantRepository.findById(TenantContext.requireId()).ifPresent(t -> {
                t.setLogoUrl(null);
                tenantRepository.save(t);
            });
            publicMenuCache.evictCurrentCafe();
        }
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private void publishCafeLogoUrl(BrandingService.Image saved) {
        tenantRepository.findById(TenantContext.requireId()).ifPresent(t -> {
            // Served by the public endpoint so the login screen, public menu and bills can all show it.
            t.setLogoUrl("/api/public/" + t.getCode() + "/logo?v=" + saved.updatedAt().toEpochSecond(ZoneOffset.UTC));
            tenantRepository.save(t);
        });
    }

    static void assertMayEdit(Authentication auth, Long outletId) {
        boolean cafeLevel = outletId == null;
        String needed = cafeLevel ? AccessPolicy.PERM_MANAGE_SETTINGS : AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS;
        boolean has = auth.getAuthorities().stream().anyMatch(a -> needed.equals(a.getAuthority()));
        if (!has) {
            throw new AccessDeniedException(cafeLevel
                    ? "Only the owner or main manager can change cafe-wide branding"
                    : "You are not allowed to change branch settings");
        }
        if (!cafeLevel && auth.getPrincipal() instanceof CafeUserDetails user
                && !AccessPolicy.hasCafeWideAccess(user.getRole())
                && !outletId.equals(user.getOutletId())) {
            throw new AccessDeniedException("You can only change your own branch");
        }
    }

    private static Kind parseKind(String kind) {
        return switch (kind.toLowerCase()) {
            case "logo" -> Kind.LOGO;
            case "payment-qr", "payment_qr" -> Kind.PAYMENT_QR;
            default -> throw new NoSuchElementException("Unknown image type: " + kind);
        };
    }
}
