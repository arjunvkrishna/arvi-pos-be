package com.neram.pos.service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force guard for SaaS owner logins. Cafe logins are protected by LoginAttemptService, which stores its state
 * in the cafe's database; platform accounts have no cafe database, so a small in-memory counter is used instead
 * (per replica, which is enough to make online guessing impractical).
 */
class PlatformLoginThrottle {

    static final int MAX_FAILURES = 5;
    static final long LOCK_MILLIS = 15 * 60_000L;

    private record State(int failures, long lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    void assertNotLocked(String key) {
        State s = states.get(key);
        long now = System.currentTimeMillis();
        if (s != null && s.lockedUntil > now) {
            throw new LoginAttemptService.AccountLockedException((s.lockedUntil - now + 999) / 1000);
        }
    }

    void recordFailure(String key) {
        long now = System.currentTimeMillis();
        if (states.size() > 10_000) {
            states.entrySet().removeIf(e -> e.getValue().lockedUntil < now);
        }
        states.merge(key, new State(1, 0), (old, ignored) -> {
            int failures = old.lockedUntil > 0 && old.lockedUntil < now ? 1 : old.failures + 1;
            return failures >= MAX_FAILURES ? new State(0, now + LOCK_MILLIS) : new State(failures, 0);
        });
    }

    void recordSuccess(String key) {
        states.remove(key);
    }
}
