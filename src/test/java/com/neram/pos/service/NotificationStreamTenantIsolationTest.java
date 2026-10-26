package com.neram.pos.service;

import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class NotificationStreamTenantIsolationTest {

    private final NotificationStreamService service = new NotificationStreamService();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        service.shutdown();
    }

    private SseEmitter subscribeAs(long tenantId, String user) {
        return TenantContext.callAs(new TenantInfo(tenantId, "t" + tenantId), () -> service.subscribe(user));
    }

    @Test
    void sameUsernameInTwoCafesIsTwoDifferentSubscribers() {
        for (int i = 0; i < NotificationStreamService.MAX_CONNECTIONS_PER_USER; i++) {
            assertNotNull(subscribeAs(5, "manager"));
        }
        // Cafe 6's "manager" must not push cafe 5's "manager" over their per-user cap and evict streams.
        assertNotNull(subscribeAs(6, "manager"));

        assertEquals(NotificationStreamService.MAX_CONNECTIONS_PER_USER + 1, service.connectionCount());
    }

    @SuppressWarnings("unchecked")
    private static int sentSoFar(SseEmitter emitter) {
        // Unit tests have no HTTP response, so the emitter buffers what was sent to it.
        java.util.Collection<Object> early = (java.util.Collection<Object>) org.springframework.test.util.ReflectionTestUtils
                .getField(emitter, org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class,
                        "earlySendAttempts");
        return early.size();
    }

    @Test
    void anOrderChangeInOneCafeIsNeverSignalledToAnotherCafesTerminals() throws Exception {
        SseEmitter blue = subscribeAs(5, "cashier");
        SseEmitter green = subscribeAs(6, "cashier");
        int blueBefore = sentSoFar(blue);
        int greenBefore = sentSoFar(green);

        TenantContext.set(new TenantInfo(5, "bluedoor"));
        service.publishChangedAfterCommit();
        TenantContext.clear();
        Thread.sleep(300); // the signal is delivered on the sender thread

        assertTrue(sentSoFar(blue) > blueBefore, "blue terminal should be signalled");
        assertEquals(greenBefore, sentSoFar(green), "green terminal must not receive blue's signal");
    }
}
