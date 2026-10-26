package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Failed-login counter for one username or one client IP (see LoginAttemptService). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_IP = "IP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String scope;

    @Column(name = "attempt_key", nullable = false, length = 100)
    private String attemptKey;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "lock_level", nullable = false)
    private int lockLevel;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;
}
