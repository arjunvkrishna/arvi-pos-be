package com.neram.pos.service;

import com.neram.pos.controller.NotificationController;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.LeaveRequestRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationDeleteTest {

    private SystemNotificationRepository repo;
    private AuditLogService audit;
    private NotificationStreamService stream;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        repo = mock(SystemNotificationRepository.class);
        audit = mock(AuditLogService.class);
        stream = mock(NotificationStreamService.class);
        controller = new NotificationController(repo, mock(LeaveRequestRepository.class),
                mock(InventoryItemRepository.class), audit, stream);
    }

    private SystemNotification notification(long id, SystemNotification.Status status) {
        return SystemNotification.builder().id(id).title("Low Stock Alert: Milk")
                .message("m").type(SystemNotification.NotificationType.LOW_STOCK).status(status).build();
    }

    @Test
    void deleteOneRemovesTheNotificationAndSignalsOtherDevices() {
        SystemNotification n = notification(7, SystemNotification.Status.READ);
        when(repo.findById(7L)).thenReturn(Optional.of(n));

        ResponseEntity<Void> res = controller.deleteNotification(7L);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(repo).delete(n);
        verify(audit).log(eq("DELETE_NOTIFICATION"), eq("NOTIFICATION"), eq(7L), anyString(), any());
        verify(stream).publishChangedAfterCommit();
    }

    @Test
    void deleteOneAlsoWorksForUnreadNotifications() {
        SystemNotification n = notification(8, SystemNotification.Status.UNREAD);
        when(repo.findById(8L)).thenReturn(Optional.of(n));
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteNotification(8L).getStatusCode());
        verify(repo).delete(n);
    }

    @Test
    void deletingSomethingAlreadyGoneIsHarmless() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<Void> res = controller.deleteNotification(99L);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(repo, never()).delete(any());
        verify(audit, never()).log(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void deleteReadOnlyTargetsReadNotificationsAndReportsTheCount() {
        when(repo.deleteAllByStatus(SystemNotification.Status.READ)).thenReturn(12);

        ResponseEntity<Map<String, Integer>> res = controller.deleteReadNotifications();

        assertEquals(12, res.getBody().get("deleted"));
        verify(repo).deleteAllByStatus(SystemNotification.Status.READ);
        verify(repo, never()).deleteAllByStatus(SystemNotification.Status.UNREAD);   // unread alerts are never removed
        verify(audit).log(eq("DELETE_READ_NOTIFICATIONS"), eq("NOTIFICATION"), isNull(), isNull(), contains("12"));
        verify(stream).publishChangedAfterCommit();
    }

    @Test
    void deleteReadWithNothingToDeleteWritesNoAuditEntry() {
        when(repo.deleteAllByStatus(SystemNotification.Status.READ)).thenReturn(0);
        assertEquals(0, controller.deleteReadNotifications().getBody().get("deleted"));
        verify(audit, never()).log(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void deleteEndpointsAreRestrictedToAdminAndManager() throws Exception {
        for (String name : new String[]{"deleteNotification", "deleteReadNotifications"}) {
            Method m = name.equals("deleteNotification")
                    ? NotificationController.class.getMethod(name, Long.class)
                    : NotificationController.class.getMethod(name);
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            assertNotNull(pa, name + " must be protected");
            assertEquals("hasAnyRole('ADMIN', 'MANAGER')", pa.value());
        }
    }
}
