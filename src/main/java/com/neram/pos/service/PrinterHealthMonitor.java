package com.neram.pos.service;

import com.neram.pos.dto.PrintBridgeHeartbeatDTO;
import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrintJobStatus;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.entity.SystemNotification.NotificationType;
import com.neram.pos.repository.PrintJobRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Watches the print bridge and the three printers and raises bell notifications (visible to every
 * manager and admin) when something stops printing, plus a "back online" notice when it recovers.
 *
 * One alert per incident (not one per check): a problem must last {@code offline-after-seconds} before
 * it is reported, and is reported once until it clears. Failed print jobs are reported once each.
 */
@Service
@Slf4j
public class PrinterHealthMonitor {

    /** Incidents that can be open. referenceId on the notification = id(), used to auto-resolve the alert. */
    enum Incident {
        BRIDGE(1, "Print bridge"),
        BILL(2, "Bill printer"),
        KOT(3, "Kitchen KOT printer"),
        JUICE(4, "Juice KOT printer"),
        STUCK_JOBS(5, "Print queue");

        final long id;
        final String label;
        Incident(long id, String label) { this.id = id; this.label = label; }
    }

    private static class State {
        final long since;
        boolean alerted;
        Long notificationId;
        State(long since) { this.since = since; }
    }

    private final PrintBridgeService bridge;
    private final PrintJobRepository jobs;
    private final SystemNotificationRepository notifications;
    private final NotificationStreamService stream;

    private final long offlineAfterMs;
    private final long stuckAfterMs;
    private final long startupGraceMs;

    /** Open incidents and already-reported jobs of ONE cafe; each cafe has its own so alerts never mix. */
    private static final class CafeState {
        final Map<Incident, State> open = new EnumMap<>(Incident.class);
        /** Jobs already reported since startup, so deleting the notification does not bring the alert back. */
        final java.util.Set<Long> reportedJobs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    }

    private final java.util.concurrent.ConcurrentHashMap<Long, CafeState> cafes = new java.util.concurrent.ConcurrentHashMap<>();

    private CafeState cafe() {
        return cafes.computeIfAbsent(com.neram.pos.tenancy.TenantContext.currentId().orElse(0L), id -> new CafeState());
    }
    private LongSupplier clock = System::currentTimeMillis;
    private long startedAt;

    @Autowired
    public PrinterHealthMonitor(PrintBridgeService bridge,
                                PrintJobRepository jobs,
                                SystemNotificationRepository notifications,
                                NotificationStreamService stream,
                                @Value("${neram.alerts.printer.offline-after-seconds:120}") long offlineAfterSeconds,
                                @Value("${neram.alerts.printer.job-stuck-after-seconds:180}") long stuckAfterSeconds,
                                @Value("${neram.alerts.printer.startup-grace-seconds:180}") long startupGraceSeconds) {
        this.bridge = bridge;
        this.jobs = jobs;
        this.notifications = notifications;
        this.stream = stream;
        this.offlineAfterMs = offlineAfterSeconds * 1000L;
        this.stuckAfterMs = stuckAfterSeconds * 1000L;
        this.startupGraceMs = startupGraceSeconds * 1000L;
        this.startedAt = clock.getAsLong();
    }

    /** Test hook: replace the time source and the moment the monitor "started". */
    void useClock(LongSupplier clock, long startedAtMillis) {
        this.clock = clock;
        this.startedAt = startedAtMillis;
    }

