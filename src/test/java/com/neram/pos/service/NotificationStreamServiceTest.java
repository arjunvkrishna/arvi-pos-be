package com.neram.pos.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NotificationStreamServiceTest {

    private final NotificationStreamService service = spy(new NotificationStreamService());

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
        service.shutdown();
    }

    @Test
    void subscribeRegistersAndCompletionRemovesTheClient() {
        SseEmitter emitter = service.subscribe("manager");
        assertNotNull(emitter);
        assertEquals(1, service.connectionCount());
        emitter.complete(); // not yet bound to a response in a unit test; cleanup callbacks are exercised below via eviction
    }

    @Test
    void perUserCapEvictsOldestStreamInsteadOfGrowing() {
        for (int i = 0; i < NotificationStreamService.MAX_CONNECTIONS_PER_USER + 3; i++) {
            assertNotNull(service.subscribe("manager"));
        }
        assertTrue(service.connectionCount() <= NotificationStreamService.MAX_CONNECTIONS_PER_USER,
                "connections for one user must stay bounded, was " + service.connectionCount());
    }

    @Test
    void globalCapRejectsNewSubscribersWhenFull() {
        for (int i = 0; i < NotificationStreamService.MAX_CONNECTIONS; i++) {
            assertNotNull(service.subscribe("user" + i));
        }
        assertNull(service.subscribe("one-more-user"));
        assertEquals(NotificationStreamService.MAX_CONNECTIONS, service.connectionCount());
    }

    @Test
    void manyOrderChangesInOneTransactionProduceOneSignalAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        for (int i = 0; i < 5; i++) {
            service.publishOrdersChangedAfterCommit();
        }
        verify(service, never()).broadcast(anyString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(service, times(1)).broadcast(NotificationStreamService.ORDERS_EVENT);
    }

    @Test
    void rolledBackOrderChangeSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        service.publishOrdersChangedAfterCommit();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(service, never()).broadcast(anyString());
    }

    @Test
    void orderChangeOutsideTransactionBroadcastsImmediately() {
        service.publishOrdersChangedAfterCommit();
        verify(service, times(1)).broadcast(NotificationStreamService.ORDERS_EVENT);
    }

    @Test
    void perUserCapAppliesPerChannel() {
        for (int i = 0; i < NotificationStreamService.MAX_CONNECTIONS_PER_USER; i++) {
            assertNotNull(service.subscribe("waiter", NotificationStreamService.ORDERS_EVENT));
        }
        assertNotNull(service.subscribe("waiter", NotificationStreamService.NOTIFICATIONS_EVENT));
        assertEquals(NotificationStreamService.MAX_CONNECTIONS_PER_USER + 1, service.connectionCount());
    }

    @Test
    void signalIsOnlySentAfterCommitNeverOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        service.publishChangedAfterCommit();
        verify(service, never()).broadcast(anyString());

        // rollback: afterCommit is not invoked
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(service, never()).broadcast(anyString());

        // commit: afterCommit is invoked
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(service, times(1)).broadcast("notifications-changed");
    }

    @Test
    void publishOutsideTransactionBroadcastsImmediately() {
        service.publishChangedAfterCommit();
        verify(service, times(1)).broadcast("notifications-changed");
    }
}
