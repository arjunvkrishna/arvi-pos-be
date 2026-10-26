package com.neram.pos.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BridgeTokenVerifierTest {

    private TenantRegistry registry;
    private TenantCrypto crypto;
    private BridgeTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setSecretKey(Base64.getEncoder().encodeToString(new byte[32]));
        crypto = new TenantCrypto(props);
        registry = mock(TenantRegistry.class);
        verifier = new BridgeTokenVerifier(registry, crypto);

        register(5, "bluedoor", "blue-token-123");
        register(6, "greenleaf", "green-token-456");
    }

    private void register(long id, String code, String token) {
        String enc = crypto.encrypt(token, TenantCrypto.purpose(code, "bridge-token"));
        when(registry.findById(id)).thenReturn(Optional.of(new TenantRecord(id, code, code, "ACTIVE", "PRO", 1, null,
                null, null, "h", 5432, "db", "u", "x", enc, null, null, null, null)));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void acceptsOnlyTheTokenOfTheCafeNamedInTheRequest() {
        TenantContext.set(new TenantInfo(5, "bluedoor"));
        assertTrue(verifier.verify("blue-token-123"));
        assertTrue(verifier.verify("Bearer blue-token-123"));
        assertFalse(verifier.verify("green-token-456"));
    }

    @Test
    void anotherCafesBridgeCannotUseItsOwnTokenAgainstThisCafe() {
        TenantContext.set(new TenantInfo(6, "greenleaf"));
        assertFalse(verifier.verify("blue-token-123"));
        assertTrue(verifier.verify("green-token-456"));
    }

    @Test
    void refusesWhenNoCafeIsNamedOrTokenIsMissing() {
        assertFalse(verifier.verify("blue-token-123"));
        TenantContext.set(new TenantInfo(5, "bluedoor"));
        assertFalse(verifier.verify(null));
        assertFalse(verifier.verify("  "));
    }
}
