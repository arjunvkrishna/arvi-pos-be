package com.neram.pos.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pushes tiny, content-free change signals to connected browsers over Server-Sent Events, replacing polling.
 *
 * Two channels, each named after the event it carries:
 *  - "notifications-changed": ADMIN/MANAGER notification list or unread counts changed.
 *  - "orders-changed": a bill, bill item or dining table changed (POS live view).
 *
 * The event carries no data: the database stays the source of truth and clients re-read the existing REST
 * endpoints on each signal (and on every (re)connect, which also recovers anything missed while disconnected).
 * Being content-free, a stream cannot leak private data even if mis-routed; who may subscribe is enforced by the
 * controller's @PreAuthorize, identical to the REST endpoint being replaced.
 *
 * State is per JVM. The deployment runs a single backend container; with several instances each instance only
 * knows its own subscribers, so a cross-instance bus (e.g. PostgreSQL LISTEN/NOTIFY) would be needed.
 */
@Slf4j
@Service
public class NotificationStreamService {

    public static final String NOTIFICATIONS_EVENT = "notifications-changed";
    public static final String ORDERS_EVENT = "orders-changed";

    static final int MAX_CONNECTIONS = 200;
    static final int MAX_CONNECTIONS_PER_USER = 5; // per user and channel
    static final long MAX_STREAM_MILLIS = 10 * 60 * 1000L; // client reconnects (and re-authenticates) after this

    private static final Object ORDERS_SIGNAL_KEY = new Object();

    private record Client(long id, long tenantId, String username, String channel, SseEmitter emitter) {}

    /** Streams are per cafe: a signal in one cafe must never reach another cafe's terminals. */
    private static long currentTenantId() {
        return com.neram.pos.tenancy.TenantContext.currentId().orElse(0L);
    }

    private final ConcurrentHashMap<Long, Client> clients = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Set<String> pendingBroadcasts = ConcurrentHashMap.newKeySet();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sse-sender");
        t.setDaemon(true);
        return t;
    });

    /** Subscribes to the notifications channel. */
    public SseEmitter subscribe(String username) {
        return subscribe(username, NOTIFICATIONS_EVENT);
    }

    /**
     * Registers a subscriber on a channel (the channel is the event name it receives). Evicts the user's oldest
     * stream on that channel when over the per-user cap. Returns null if the server is full.
     */
    public synchronized SseEmitter subscribe(String username, String channel) {
        long tenantId = currentTenantId();
        List<Client> mine = new ArrayList<>();
        clients.values().forEach(c -> {
            if (c.tenantId() == tenantId && c.username().equals(username) && c.channel().equals(channel)) mine.add(c);
        });
        if (mine.size() >= MAX_CONNECTIONS_PER_USER) {
            mine.stream().min(Comparator.comparingLong(Client::id)).ifPresent(old -> {
                clients.remove(old.id());
                old.emitter().complete();
            });
        } else if (clients.size() >= MAX_CONNECTIONS) {
            return null;
        }

        long id = sequence.incrementAndGet();
        SseEmitter emitter = new SseEmitter(MAX_STREAM_MILLIS);
        Client client = new Client(id, tenantId, username, channel, emitter);
        Runnable cleanup = () -> clients.remove(id);
        emitter.onCompletion(cleanup);
        // Rotating a long-lived stream is expected: complete it cleanly so the client simply reconnects.
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(t -> cleanup.run());
        clients.put(id, client);
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok").reconnectTime(3000));
        } catch (IOException | IllegalStateException e) {
            cleanup.run();
        }
        return emitter;
    }

    /** Signals notification subscribers once the surrounding transaction (if any) has committed. Nothing is sent on rollback. */
    public void publishChangedAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(NOTIFICATIONS_EVENT);
                }
            });
        } else {
            broadcast(NOTIFICATIONS_EVENT);
        }
    }

    /** Signals only one manager's own open screens (their other tabs and devices), after commit. Others are not disturbed. */
    public void publishChangedForUserAfterCommit(String username) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcastToUser(username);
                }
            });
        } else {
            broadcastToUser(username);
        }
    }

    void broadcastToUser(String username) {
        // Every cafe has its own "admin": the key and the send are both limited to the cafe doing the work.
        long tenantId = currentTenantId();
        String key = tenantId + ":" + NOTIFICATIONS_EVENT + "@" + username;
        if (pendingBroadcasts.add(key)) {
            try {
                sender.execute(() -> {
                    pendingBroadcasts.remove(key);
                    sendToUser(tenantId, username);
                });
            } catch (RuntimeException rejected) {
                pendingBroadcasts.remove(key);
            }
        }
    }

    private void sendToUser(long tenantId, String username) {
        for (Client c : clients.values()) {
            if (c.tenantId() != tenantId || !c.channel().equals(NOTIFICATIONS_EVENT) || !c.username().equals(username)) {
                continue;
            }
            try {
                c.emitter().send(SseEmitter.event().name(NOTIFICATIONS_EVENT).data("1"));
            } catch (IOException | IllegalStateException e) {
                clients.remove(c.id());
                try { c.emitter().complete(); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Signals POS subscribers that orders/tables changed. Called from entity lifecycle callbacks, so it can fire many
     * times in one transaction; it registers a single after-commit signal per transaction. Nothing is sent on rollback.
     */
    public void publishOrdersChangedAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            if (TransactionSynchronizationManager.hasResource(ORDERS_SIGNAL_KEY)) {
                return;
            }
            TransactionSynchronizationManager.bindResource(ORDERS_SIGNAL_KEY, Boolean.TRUE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(ORDERS_EVENT);
                }

                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(ORDERS_SIGNAL_KEY)) {
                        TransactionSynchronizationManager.unbindResource(ORDERS_SIGNAL_KEY);
                    }
                }
            });
        } else {
            broadcast(ORDERS_EVENT);
        }
    }

    /**
     * Hands the write to a single sender thread so a slow browser can never stall the committing request thread.
     * Bursts of the same event coalesce into one signal.
     */
    void broadcast(String eventName) {
        long tenantId = currentTenantId();
        String key = tenantId + ":" + eventName;
        if (pendingBroadcasts.add(key)) {
            try {
                sender.execute(() -> {
                    pendingBroadcasts.remove(key);
                    sendToChannel(tenantId, eventName);
                });
            } catch (RuntimeException rejected) {
                pendingBroadcasts.remove(key);
            }
        }
    }

    private void sendToChannel(long tenantId, String eventName) {
        for (Client c : clients.values()) {
            if (c.tenantId() != tenantId || !c.channel().equals(eventName)) {
                continue;
            }
            try {
                c.emitter().send(SseEmitter.event().name(eventName).data("1"));
            } catch (IOException | IllegalStateException e) {
                clients.remove(c.id());
                try { c.emitter().complete(); } catch (Exception ignored) { }
            }
        }
    }

    /** Keeps proxies from closing idle streams and evicts connections whose browser has gone away. */
    @Scheduled(fixedDelay = 25000, initialDelay = 25000)
    void heartbeat() {
        if (!com.neram.pos.config.JobMode.interactive()) return;
        for (Client c : clients.values()) {
            try {
                c.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                clients.remove(c.id());
                try { c.emitter().complete(); } catch (Exception ignored) { }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
        clients.values().forEach(c -> { try { c.emitter().complete(); } catch (Exception ignored) { } });
        clients.clear();
    }

    public int connectionCount() {
        return clients.size();
    }
}
