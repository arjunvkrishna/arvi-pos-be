package com.neram.pos.tenancy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Checks the print bridge's token against the token of the cafe named in the request. Each cafe has its own token
 * (stored encrypted in the registry), so one cafe's bridge can never poll or ack another cafe's print jobs.
 */
@Component
@RequiredArgsConstructor
public class BridgeTokenVerifier {

    private static final String FIELD = "bridge-token";

    private final TenantRegistry registry;
    private final TenantCrypto crypto;

    public boolean verify(String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        TenantInfo tenant = TenantContext.current().orElse(null);
        if (tenant == null) {
            return false;
        }
        TenantRecord rec = registry.findById(tenant.id()).orElse(null);
        if (rec == null || rec.bridgeTokenEnc() == null) {
            return false;
        }
        String clean = presented.startsWith("Bearer ") ? presented.substring(7).trim() : presented.trim();
        String expected = crypto.decrypt(rec.bridgeTokenEnc(), TenantCrypto.purpose(rec.code(), FIELD));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), clean.getBytes(StandardCharsets.UTF_8));
    }
}
