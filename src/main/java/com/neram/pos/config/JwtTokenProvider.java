package com.neram.pos.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    static final String CLAIM_TENANT_ID = "tid";
    static final String CLAIM_TENANT_CODE = "tc";
    static final String CLAIM_SCOPE = "scope";
    static final String SCOPE_PLATFORM = "platform";

    /** What a verified token says about its holder. A platform token has no cafe. */
    public record TokenIdentity(String username, Long tenantId, String tenantCode, boolean platform) {
    }

    @Value("${neram.jwt.secret}")
    private String jwtSecret;

    @Value("${neram.jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** Token for a cafe user; the cafe is baked in so a token can never be replayed against another cafe. */
    public String generateToken(Authentication authentication, long tenantId, String tenantCode) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateTokenFromUsername(userDetails.getUsername(), tenantId, tenantCode);
    }

    public String generateTokenFromUsername(String username, long tenantId, String tenantCode) {
        return builder(username)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_TENANT_CODE, tenantCode)
                .compact();
    }

    /** Token for a SaaS owner account (control database); valid for platform endpoints only. */
    public String generatePlatformToken(String username) {
        return builder(username).claim(CLAIM_SCOPE, SCOPE_PLATFORM).compact();
    }

    private io.jsonwebtoken.JwtBuilder builder(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpiration))
                .signWith(getSigningKey());
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    public TokenIdentity getIdentity(String token) {
        Claims claims = parse(token);
        boolean platform = SCOPE_PLATFORM.equals(claims.get(CLAIM_SCOPE, String.class));
        Number tid = claims.get(CLAIM_TENANT_ID, Number.class);
        return new TokenIdentity(claims.getSubject(), tid == null ? null : tid.longValue(),
                claims.get(CLAIM_TENANT_CODE, String.class), platform);
    }

    public String getUsernameFromToken(String token) {
        return parse(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}
