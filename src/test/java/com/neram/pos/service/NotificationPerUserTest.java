package com.neram.pos.service;

import com.neram.pos.controller.NotificationController;
import com.neram.pos.dto.NotificationDTO;
import com.neram.pos.dto.NotificationSummaryDTO;
import com.neram.pos.entity.NotificationUserState;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.entity.User;
import com.neram.pos.repository.LeaveRequestRepository;
import com.neram.pos.repository.NotificationUserStateRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Notifications are personal: what one manager reads or clears must not change what another manager sees. */
class NotificationPerUserTest {

    private SystemNotificationRepository notifications;
    private NotificationUserStateRepository states;
    private LeaveRequestRepository leaves;
    private UserRepository users;
    private AuditLogService audit;
    private NotificationStreamService stream;
    private NotificationService service;

    private static User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        return u;
    }

    private static SystemNotification note(long id, SystemNotification.Status status, SystemNotification.NotificationType type) {
        return SystemNotification.builder().id(id).title("t" + id).message("m").type(type).status(status).createdAt(LocalDateTime.now()).build();
    }

    @BeforeEach
    void setUp() {
        notifications = mock(SystemNotificationRepository.class);
        states = mock(NotificationUserStateRepository.class);
        leaves = mock(LeaveRequestRepository.class);
        users = mock(UserRepository.class);
        audit = mock(AuditLogService.class);
        stream = mock(NotificationStreamService.class);
        service = new NotificationService(notifications, states, leaves, users, audit, stream);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));
        when(users.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    }

    @Test
    @DisplayName("the same notification is unread for one manager and read for another")
    void statusIsPerManager() {
        SystemNotification a = note(10, SystemNotification.Status.UNREAD, SystemNotification.NotificationType.LOW_STOCK);
        SystemNotification b = note(11, SystemNotification.Status.UNREAD, SystemNotification.NotificationType.GENERAL);
        SystemNotification resolved = note(12, SystemNotification.Status.READ, SystemNotification.NotificationType.PRINTER_ALERT);
        when(notifications.findVisibleFor(eq(1L), any(Pageable.class))).thenReturn(List.of(a, b, resolved));
        when(notifications.findVisibleFor(eq(2L), any(Pageable.class))).thenReturn(List.of(a, b, resolved));
        // alice has read #10; bob has read nothing
        when(states.findByUserIdAndNotificationIdIn(eq(1L), any())).thenReturn(List.of(NotificationUserState.builder().notificationId(10L).userId(1L).readAt(LocalDateTime.now()).build()));
        when(states.findByUserIdAndNotificationIdIn(eq(2L), any())).thenReturn(List.of());

        List<NotificationDTO> forAlice = service.summary("alice").getNotifications();
        List<NotificationDTO> forBob = service.summary("bob").getNotifications();

        assertEquals(SystemNotification.Status.READ, forAlice.get(0).getStatus());
        assertEquals(SystemNotification.Status.UNREAD, forAlice.get(1).getStatus());
        assertEquals(SystemNotification.Status.READ, forAlice.get(2).getStatus(), "what the system resolved is read for everybody");
        assertEquals(SystemNotification.Status.UNREAD, forBob.get(0).getStatus(), "bob still has #10 unread");
        assertEquals(SystemNotification.Status.UNREAD, forBob.get(1).getStatus());
        assertEquals(SystemNotification.Status.READ, forBob.get(2).getStatus());
    }

    @Test
    @DisplayName("unread counts and the list come from the caller's own state, never another manager's")
    void countsAreAskedForTheCaller() {
        when(notifications.findVisibleFor(anyLong(), any(Pageable.class))).thenReturn(List.of());
        when(notifications.countUnreadFor(eq(1L), any())).thenReturn(3L);
        when(notifications.countUnreadFor(eq(2L), any())).thenReturn(5L);
        when(notifications.countUnreadOfTypeFor(eq(1L), eq(SystemNotification.NotificationType.LOW_STOCK), any())).thenReturn(1L);
        when(notifications.countUnreadOfTypeFor(eq(2L), eq(SystemNotification.NotificationType.LOW_STOCK), any())).thenReturn(4L);
        when(leaves.countByStatus(any())).thenReturn(2L);

        NotificationSummaryDTO alice = service.summary("alice");
        NotificationSummaryDTO bob = service.summary("bob");

        assertEquals(3, alice.getUnreadCount());
        assertEquals(1, alice.getLowStockAlertsCount());
        assertEquals(5, bob.getUnreadCount());
        assertEquals(4, bob.getLowStockAlertsCount());
        assertEquals(2, alice.getPendingLeavesCount(), "leave requests waiting are shared work, not personal state");
    }

    @Test
    @DisplayName("reading marks it read for the caller only and signals only the caller's own screens")
    void markReadIsPersonal() {
        SystemNotification n = note(10, SystemNotification.Status.UNREAD, SystemNotification.NotificationType.LOW_STOCK);
        when(notifications.findById(10L)).thenReturn(Optional.of(n));

        NotificationDTO result = service.markRead("alice", 10L);

        assertEquals(SystemNotification.Status.READ, result.getStatus());
        verify(states).markRead(10L, 1L);
        verify(states, never()).markRead(anyLong(), eq(2L));
        assertEquals(SystemNotification.Status.UNREAD, n.getStatus(), "the shared record is left as it was");
        verify(notifications, never()).save(any());
        verify(stream).publishChangedForUserAfterCommit("alice");
        verify(stream, never()).publishChangedAfterCommit();
    }

    @Test
    @DisplayName("mark all read only touches the caller")
    void markAllReadIsPersonal() {
        service.markAllRead("alice");
        verify(states).markAllRead(1L);
        verify(states, never()).markAllRead(2L);
        verify(stream).publishChangedForUserAfterCommit("alice");
    }

    @Test
    @DisplayName("clearing removes it from the caller's list and never deletes the notification")
    void dismissNeverDeletes() {
        when(notifications.findById(10L)).thenReturn(Optional.of(note(10, SystemNotification.Status.UNREAD, SystemNotification.NotificationType.LOW_STOCK)));

        service.dismiss("alice", 10L);

        verify(states).dismiss(10L, 1L);
        verify(notifications, never()).delete(any());
        verify(notifications, never()).deleteById(any());
        verify(notifications, never()).deleteAllByStatus(any());
        verify(audit).log(eq("DELETE_NOTIFICATION"), eq("NOTIFICATION"), eq(10L), anyString(), contains("alice"));
        verify(stream).publishChangedForUserAfterCommit("alice");
    }

    @Test
    @DisplayName("clearing something that is already gone is harmless and writes nothing")
    void dismissUnknownIsHarmless() {
        when(notifications.findById(99L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.dismiss("alice", 99L));
        verify(states, never()).dismiss(anyLong(), anyLong());
        verify(audit, never()).log(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("clear read: only the caller's own read ones, counted and audited; nothing to clear writes no audit")
    void dismissReadIsPersonal() {
        when(states.dismissRead(1L)).thenReturn(4);
        when(states.dismissRead(2L)).thenReturn(0);

        assertEquals(4, service.dismissRead("alice"));
        verify(audit).log(eq("DELETE_READ_NOTIFICATIONS"), eq("NOTIFICATION"), isNull(), isNull(), contains("4"));
        reset(audit);
        assertEquals(0, service.dismissRead("bob"));
        verify(audit, never()).log(anyString(), anyString(), any(), any(), any());
        verify(notifications, never()).deleteAllByStatus(any());
    }

    @Test
    @DisplayName("an unknown account cannot read or clear anything")
    void unknownUserIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.summary("ghost"));
        assertThrows(IllegalArgumentException.class, () -> service.markAllRead("ghost"));
    }

    @Test
    @DisplayName("housekeeping removes old notifications for everyone")
    void oldNotificationsAreRemoved() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        when(notifications.deleteOlderThan(cutoff)).thenReturn(7);
        assertEquals(7, service.removeOlderThan(cutoff));
    }

    @Test
    @DisplayName("every notification endpoint is still for admin and manager only")
    void endpointsAreRestricted() throws Exception {
        for (String name : new String[]{"getNotifications", "markAllRead", "deleteReadNotifications"}) {
            Method m = NotificationController.class.getMethod(name, Authentication.class);
            assertEquals("hasAnyRole('ADMIN', 'MANAGER')", m.getAnnotation(PreAuthorize.class).value(), name);
        }
        for (String name : new String[]{"markAsRead", "deleteNotification"}) {
            Method m = NotificationController.class.getMethod(name, Long.class, Authentication.class);
            assertEquals("hasAnyRole('ADMIN', 'MANAGER')", m.getAnnotation(PreAuthorize.class).value(), name);
        }
    }
}
