package com.neram.pos.tenancy;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TenantCryptoTest {

    private static String key(int fill) {
        byte[] b = new byte[32];
        java.util.Arrays.fill(b, (byte) fill);
        return Base64.getEncoder().encodeToString(b);
    }

    private static TenantCrypto crypto(String key, String previous) {
        TenantProperties p = new TenantProperties();
        p.setSecretKey(key);
        p.setPreviousSecretKey(previous);
        return new TenantCrypto(p);
    }

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        TenantCrypto c = crypto(key(1), null);
        String sealed = c.encrypt("s3cret-pass", TenantCrypto.purpose("bluedoor", "db-password"));

        assertFalse(sealed.contains("s3cret-pass"));
        assertEquals("s3cret-pass", c.decrypt(sealed, TenantCrypto.purpose("bluedoor", "db-password")));
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        TenantCrypto c = crypto(key(1), null);
        String a = c.encrypt("x", "p");
        String b = c.encrypt("x", "p");
        assertNotEquals(a, b);
    }

    @Test
    void aValueCopiedToAnotherCafeOrFieldDoesNotDecrypt() {
        TenantCrypto c = crypto(key(1), null);
        String sealed = c.encrypt("pw", TenantCrypto.purpose("bluedoor", "db-password"));

        assertThrows(IllegalStateException.class, () -> c.decrypt(sealed, TenantCrypto.purpose("greenleaf", "db-password")));
        assertThrows(IllegalStateException.class, () -> c.decrypt(sealed, TenantCrypto.purpose("bluedoor", "bridge-token")));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        TenantCrypto c = crypto(key(1), null);
        String sealed = c.encrypt("pw", "p");
        String tampered = sealed.substring(0, sealed.length() - 4) + (sealed.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThrows(IllegalStateException.class, () -> c.decrypt(tampered, "p"));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String sealed = crypto(key(1), null).encrypt("pw", "p");
        assertThrows(IllegalStateException.class, () -> crypto(key(2), null).decrypt(sealed, "p"));
    }

    @Test
    void keyRotationKeepsOldValuesReadableAndFlagsThemForRewrap() {
        String sealedWithOld = crypto(key(1), null).encrypt("pw", "p");

        TenantCrypto rotated = crypto(key(2), key(1));

        assertEquals("pw", rotated.decrypt(sealedWithOld, "p"));
        assertTrue(rotated.needsRewrap(sealedWithOld));
        String fresh = rotated.encrypt("pw", "p");
        assertFalse(rotated.needsRewrap(fresh));
    }

    @Test
    void missingOrMalformedKeyFailsFast() {
        assertThrows(IllegalStateException.class, () -> crypto(null, null));
        assertThrows(IllegalStateException.class, () -> crypto("", null));
        assertThrows(IllegalStateException.class, () -> crypto("not base64 !!", null));
        assertThrows(IllegalStateException.class, () -> crypto(Base64.getEncoder().encodeToString(new byte[16]), null));
    }
}
