package com.neram.pos.service;

import com.neram.pos.entity.LoginAttempt;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.entity.User;
import com.neram.pos.repository.LoginAttemptRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    /** Movable clock so lock expiry can be tested without sleeping. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-21T10:00:00Z");
        void advanceSeconds(long s) { now = now.plusSeconds(s); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final Map<String, LoginAttempt> store = new HashMap<>();
    private LoginAttemptRepository repo;
    private UserRepository users;
    private SystemNotificationRepository notifications;
    private NotificationStreamService stream;
    private AuditLogService audit;
    private TestClock clock;
    private LoginAttemptService service;
    private final List<SystemNotification> raised = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repo = mock(LoginAttemptRepository.class);
        users = mock(UserRepository.class);
        notifications = mock(SystemNotificationRepository.class);
        stream = mock(NotificationStreamService.class);
        audit = mock(AuditLogService.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(null));

        when(repo.findByScopeAndAttemptKey(anyString(), anyString())).thenAnswer(i ->
                Optional.ofNullable(store.get(i.getArgument(0) + "|" + i.getArgument(1))));
        when(repo.save(any(LoginAttempt.class))).thenAnswer(i -> {
            LoginAttempt a = i.getArgument(0);
            store.put(a.getScope() + "|" + a.getAttemptKey(), a);
            return a;
        });
        when(repo.deleteByScopeAndAttemptKey(anyString(), anyString())).thenAnswer(i ->
                store.remove(i.getArgument(0) + "|" + i.getArgument(1)) != null ? 1 : 0);
        when(repo.findByScopeAndAttemptKeyIn(anyString(), any())).thenAnswer(i -> {
            Collection<String> keys = i.getArgument(1);
            return store.values().stream().filter(a -> a.getScope().equals(i.getArgument(0)) && keys.contains(a.getAttemptKey())).toList();
        });
        when(notifications.save(any(SystemNotification.class))).thenAnswer(i -> { raised.add(i.getArgument(0)); return i.getArgument(0); });

        User admin = new User();
        admin.setId(1L); admin.setUsername("admin"); admin.setRole(User.Role.ADMIN);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());

        clock = new TestClock();
        service = new LoginAttemptService(repo, users, notifications, stream, audit, tx, 5, 30, 900, 14400, 1800);
        service.useClock(clock);
    }

    private long fail(String username, String ip) { return service.recordFailure(username, ip); }

    @Test
    void fifthWrongPasswordLocksTheAccountForFifteenMinutes() {
        for (int i = 1; i <= 4; i++) assertEquals(0, fail("admin", "1.1.1." + i), "attempt " + i + " must not lock");
        long locked = fail("admin", "1.1.1.9");
        assertEquals(900, locked);
        LoginAttemptService.AccountLockedException ex = assertThrows(LoginAttemptService.AccountLockedException.class,
                () -> service.assertNotLocked("admin", "8.8.8.8"));
        assertEquals(900, ex.getRetryAfterSeconds());
        assertTrue(ex.getMessage().contains("15 minutes"));
    }

    @Test
    void lockAppliesRegardlessOfUsernameCasing() {
        for (int i = 0; i < 5; i++) fail("ADMIN", "1.1.1.1");
        assertThrows(LoginAttemptService.AccountLockedException.class, () -> service.assertNotLocked("admin ", "9.9.9.9"));
    }

    @Test
    void lockExpiresAfterItsTime() {
        for (int i = 0; i < 5; i++) fail("admin", "1.1.1.1");
        clock.advanceSeconds(901);
        assertDoesNotThrow(() -> service.assertNotLocked("admin", "9.9.9.9"));
    }

    @Test
    void correctLoginResetsTheCounter() {
        for (int i = 0; i < 4; i++) fail("admin", "1.1.1.1");
        service.recordSuccess("admin", "1.1.1.1");
        for (int i = 0; i < 4; i++) assertEquals(0, fail("admin", "1.1.1.1"), "counter restarted after the successful login");
        assertDoesNotThrow(() -> service.assertNotLocked("admin", "1.1.1.1"));
    }

    @Test
    void repeatedLockoutsGetLonger() {
        for (int i = 0; i < 5; i++) fail("admin", "1.1.1.1");              // 15 min
        clock.advanceSeconds(901);
        long second = 0;
        for (int i = 0; i < 5; i++) second = fail("admin", "1.1.1.1");     // second lock in a row
        assertEquals(1800, second, "second lockout is twice as long");
    }

    @Test
    void lockTimeIsCapped() {
        long last = 0;
        for (int round = 0; round < 8; round++) {
            for (int i = 0; i < 5; i++) last = fail("admin", "1.1.1.1");
            clock.advanceSeconds(20_000);   // past the lock, but inside... the cap is what matters
        }
        assertTrue(last <= 14400);
    }

    @Test
    void oldFailuresAreForgotten() {
        for (int i = 0; i < 4; i++) fail("admin", "1.1.1.1");
        clock.advanceSeconds(1801);                                         // quiet for longer than the window
        for (int i = 0; i < 4; i++) assertEquals(0, fail("admin", "1.1.1.1"));
    }

    @Test
    void unknownUsernameIsThrottledExactlyLikeARealOne() {
        for (int i = 0; i < 4; i++) assertEquals(0, fail("nobody", "2.2.2.2"));
        assertEquals(900, fail("nobody", "2.2.2.2"));
        assertThrows(LoginAttemptService.AccountLockedException.class, () -> service.assertNotLocked("nobody", "3.3.3.3"));
        assertTrue(raised.isEmpty(), "no bell alert and no audit entry for usernames that do not exist");
        verify(audit, never()).log(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void lockingARealAccountAlertsManagersAndAdminsOnceAndIsAudited() {
        for (int i = 0; i < 5; i++) fail("admin", "1.1.1.1");
        assertEquals(1, raised.size());
        SystemNotification n = raised.get(0);
        assertEquals(SystemNotification.NotificationType.SECURITY_ALERT, n.getType());
        assertEquals("Account locked: admin", n.getTitle());
        assertTrue(n.getMessage().contains("15 minutes"));
        verify(stream, times(1)).publishChangedAfterCommit();
        verify(audit).log(eq("ACCOUNT_LOCKED"), eq("USER"), eq(1L), any(), anyString(), eq(1L), eq("admin"));
        verify(audit, times(5)).log(eq("LOGIN_FAILED"), eq("USER"), eq(1L), any(), anyString(), eq(1L), eq("admin"));

        fail("admin", "1.1.1.1");                                           // guesses while locked change nothing
        assertEquals(1, raised.size());
    }

    @Test
    void guessingWhileLockedDoesNotExtendTheLock() {
        for (int i = 0; i < 5; i++) fail("admin", "1.1.1.1");
        clock.advanceSeconds(600);
        for (int i = 0; i < 10; i++) fail("admin", "1.1.1.1");
        clock.advanceSeconds(301);                                          // 900s after the original lock
        assertDoesNotThrow(() -> service.assertNotLocked("admin", "9.9.9.9"));
    }

    @Test
    void oneAddressTryingManyDifferentAccountsGetsBlocked() {
        long locked = 0;
        for (int i = 0; i < 30; i++) locked = fail("user" + i, "6.6.6.6");  // each username only once
        assertEquals(900, locked);
        assertThrows(LoginAttemptService.AccountLockedException.class, () -> service.assertNotLocked("someone-else", "6.6.6.6"));
        assertDoesNotThrow(() -> service.assertNotLocked("someone-else", "7.7.7.7"), "other addresses are unaffected");
    }

    @Test
    void unlockRemovesTheLockImmediately() {
        for (int i = 0; i < 5; i++) fail("admin", "1.1.1.1");
        assertTrue(service.lockedUntil(List.of("admin", "manager")).containsKey("admin"));
        service.unlock("Admin");
        assertDoesNotThrow(() -> service.assertNotLocked("admin", "9.9.9.9"));
        assertTrue(service.lockedUntil(List.of("admin")).isEmpty());
    }

    @Test
    void lockedUntilReportsOnlyActiveLocks() {
        for (int i = 0; i < 5; i++) fail("admin", "1.1.1.1");
        LocalDateTime until = service.lockedUntil(List.of("admin")).get("admin");
        assertNotNull(until);
        clock.advanceSeconds(901);
        assertTrue(service.lockedUntil(List.of("admin")).isEmpty());
    }
}