    /** Run once per cafe whose bridge has connected since startup; see PrinterHealthJob. */
    public void scheduledCheck() {
        if (!com.neram.pos.config.JobMode.interactive()) return;
        try {
            check();
        } catch (Exception e) {
            log.warn("Printer health check failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void check() {
        long now = clock.getAsLong();
        long lastHb = bridge.getLastHeartbeatTimestamp();

        // ---- Bridge: no heartbeat for too long (or none at all after the startup grace period)
        boolean bridgeDown = (lastHb == 0)
                ? (now - startedAt) >= startupGraceMs
                : (now - lastHb) >= offlineAfterMs;
        String bridgeMsg = lastHb == 0
                ? "No heartbeat has been received from the print bridge since the server started. Kitchen tickets and bills are waiting in the queue and will print when it connects. Check the ESP32 power and Wi-Fi."
                : "No heartbeat from the print bridge for " + minutes(now - lastHb) + " (last seen " + clockTime(lastHb) + "). Kitchen tickets and bills are waiting in the queue and will print when it reconnects. Check the ESP32 power and Wi-Fi.";
        update(Incident.BRIDGE, bridgeDown, 0, "Print bridge offline", bridgeMsg, now);

        // ---- Printers (only meaningful while the bridge is reporting)
        PrintBridgeHeartbeatDTO hb = bridge.getLatestHeartbeat();
        if (!bridgeDown && hb != null) {
            checkPrinter(Incident.BILL, hb.getBillPrinterHost(), hb.getBillPrinterPort(), hb.getBillPrinterReachable(), now);
            checkPrinter(Incident.KOT, hb.getKotPrinterHost(), hb.getKotPrinterPort(), hb.getKotPrinterReachable(), now);
            checkPrinter(Incident.JUICE, hb.getJuiceKotPrinterHost(), hb.getJuiceKotPrinterPort(), hb.getJuiceKotPrinterReachable(), now);

            // ---- Jobs waiting too long although the bridge is up
            LocalDateTime threshold = LocalDateTime.now().minusNanos(stuckAfterMs * 1_000_000L);
            long waiting = jobs.countByStatusAndCreatedAtBefore(PrintJobStatus.PENDING, threshold);
            update(Incident.STUCK_JOBS, waiting > 0, 0, "Print jobs are not being picked up",
                    waiting + " print job(s) have been waiting more than " + minutes(stuckAfterMs)
                            + " although the print bridge is online. Check the printers (paper, power, network) and the Bill Log.", now);
        }

        // ---- Jobs that ended FAILED / NEEDS_REVIEW (each reported once, survives restarts)
        List<PrintJob> failed = jobs.findByStatusInAndUpdatedAtAfter(
                List.of(PrintJobStatus.FAILED, PrintJobStatus.NEEDS_REVIEW), LocalDateTime.now().minusMinutes(30));
        for (PrintJob job : failed) {
            if (cafe().reportedJobs.contains(job.getId())) continue;
            if (!notifications.findByTypeAndReferenceId(NotificationType.PRINT_JOB_FAILED, job.getId()).isEmpty()) {
                cafe().reportedJobs.add(job.getId());     // already reported before a restart
                continue;
            }
            boolean review = job.getStatus() == PrintJobStatus.NEEDS_REVIEW;
            if (cafe().reportedJobs.size() > 1000) cafe().reportedJobs.clear();
            cafe().reportedJobs.add(job.getId());
            String what = describeJob(job);
            notify(NotificationType.PRINT_JOB_FAILED, job.getId(),
                    (review ? "Print needs review: " : "Print failed: ") + what,
                    (review ? "The printer may have printed part of this ticket, so it was not retried automatically. "
                            : "The ticket could not be printed after several attempts. ")
                            + (job.getLastErrorMessage() != null ? "Reason: " + job.getLastErrorMessage() + ". " : "")
                            + "Open Bill Log & Printing to check the printout and retry if needed.");
        }
    }

    private void checkPrinter(Incident incident, String host, Integer port, Boolean reachable, long now) {
        // Not configured / not reported -> unknown. Unknown must neither raise an alarm nor claim recovery.
        if (host == null || host.isBlank() || reachable == null) {
            return;
        }
        update(incident, !reachable, offlineAfterMs, incident.label + " offline",
                incident.label + " (" + host + (port != null ? ":" + port : "") + ") is not reachable from the print bridge. "
                        + "Tickets for it are waiting in the queue. Check the printer's power, paper and network connection.", now);
    }

    /** Opens / keeps / closes an incident and raises the alert once it has lasted {@code confirmAfterMs}. */
    private void update(Incident incident, boolean bad, long confirmAfterMs, String title, String message, long now) {
        State st = cafe().open.get(incident);
        if (bad) {
            if (st == null) { st = new State(now); cafe().open.put(incident, st); }
            if (!st.alerted && now - st.since >= confirmAfterMs) {
                SystemNotification n = notify(NotificationType.PRINTER_ALERT, incident.id, title, message);
                st.alerted = true;
                st.notificationId = n.getId();
            }
        } else if (st != null) {
            cafe().open.remove(incident);
            if (st.alerted) {
                if (st.notificationId != null) {
                    notifications.findById(st.notificationId).ifPresent(n -> {
                        n.setStatus(SystemNotification.Status.READ);   // the problem is over: clear it from the badge
                        notifications.save(n);
                    });
                }
                notify(NotificationType.PRINTER_ALERT, incident.id, incident.label + " back online",
                        incident.label + " is working again (was down for " + minutes(now - st.since) + ").");
            }
        }
    }

    private SystemNotification notify(NotificationType type, Long referenceId, String title, String message) {
        SystemNotification saved = notifications.save(SystemNotification.builder()
                .type(type)
                .title(title)
                .message(message.length() > 1000 ? message.substring(0, 1000) : message)
                .referenceId(referenceId)
                .createdBy("System")
                .status(SystemNotification.Status.UNREAD)
                .createdAt(LocalDateTime.now())
                .build());
        stream.publishChangedAfterCommit();
        log.warn("Printer alert raised: {}", title);
        return saved;
    }

    private static String describeJob(PrintJob job) {
        String type = job.getJobType() == null ? "print job"
                : job.getJobType().replace("_", " ").replace("KOT UPDATE", "updated KOT");
        return type + (job.getBillNumber() != null ? " for order " + job.getBillNumber() : "");
    }

    private static String minutes(long ms) {
        long m = Math.max(1, Math.round(ms / 60000.0));
        return m + (m == 1 ? " minute" : " minutes");
    }

    private static String clockTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.of("Asia/Kolkata"))
                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)).toLowerCase();
    }
}
