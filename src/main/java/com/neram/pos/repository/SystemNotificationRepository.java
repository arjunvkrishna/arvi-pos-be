package com.neram.pos.repository;

import com.neram.pos.entity.SystemNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {
    List<SystemNotification> findTop50ByOrderByCreatedAtDesc();
    List<SystemNotification> findByStatusOrderByCreatedAtDesc(SystemNotification.Status status);
    List<SystemNotification> findByTypeAndReferenceId(SystemNotification.NotificationType type, Long referenceId);
    long countByStatus(SystemNotification.Status status);
    long countByTypeAndStatus(SystemNotification.NotificationType type, SystemNotification.Status status);

    /** The notifications this manager has not cleared, newest first. */
    @Query("SELECT n FROM SystemNotification n WHERE NOT EXISTS (SELECT 1 FROM NotificationUserState s WHERE s.notificationId = n.id "
            + "AND s.userId = :uid AND s.dismissedAt IS NOT NULL) ORDER BY n.createdAt DESC")
    List<SystemNotification> findVisibleFor(@Param("uid") Long userId, Pageable page);

    /** Unread for this manager: not resolved by the system, and neither read nor cleared by them. */
    @Query("SELECT COUNT(n) FROM SystemNotification n WHERE n.status = :unread AND NOT EXISTS (SELECT 1 FROM NotificationUserState s "
            + "WHERE s.notificationId = n.id AND s.userId = :uid AND (s.readAt IS NOT NULL OR s.dismissedAt IS NOT NULL))")
    long countUnreadFor(@Param("uid") Long userId, @Param("unread") SystemNotification.Status unread);

    @Query("SELECT COUNT(n) FROM SystemNotification n WHERE n.type = :type AND n.status = :unread AND NOT EXISTS (SELECT 1 FROM NotificationUserState s "
            + "WHERE s.notificationId = n.id AND s.userId = :uid AND (s.readAt IS NOT NULL OR s.dismissedAt IS NOT NULL))")
    long countUnreadOfTypeFor(@Param("uid") Long userId, @Param("type") SystemNotification.NotificationType type, @Param("unread") SystemNotification.Status unread);

    /** Housekeeping: old notifications go for everyone (their per-manager rows go with them). */
    @Modifying
    @Transactional
    @Query("DELETE FROM SystemNotification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") java.time.LocalDateTime cutoff);

    /** Bulk delete by status in one statement (no entity loading). Returns the number of rows removed. */
    @Modifying
    @Transactional
    @Query("DELETE FROM SystemNotification n WHERE n.status = :status")
    int deleteAllByStatus(@Param("status") SystemNotification.Status status);
}
