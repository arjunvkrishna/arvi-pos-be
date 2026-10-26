package com.neram.pos.service;

import com.neram.pos.dto.PrintBridgeHeartbeatDTO;
import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrintJobStatus;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.entity.SystemNotification.NotificationType;
import com.neram.pos.repository.PrintJobRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrinterHealthMonitorTest {

    private static final long MIN = 60_000L;

    private PrintBridgeService bridge;
    private PrintJobRepository jobs;
    private SystemNotificationRepository notifications;
    private NotificationStreamService stream;
    private PrinterHealthMonitor monitor;
    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final List<SystemNotification> saved = new ArrayList<>();
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        bridge = mock(PrintBridgeService.class);
        jobs = mock(PrintJobRepository.class);
        notifications = mock(SystemNotificationRepository.class);
        stream = mock(NotificationStreamService.class);
        monitor = new PrinterHealthMonitor(bridge, jobs, notifications, stream, 120, 180, 180);
        monitor.useClock(now::get, now.get());

        when(notifications.save(any(SystemNotification.class))).thenAnswer(i -> {
            SystemNotification n = i.getArgument(0);
            if (n.getId() == null) { n.setId(nextId++); saved.add(n); }
            return n;
        });
        when(notifications.findById(any())).thenAnswer(i -> saved.stream().filter(n -> n.getId().equals(i.getArgument(0))).findFirst());
        when(notifications.findByTypeAndReferenceId(any(), any())).thenAnswer(i -> saved.stream()
                .filter(n -> n.getType() == i.getArgument(0) && n.getReferenceId().equals(i.getArgument(1))).toList());
        when(jobs.findByStatusInAndUpdatedAtAfter(any(), any())).thenReturn(List.of());
    }

    private PrintBridgeHeartbeatDTO heartbeat(Boolean bill, Boolean kot, Boolean juice) {
        PrintBridgeHeartbeatDTO hb = new PrintBridgeHeartbeatDTO();
        hb.setBillPrinterHost("192.168.1.41"); hb.setBillPrinterPort(9100); hb.setBillPrinterReachable(bill);
        hb.setKotPrinterHost("192.168.1.47"); hb.setKotPrinterPort(9100); hb.setKotPrinterReachable(kot);
        hb.setJuiceKotPrinterHost("192.168.1.42"); hb.setJuiceKotPrinterPort(9100); hb.setJuiceKotPrinterReachable(juice);
        return hb;
    }

    private void bridgeSeenNow(PrintBridgeHeartbeatDTO hb) {
        when(bridge.getLastHeartbeatTimestamp()).thenReturn(now.get());
        when(bridge.getLatestHeartbeat()).thenReturn(hb);
    }

    private List<SystemNotification> alerts(NotificationType type) {
        return saved.stream().filter(n -> n.getType() == type).toList();
    }

    @Test
    void healthySystemRaisesNothing() {
        bridgeSeenNow(heartbeat(true, true, true));
        monitor.check();
        assertTrue(saved.isEmpty());
        verify(stream, never()).publishChangedAfterCommit();
    }

    @Test
    void kitchenPrinterOfflineAlertsOnlyAfterTwoMinutesAndOnlyOnce() {
        bridgeSeenNow(heartbeat(true, false, true));
        monitor.check();                                  // just went offline
        assertTrue(saved.isEmpty(), "a short blip must not alert");

        now.addAndGet(1 * MIN); bridgeSeenNow(heartbeat(true, false, true)); monitor.check();
        assertTrue(saved.isEmpty(), "1 minute is still below the 2 minute threshold");

        now.addAndGet(1 * MIN); bridgeSeenNow(heartbeat(true, false, true)); monitor.check();
        assertEquals(1, saved.size());
        SystemNotification n = saved.get(0);
        assertEquals(NotificationType.PRINTER_ALERT, n.getType());
        assertEquals("Kitchen KOT printer offline", n.getTitle());
        assertTrue(n.getMessage().contains("192.168.1.47"));
        assertEquals(SystemNotification.Status.UNREAD, n.getStatus());
        verify(stream, times(1)).publishChangedAfterCommit();

        for (int i = 0; i < 5; i++) { now.addAndGet(MIN); bridgeSeenNow(heartbeat(true, false, true)); monitor.check(); }
        assertEquals(1, saved.size(), "still offline: no repeated alerts");
    }

    @Test
    void recoveryRaisesBackOnlineAndClearsTheOriginalAlert() {
        bridgeSeenNow(heartbeat(true, false, true)); monitor.check();
        now.addAndGet(3 * MIN); bridgeSeenNow(heartbeat(true, false, true)); monitor.check();
        SystemNotification offline = saved.get(0);

        now.addAndGet(MIN); bridgeSeenNow(heartbeat(true, true, true)); monitor.check();

        assertEquals(2, saved.size());
        assertEquals("Kitchen KOT printer back online", saved.get(1).getTitle());
        assertEquals(SystemNotification.Status.READ, offline.getStatus(), "the offline alert no longer counts as unread");
    }

    @Test
    void aShortBlipThatRecoversBeforeTheThresholdNeverAlerts() {
        bridgeSeenNow(heartbeat(true, false, true)); monitor.check();
        now.addAndGet(MIN); bridgeSeenNow(heartbeat(true, true, true)); monitor.check();
        assertTrue(saved.isEmpty());
    }

    @Test
    void bridgeSilentForTwoMinutesAlertsAndSuppressesPrinterAlerts() {
        long lastSeen = now.get();
        when(bridge.getLastHeartbeatTimestamp()).thenReturn(lastSeen);
        when(bridge.getLatestHeartbeat()).thenReturn(heartbeat(false, false, false));

        now.addAndGet(3 * MIN);
        monitor.check();

        assertEquals(1, saved.size());
        assertEquals("Print bridge offline", saved.get(0).getTitle());
        assertEquals(1L, saved.get(0).getReferenceId());
    }

    @Test
    void noHeartbeatAtAllIsForgivenDuringStartupGraceThenReported() {
        when(bridge.getLastHeartbeatTimestamp()).thenReturn(0L);
        now.addAndGet(2 * MIN); monitor.check();
        assertTrue(saved.isEmpty(), "just restarted: the bridge has not had time to report");

        now.addAndGet(2 * MIN); monitor.check();       // 4 minutes after start, grace is 3
        assertEquals(1, saved.size());
        assertEquals("Print bridge offline", saved.get(0).getTitle());
    }

    @Test
    void unknownPrinterStateNeverAlertsAndNeverClaimsRecovery() {
        bridgeSeenNow(heartbeat(true, null, true));    // KOT state not reported
        now.addAndGet(5 * MIN); bridgeSeenNow(heartbeat(true, null, true)); monitor.check();
        assertTrue(saved.isEmpty());
    }

    @Test
    void failedPrintJobIsReportedExactlyOnce() {
        bridgeSeenNow(heartbeat(true, true, true));
        PrintJob job = PrintJob.builder().id(55L).jobType("KOT").billNumber("NRM-9").status(PrintJobStatus.FAILED)
                .lastErrorMessage("connection refused").build();
        when(jobs.findByStatusInAndUpdatedAtAfter(any(), any())).thenReturn(List.of(job));

        monitor.check();
        monitor.check();

        List<SystemNotification> failed = alerts(NotificationType.PRINT_JOB_FAILED);
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).getTitle().contains("NRM-9"));
        assertTrue(failed.get(0).getMessage().contains("connection refused"));
        assertEquals(55L, failed.get(0).getReferenceId());
    }

    @Test
    void deletingAFailedPrintAlertDoesNotBringItBack() {
        bridgeSeenNow(heartbeat(true, true, true));
        PrintJob job = PrintJob.builder().id(57L).jobType("KOT").billNumber("NRM-11").status(PrintJobStatus.FAILED).build();
        when(jobs.findByStatusInAndUpdatedAtAfter(any(), any())).thenReturn(List.of(job));
        monitor.check();
        assertEquals(1, alerts(NotificationType.PRINT_JOB_FAILED).size());

        saved.clear();                    // the manager deleted the notification from the bell
        monitor.check();
        monitor.check();

        assertTrue(saved.isEmpty(), "the same failed job must not be reported again after its alert was deleted");
    }

    @Test
    void needsReviewJobSaysThePrinterMayHavePrintedPartOfIt() {
        bridgeSeenNow(heartbeat(true, true, true));
        PrintJob job = PrintJob.builder().id(56L).jobType("BILL").billNumber("NRM-10").status(PrintJobStatus.NEEDS_REVIEW).build();
        when(jobs.findByStatusInAndUpdatedAtAfter(any(), any())).thenReturn(List.of(job));
        monitor.check();
        SystemNotification n = alerts(NotificationType.PRINT_JOB_FAILED).get(0);
        assertTrue(n.getTitle().startsWith("Print needs review"));
        assertTrue(n.getMessage().contains("part of this ticket"));
    }

    @Test
    void jobsWaitingTooLongWhileBridgeIsOnlineRaiseOneQueueAlert() {
        bridgeSeenNow(heartbeat(true, true, true));
        when(jobs.countByStatusAndCreatedAtBefore(eq(PrintJobStatus.PENDING), any())).thenReturn(2L);
        monitor.check(); monitor.check();
        List<SystemNotification> a = alerts(NotificationType.PRINTER_ALERT);
        assertEquals(1, a.size());
        assertEquals("Print jobs are not being picked up", a.get(0).getTitle());
    }
}
