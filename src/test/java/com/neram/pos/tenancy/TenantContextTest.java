package com.neram.pos.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    private static final TenantInfo BLUE = new TenantInfo(5, "bluedoor");
    private static final TenantInfo GREEN = new TenantInfo(6, "greenleaf");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void failsClosedWhenNoCafeIsSelected() {
        assertThrows(TenantNotSelectedException.class, TenantContext::require);
        assertThrows(TenantNotSelectedException.class, TenantContext::requireId);
        assertTrue(TenantContext.current().isEmpty());
    }

    @Test
    void runAsRestoresThePreviousCafeEvenOnFailure() {
        TenantContext.set(BLUE);

        assertThrows(IllegalStateException.class, () -> TenantContext.runAs(GREEN, () -> {
            assertEquals(6, TenantContext.requireId());
            throw new IllegalStateException("boom");
        }));

        assertEquals(5, TenantContext.requireId());
    }

    @Test
    void runAsWithNothingSelectedLeavesNothingSelected() {
        TenantContext.runAs(BLUE, () -> assertEquals(5, TenantContext.requireId()));
        assertTrue(TenantContext.current().isEmpty());
    }

    @Test
    void boundTaskRunsForTheCafeThatWasSelectedWhenItWasBound() throws Exception {
        TenantContext.set(BLUE);
        AtomicReference<Long> seen = new AtomicReference<>();
        Runnable bound = TenantContext.bind(() -> seen.set(TenantContext.currentId().orElse(-1L)));
        TenantContext.set(GREEN);

        Thread t = new Thread(bound);
        t.start();
        t.join();

        assertEquals(5L, seen.get());
    }

    @Test
    void contextDoesNotLeakIntoOtherThreads() throws Exception {
        TenantContext.set(BLUE);
        AtomicReference<Boolean> otherThreadHasCafe = new AtomicReference<>();
        Thread t = new Thread(() -> otherThreadHasCafe.set(TenantContext.current().isPresent()));
        t.start();
        t.join();
        assertFalse(otherThreadHasCafe.get());
    }
}
