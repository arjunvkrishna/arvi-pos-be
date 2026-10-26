package com.neram.pos.service;

import com.neram.pos.entity.LoginAttempt;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.entity.User;
import com.neram.pos.repository.LoginAttemptRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import com.neram.pos.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Brute-force protection for the login endpoint.
 *
 * - Per account: {@code max-failed-attempts} wrong passwords in a row lock that username. The lock time grows
 *   with repeated lockouts (base, 2x, 4x ... up to a cap). A correct login resets everything.
 * - Per client IP (second line of defence, more generous): many failures from one address block that address.
 * - While locked even the CORRECT password is refused, and an unknown username behaves exactly like a real one,
 *   so the endpoint cannot be used to discover which accounts exist.
 * - When a real account locks, every manager and admin sees a bell notification and the audit log records it.
 */
@Service
@Slf4j
public class LoginAttemptService {

    /** Thrown when the account or the client address is currently locked. */
    public static class AccountLockedException extends RuntimeException {
        private final long retryAfterSeconds;
        public AccountLockedException(long retryAfterSeconds) {
            super("Too many failed login attempts. Try again in " + minutesText(retryAfterSeconds) + ".");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    private final LoginAttemptRepository repository;
    private final UserRepository userRepository;
    private final SystemNotificationRepository notifications;
    private final NotificationStreamService stream;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;

    private final int maxFailedAttempts;
    private final int ipMaxFailedAttempts;
    private final long baseLockSeconds;
    private final long maxLockSeconds;
    private final long failureWindowSeconds;

    private Clock clock = Clock.systemDefaultZone();

    @Autowired
    public LoginAttemptService(LoginAttemptRepository repository,
                               UserRepository userRepository,
                               SystemNotificationRepository notifications,
                               NotificationStreamService stream,
                               AuditLogService auditLogService,
                               TransactionTemplate tx,
                               @Value("${neram.security.login.max-failed-attempts:5}") int maxFailedAttempts,
                               @Value("${neram.security.login.ip-max-failed-attempts:30}") int ipMaxFailedAttempts,
                               @Value("${neram.security.login.base-lock-seconds:900}") long baseLockSeconds,
                               @Value("${neram.security.login.max-lock-seconds:14400}") long maxLockSeconds,
                               @Value("${neram.security.login.failure-window-seconds:1800}") long failureWindowSeconds) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.stream = stream;
        this.auditLogService = auditLogService;
        this.tx = tx;
        this.maxFailedAttempts = maxFailedAttempts;
        this.ipMaxFailedAttempts = ipMaxFailedAttempts;
        this.baseLockSeconds = baseLockSeconds;
        this.maxLockSeconds = maxLockSeconds;
        this.failureWindowSeconds = failureWindowSeconds;
    }

    void useClock(Clock clock) { this.clock = clock; }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    static String userKey(String username) {
        String k = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return k.length() > 100 ? k.substring(0, 100) : k;
    }

    private static String ipKey(String ip) {
        String k = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        return k.length() > 100 ? k.substring(0, 100) : k;
    }

    // ------------------------------------------------------------------ checks

    /** Throws {@link AccountLockedException} while the username or the client address is locked. */
    public void assertNotLocked(String username, String ip) {
        long remaining = Math.max(remainingSeconds(LoginAttempt.SCOPE_USER, userKey(username)),
                remainingSeconds(LoginAttempt.SCOPE_IP, ipKey(ip)));
        if (remaining > 0) throw new AccountLockedException(remaining);
    }

    private long remainingSeconds(String scope, String key) {
        return repository.findByScopeAndAttemptKey(scope, key)
                .filter(a -> a.getLockedUntil() != null && a.getLockedUntil().isAfter(now()))
                .map(a -> Math.max(1, (Duration.between(now(), a.getLockedUntil()).toMillis() + 999) / 1000))
                .orElse(0L);
    }

    // ---------------------------------------------------------------- outcomes

    /**
     * Records a failed login. Returns the lock time in seconds if THIS failure locked the account
     * (or the address), otherwise 0.
     */
    public long recordFailure(String username, String ip) {
        long locked;
        try {
            locked = tx.execute(s -> doRecordFailure(username, ip));
        } catch (DataIntegrityViolationException raceOnFirstInsert) {
            locked = tx.execute(s -> doRecordFailure(username, ip));     // two parallel first failures: retry once
        }
        return locked;
    }

