package com.neram.pos.service;

import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramUpdateRouterTest {

    private TenantRegistry registry;
    private TelegramNotificationService notifications;
    private TelegramBotClient bot;
    private TelegramUpdateRouter router;

    @BeforeEach
    void setUp() {
        registry = mock(TenantRegistry.class);
        notifications = mock(TelegramNotificationService.class);
        bot = mock(TelegramBotClient.class);
        router = new TelegramUpdateRouter(registry, notifications, bot);
    }

    private TenantRecord cafe(long id, String code, String status) {
        return new TenantRecord(id, code, code, status, "PRO", 1, null, null, null, "h", 1, "d", "u", "e", "e", null,
                null, null, null);
    }

    @Test
    void routesTheLinkToTheCafeNamedInTheCodeAndPassesOnlyTheRandomPart() {
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        AtomicLong cafeSeen = new AtomicLong();
        when(notifications.processIncomingTelegramUpdate(anyString(), anyLong(), any(), any())).thenAnswer(i -> {
            cafeSeen.set(TenantContext.requireId());
            return true;
        });

        boolean linked = router.route("/start bluedoor_AB12CD", 99L, "mgr", "Mia");

        assertTrue(linked);
        assertEquals(5L, cafeSeen.get());
        verify(notifications).processIncomingTelegramUpdate("/link AB12CD", 99L, "mgr", "Mia");
        assertTrue(TenantContext.current().isEmpty(), "context must not leak after routing");
    }

    @Test
    void aCodeNamingAnotherCafeNeverReachesThisCafesDatabase() {
        when(registry.findByCode("greenleaf")).thenReturn(Optional.of(cafe(6, "greenleaf", "ACTIVE")));

        router.route("/link greenleaf_AB12CD", 1L, "u", "n");

        verify(notifications).processIncomingTelegramUpdate(eq("/link AB12CD"), eq(1L), any(), any());
        verify(registry, never()).findByCode("bluedoor");
    }

    @Test
    void unknownSuspendedOrPrefixlessCodesAreRejectedWithAReply() throws Exception {
        when(registry.findByCode("ghost")).thenReturn(Optional.empty());
        when(registry.findByCode("paused")).thenReturn(Optional.of(cafe(7, "paused", "SUSPENDED")));

        assertFalse(router.route("/link ghost_AB12CD", 1L, "u", "n"));
        assertFalse(router.route("/link paused_AB12CD", 1L, "u", "n"));
        assertFalse(router.route("/link AB12CD", 1L, "u", "n"));
        assertFalse(router.route("/link bluedoor_", 1L, "u", "n"));

        verifyNoInteractions(notifications);
        verify(bot, times(4)).sendMessage(eq("1"), contains("Invalid Linking Code"));
    }

    @Test
    void ordinaryMessagesAreIgnored() {
        assertFalse(router.route("hello", 1L, "u", "n"));
        assertFalse(router.route(null, 1L, "u", "n"));
        assertFalse(router.route("/link", 1L, "u", "n"));
        verifyNoInteractions(notifications, registry);
    }
}
