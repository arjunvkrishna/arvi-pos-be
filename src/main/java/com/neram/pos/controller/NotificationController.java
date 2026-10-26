package com.neram.pos.controller;

import com.neram.pos.dto.NotificationSummaryDTO;
import com.neram.pos.dto.StockAlertRequestDTO;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.LeaveRequest;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.LeaveRequestRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import com.neram.pos.service.AuditLogService;
import com.neram.pos.service.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SystemNotificationRepository notificationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final AuditLogService auditLogService;
    private final NotificationStreamService notificationStreamService;

    /** Single SSE stream per browser session; replaces periodic polling. Carries only a content-free change signal. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SseEmitter> stream(Authentication auth) {
        SseEmitter emitter = notificationStreamService.subscribe(auth.getName());
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<NotificationSummaryDTO> getNotifications() {
        List<SystemNotification> list = notificationRepository.findTop50ByOrderByCreatedAtDesc();
        long unread = notificationRepository.countByStatus(SystemNotification.Status.UNREAD);
        long pendingLeaves = leaveRequestRepository.countByStatus(LeaveRequest.Status.PENDING);
        long lowStockCount = notificationRepository.countByTypeAndStatus(SystemNotification.NotificationType.LOW_STOCK, SystemNotification.Status.UNREAD);

        return ResponseEntity.ok(NotificationSummaryDTO.builder()
                .unreadCount(unread)
                .pendingLeavesCount(pendingLeaves)
                .lowStockAlertsCount(lowStockCount)
                .notifications(list)
                .build());
    }

    @PostMapping("/stock-alert")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<SystemNotification> reportLowStock(@RequestBody StockAlertRequestDTO req, Authentication auth) {
        String caller = (auth != null && auth.getName() != null) ? auth.getName() : "Staff";

        InventoryItem invItem = null;
        if (req.getInventoryItemId() != null) {
            invItem = inventoryItemRepository.findById(req.getInventoryItemId()).orElse(null);
        }

        String item = invItem != null ? invItem.getName() : (req.getItemName() != null ? req.getItemName() : "Item #" + req.getInventoryItemId());
        String unit = invItem != null ? (invItem.getUnit() != null ? invItem.getUnit() : "units") : (req.getUnit() != null ? req.getUnit() : "units");
        Double qty = invItem != null ? (invItem.getQuantity() != null ? invItem.getQuantity() : 0.0) : (req.getCurrentQuantity() != null ? req.getCurrentQuantity() : 0.0);
        Double min = invItem != null ? (invItem.getMinQuantity() != null ? invItem.getMinQuantity() : 0.0) : (req.getMinQuantity() != null ? req.getMinQuantity() : 0.0);
        String supplier = (invItem != null && invItem.getSupplier() != null && !invItem.getSupplier().isBlank()) ? invItem.getSupplier() : "N/A";

        String noteText = req.getEffectiveNote();
        String extra = (noteText != null && !noteText.isBlank()) ? " | Note: " + noteText.trim() : "";

        String title = String.format("Low Stock Alert: %s (%.2f %s left)", item, qty, unit);
        String msg = String.format("Item: %s | Current Stock: %.2f %s (Min: %.2f %s) | Supplier: %s | Reported by: %s%s",
                item, qty, unit, min, unit, supplier, caller, extra);

        SystemNotification notification = SystemNotification.builder()
                .type(SystemNotification.NotificationType.LOW_STOCK)
                .title(title)
                .message(msg)
                .referenceId(req.getInventoryItemId())
                .createdBy(caller)
                .status(SystemNotification.Status.UNREAD)
                .createdAt(LocalDateTime.now())
                .build();

        SystemNotification saved = notificationRepository.save(notification);
        auditLogService.log("STOCK_ALERT", "INVENTORY", req.getInventoryItemId(), null, msg);
        notificationStreamService.publishChangedAfterCommit();
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SystemNotification> markAsRead(@PathVariable Long id) {
        SystemNotification notif = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
        notif.setStatus(SystemNotification.Status.READ);
        SystemNotification savedNotif = notificationRepository.save(notif);
        notificationStreamService.publishChangedAfterCommit();
        return ResponseEntity.ok(savedNotif);
    }

    @PostMapping("/mark-all-read")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> markAllRead() {
        List<SystemNotification> unread = notificationRepository.findByStatusOrderByCreatedAtDesc(SystemNotification.Status.UNREAD);
        for (SystemNotification n : unread) {
            n.setStatus(SystemNotification.Status.READ);
        }
        notificationRepository.saveAll(unread);
        notificationStreamService.publishChangedAfterCommit();
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete one notification (read or unread). Idempotent: deleting something another device already
     * removed still returns 204, so a stale list never shows an error.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            notificationRepository.delete(n);
            auditLogService.log("DELETE_NOTIFICATION", "NOTIFICATION", id, n.getTitle(), null);
        });
        notificationStreamService.publishChangedAfterCommit();
        return ResponseEntity.noContent().build();
    }

    /** Delete every notification that has already been marked read. Unread notifications are never touched. */
    @DeleteMapping("/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Integer>> deleteReadNotifications() {
        int deleted = notificationRepository.deleteAllByStatus(SystemNotification.Status.READ);
        if (deleted > 0) {
            auditLogService.log("DELETE_READ_NOTIFICATIONS", "NOTIFICATION", null, null, "Deleted " + deleted + " read notification(s)");
        }
        notificationStreamService.publishChangedAfterCommit();
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