    private long doRecordFailure(String username, String ip) {
        String uKey = userKey(username);
        long userLock = bump(LoginAttempt.SCOPE_USER, uKey, maxFailedAttempts);
        long ipLock = bump(LoginAttempt.SCOPE_IP, ipKey(ip), ipMaxFailedAttempts);

        Optional<User> real = uKey.isEmpty() ? Optional.empty() : userRepository.findByUsername(username.trim());
        if (real.isPresent()) {
            User u = real.get();
            auditLogService.log("LOGIN_FAILED", "USER", u.getId(), null,
                    "Failed login for " + u.getUsername() + " from " + ipKey(ip), u.getId(), u.getUsername());
            if (userLock > 0) {
                String title = "Account locked: " + u.getUsername();
                String msg = maxFailedAttempts + " failed login attempts in a row for \"" + u.getUsername() + "\" (last from "
                        + ipKey(ip) + "). The account is locked for " + minutesText(userLock)
                        + ". If this was not the user, change their password; an admin or manager can unlock it from User Accounts.";
                notifications.save(SystemNotification.builder()
                        .type(SystemNotification.NotificationType.SECURITY_ALERT)
                        .title(title).message(msg).referenceId(u.getId())
                        .createdBy("System").status(SystemNotification.Status.UNREAD).createdAt(now()).build());
                auditLogService.log("ACCOUNT_LOCKED", "USER", u.getId(), null, title + " for " + minutesText(userLock),
                        u.getId(), u.getUsername());
                stream.publishChangedAfterCommit();
                log.warn("Account '{}' locked for {}s after repeated failed logins from {}", u.getUsername(), userLock, ipKey(ip));
            }
        }
        if (ipLock > 0) {
            log.warn("Client address {} blocked for {}s after repeated failed logins", ipKey(ip), ipLock);
        }
        return Math.max(userLock, ipLock);
    }

    /** Increments the counter for one key and returns the lock time in seconds if it just locked, else 0. */
    private long bump(String scope, String key, int max) {
        LocalDateTime now = now();
        LoginAttempt a = repository.findByScopeAndAttemptKey(scope, key).orElseGet(() ->
                LoginAttempt.builder().scope(scope).attemptKey(key).failedCount(0).lockLevel(0).lastFailedAt(now).build());

        if (a.getLockedUntil() != null && a.getLockedUntil().isAfter(now)) {
            return 0;      // already locked; do not extend the lock with further guesses
        }
        // Forget old failures; forget the escalation level once the key has been quiet for a full window
        if (a.getLastFailedAt() != null && a.getLastFailedAt().isBefore(now.minusSeconds(failureWindowSeconds))) {
            a.setFailedCount(0);
            if (a.getLockedUntil() == null || a.getLockedUntil().isBefore(now.minusSeconds(failureWindowSeconds))) {
                a.setLockLevel(0);
            }
        }
        a.setFailedCount(a.getFailedCount() + 1);
        a.setLastFailedAt(now);

        long lockSeconds = 0;
        if (a.getFailedCount() >= max) {
            a.setLockLevel(a.getLockLevel() + 1);
            long factor = 1L << Math.min(a.getLockLevel() - 1, 20);
            lockSeconds = Math.min(baseLockSeconds * factor, maxLockSeconds);
            a.setLockedUntil(now.plusSeconds(lockSeconds));
            a.setFailedCount(0);
        }
        repository.save(a);
        return lockSeconds;
    }

    /** A correct password clears the account's counters and escalation level. */
    public void recordSuccess(String username, String ip) {
        repository.deleteByScopeAndAttemptKey(LoginAttempt.SCOPE_USER, userKey(username));
        repository.deleteByScopeAndAttemptKey(LoginAttempt.SCOPE_IP, ipKey(ip));
    }

    // ------------------------------------------------------------------ admin

    /** Manually lifts a lock (User Accounts → Unlock). */
    public void unlock(String username) {
        repository.deleteByScopeAndAttemptKey(LoginAttempt.SCOPE_USER, userKey(username));
    }

    /** Lower-cased username → lock expiry, for the usernames that are locked right now. */
    public Map<String, LocalDateTime> lockedUntil(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) return Map.of();
        Set<String> keys = new HashSet<>();
        usernames.forEach(u -> keys.add(userKey(u)));
        Map<String, LocalDateTime> out = new HashMap<>();
        for (LoginAttempt a : repository.findByScopeAndAttemptKeyIn(LoginAttempt.SCOPE_USER, keys)) {
            if (a.getLockedUntil() != null && a.getLockedUntil().isAfter(now())) {
                out.put(a.getAttemptKey(), a.getLockedUntil());
            }
        }
        return out;
    }

    public static String keyFor(String username) { return userKey(username); }

    public void cleanup() {
        int removed = repository.deleteStale(now(), now().minusDays(7));
        if (removed > 0) log.info("Removed {} stale login attempt record(s)", removed);
    }

    static String minutesText(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
