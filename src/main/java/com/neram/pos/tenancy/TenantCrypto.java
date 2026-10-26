package com.neram.pos.tenancy;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AES-256-GCM encryption for secrets kept in the control database (per-cafe DB passwords, bridge tokens).
 *
 * <p>Format: {@code v1:<key id>:<base64(iv | ciphertext+tag)>}. The key id lets a rotated key coexist with the
 * previous one: set the new key as TENANT_SECRET_KEY and the old one as TENANT_SECRET_KEY_PREVIOUS, then call
 * {@link #needsRewrap} / re-encrypt rows. The associated data binds each ciphertext to its cafe and purpose, so a
 * value copied from another row fails to decrypt.
 */
@Component
public class TenantCrypto {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final String currentKeyId;
    private final Map<String, SecretKeySpec> keysById = new LinkedHashMap<>();

    public TenantCrypto(TenantProperties properties) {
        SecretKeySpec current = parseKey(properties.getSecretKey(), "TENANT_SECRET_KEY");
        if (current == null) {
            throw new IllegalStateException(
                    "TENANT_SECRET_KEY is required. Generate one with: openssl rand -base64 32");
        }
        this.currentKeyId = keyId(current);
        keysById.put(currentKeyId, current);
        SecretKeySpec previous = parseKey(properties.getPreviousSecretKey(), "TENANT_SECRET_KEY_PREVIOUS");
        if (previous != null) {
            keysById.putIfAbsent(keyId(previous), previous);
        }
    }

    public String encrypt(String plaintext, String purpose) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keysById.get(currentKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return PREFIX + currentKeyId + ":" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    public String decrypt(String envelope, String purpose) {
        if (envelope == null || !envelope.startsWith(PREFIX)) {
            throw new IllegalStateException("Unrecognised secret format");
        }
        String[] parts = envelope.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalStateException("Malformed secret");
        }
        SecretKeySpec key = keysById.get(parts[1]);
        if (key == null) {
            throw new IllegalStateException("Secret was encrypted with an unknown key (" + parts[1]
                    + "). Provide it as TENANT_SECRET_KEY_PREVIOUS.");
        }
        try {
            byte[] all = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt secret (wrong key or tampered value)", e);
        }
    }

    /** True when the value was sealed with an older key and should be re-encrypted with the current one. */
    public boolean needsRewrap(String envelope) {
        return envelope != null && !envelope.startsWith(PREFIX + currentKeyId + ":");
    }

    /** Purpose string used as AES-GCM associated data. */
    public static String purpose(String tenantCode, String field) {
        return "tenant:" + tenantCode + ":" + field;
    }

    private static SecretKeySpec parseKey(String base64, String name) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be base64 (openssl rand -base64 32)", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(name + " must decode to exactly 32 bytes (openssl rand -base64 32)");
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static String keyId(SecretKeySpec key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
