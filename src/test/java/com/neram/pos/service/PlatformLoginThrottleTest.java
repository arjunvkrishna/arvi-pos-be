package com.neram.pos.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformLoginThrottleTest {

    @Test
    void lockedAfterRepeatedFailuresAndSuccessResets() {
        PlatformLoginThrottle t = new PlatformLoginThrottle();
        for (int i = 0; i < PlatformLoginThrottle.MAX_FAILURES - 1; i++) {
            t.recordFailure("root|1.2.3.4");
            assertDoesNotThrow(() -> t.assertNotLocked("root|1.2.3.4"));
        }
        t.recordFailure("root|1.2.3.4");
        LoginAttemptService.AccountLockedException e = assertThrows(LoginAttemptService.AccountLockedException.class,
                () -> t.assertNotLocked("root|1.2.3.4"));
        assertTrue(e.getRetryAfterSeconds() > 0);

        assertDoesNotThrow(() -> t.assertNotLocked("root|9.9.9.9"), "another client address is not locked");

        t.recordSuccess("root|1.2.3.4");
        assertDoesNotThrow(() -> t.assertNotLocked("root|1.2.3.4"));
    }
}
