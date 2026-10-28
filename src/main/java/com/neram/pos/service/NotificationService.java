package com.neram.pos.service;

import com.neram.pos.dto.NotificationDTO;
import com.neram.pos.dto.NotificationSummaryDTO;
import com.neram.pos.entity.LeaveRequest;
import com.neram.pos.entity.NotificationUserState;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.repository.LeaveRequestRepository;
import com.neram.pos.repository.NotificationUserStateRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Notifications are personal. A notification is one shared record, but whether it is read, and whether it is still in
 * the list, is kept per manager: when one manager reads or clears it, the others still see it as it was.
 * What the system itself has resolved (a handled leave request, a printer that came back) is read for everybody.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int LIST_SIZE = 50;
    static final int KEEP_DAYS = 90;

    private final SystemNotificationRepository notifications;
    private final NotificationUserStateRepository states;
    private final LeaveRequestRepository leaves;
    private final UserRepository users;
    private final AuditLogService audit;
    private final NotificationStreamService stream;

    /** The caller's list (newest 50 they have not cleared), their unread counts, and the shared count of leave requests waiting. */
    @Transactional(readOnly = true)
    public NotificationSummaryDTO summary(String username) {
        Long uid = userId(username);
        List<SystemNotification> list = notifications.findVisibleFor(uid, PageRequest.of(0, LIST_SIZE));
        Set<Long> readByMe = states.findByUserIdAndNotificationIdIn(uid, list.stream().map(SystemNotification::getId).collect(Collectors.toList()))
                .stream().filter(s -> s.getReadAt() != null).map(NotificationUserState::getNotificationId).collect(Collectors.toSet());
        return NotificationSummaryDTO.builder()
                .unreadCount(notifications.countUnreadFor(uid, SystemNotification.Status.UNREAD))
                .pendingLeavesCount(leaves.countByStatus(LeaveRequest.Status.PENDING))
                .lowStockAlertsCount(notifications.countUnreadOfTypeFor(uid, SystemNotification.NotificationType.LOW_STOCK, SystemNotification.Status.UNREAD))
                .notifications(list.stream()
                        .map(n -> NotificationDTO.of(n, n.getStatus() == SystemNotification.Status.READ || readByMe.contains(n.getId())))
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public NotificationDTO markRead(String username, Long id) {
        Long uid = userId(username);
        SystemNotification n = notifications.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
        states.markRead(id, uid);
        stream.publishChangedForUserAfterCommit(username);
        return NotificationDTO.of(n, true);
    }

    @Transactional
    public void markAllRead(String username) {
        states.markAllRead(userId(username));
        stream.publishChangedForUserAfterCommit(username);
    }

    /** Clears one notification from this manager's list. Idempotent: clearing something already gone (or never there) is not an error. */
    @Transactional
    public void dismiss(String username, Long id) {
        Long uid = userId(username);
        notifications.findById(id).ifPresent(n -> {
            states.dismiss(id, uid);
            audit.log("DELETE_NOTIFICATION", "NOTIFICATION", id, n.getTitle(), "Cleared from " + username + "'s own list");
        });
        stream.publishChangedForUserAfterCommit(username);
    }

    /** Clears every notification this manager has read from their own list. Unread ones stay. */
    @Transactional
    public int dismissRead(String username) {
        int cleared = states.dismissRead(userId(username));
        if (cleared > 0) {
            audit.log("DELETE_READ_NOTIFICATIONS", "NOTIFICATION", null, null, "Cleared " + cleared + " read notification(s) from " + username + "'s own list");
        }
        stream.publishChangedForUserAfterCommit(username);
        return cleared;
    }

    private Long userId(String username) {
        return users.findByUsername(username).map(u -> u.getId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + username));
    }

    /** Old notifications are removed for everyone, so the table does not grow forever now that clearing is personal. */
    public void removeOld() {
        removeOlderThan(LocalDateTime.now().minusDays(KEEP_DAYS));
    }

    int removeOlderThan(LocalDateTime cutoff) {
        int removed = notifications.deleteOlderThan(cutoff);
        if (removed > 0) log.info("Removed {} notification(s) older than {} days", removed, KEEP_DAYS);
        return removed;
    }
}
