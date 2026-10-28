package com.neram.pos.service;

import com.neram.pos.entity.RefreshToken;
import com.neram.pos.entity.User;
import com.neram.pos.repository.RefreshTokenRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Issues and rotates refresh tokens.
 *
 * - The raw token is 256 bits from SecureRandom and is returned to the client once; only its SHA-256 is stored.
 * - Every refresh rotates: the presented token is revoked and a new one issued.
 * - Presenting an already-revoked token (outside a short concurrency grace window) is treated as theft and
 *   revokes every token of that user.
 * - Tokens of inactive or deleted users are refused.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    static final int MAX_ACTIVE_TOKENS_PER_USER = 10;
    static final long REUSE_GRACE_SECONDS = 10;

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    public record Rotation(User user, String refreshToken) {}

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${neram.jwt.refresh-expiration:2592000000}") // 30 days
    private long refreshExpirationMillis;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issue(User user) {
        LocalDateTime now = LocalDateTime.now();
        String raw = newRawToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .token(hash(raw))
                .userId(user.getId())
                .expiryDate(now.plusNanos(refreshExpirationMillis * 1_000_000L))
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Bound the number of live sessions per user: revoke the oldest beyond the cap.
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(user.getId());
        for (int i = 0; i < active.size() - MAX_ACTIVE_TOKENS_PER_USER; i++) {
            active.get(i).setRevoked(true);
            active.get(i).setUpdatedAt(now);
            refreshTokenRepository.save(active.get(i));
        }
        return raw;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is required");
        }
        LocalDateTime now = LocalDateTime.now();
        RefreshToken stored = refreshTokenRepository.findByTokenForUpdate(hash(rawToken.trim()))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        if (stored.isRevoked()) {
            if (stored.getUpdatedAt().isBefore(now.minusSeconds(REUSE_GRACE_SECONDS))) {
                int revoked = refreshTokenRepository.revokeAllByUserId(stored.getUserId(), now);
                log.warn("Refresh token reuse detected for userId={}; revoked {} active session(s)", stored.getUserId(), revoked);
            }
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }
        if (stored.getExpiryDate().isBefore(now)) {
            stored.setRevoked(true);
            stored.setUpdatedAt(now);
            refreshTokenRepository.save(stored);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        User user = userRepository.findById(stored.getUserId()).orElse(null);
        if (user == null || Boolean.FALSE.equals(user.getActive())) {
            stored.setRevoked(true);
            stored.setUpdatedAt(now);
            refreshTokenRepository.save(stored);
            throw new InvalidRefreshTokenException("User account is not active");
        }

        stored.setRevoked(true);
        stored.setUpdatedAt(now);
        refreshTokenRepository.save(stored);
        return new Rotation(user, issue(user));
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenForUpdate(hash(rawToken.trim())).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevoked(true);
                t.setUpdatedAt(LocalDateTime.now());
                refreshTokenRepository.save(t);
            }
        });
    }

    /** Ends every session of a user (password change or reset, deactivation, deletion). */
    @Transactional
    public void revokeAllForUser(Long userId) {
        if (userId != null) {
            refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
        }
    }

    /** Keeps the table small: removes expired tokens and revoked tokens older than a week. Run per cafe by RefreshTokenCleanupJob. */
    @Transactional
    public void purgeStale() {
        if (!com.neram.pos.config.JobMode.interactive()) return;
        int removed = refreshTokenRepository.deleteStale(LocalDateTime.now().minusDays(7));
        if (removed > 0) {
            log.info("Purged {} stale refresh token(s)", removed);
        }
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
