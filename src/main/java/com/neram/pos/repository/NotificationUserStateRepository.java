package com.neram.pos.repository;

import com.neram.pos.entity.NotificationUserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Per-manager read / cleared state. Every write is one upsert, so two devices of the same manager cannot collide. */
@Repository
public interface NotificationUserStateRepository extends JpaRepository<NotificationUserState, Long> {

    List<NotificationUserState> findByUserIdAndNotificationIdIn(Long userId, Collection<Long> notificationIds);

    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO notification_user_state (notification_id, user_id, read_at) VALUES (:nid, :uid, now()) "
            + "ON CONFLICT (notification_id, user_id) DO UPDATE SET read_at = COALESCE(notification_user_state.read_at, now())")
    int markRead(@Param("nid") Long notificationId, @Param("uid") Long userId);

    /** Clearing also counts as read, so a cleared notification can never come back as unread. */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO notification_user_state (notification_id, user_id, read_at, dismissed_at) VALUES (:nid, :uid, now(), now()) "
            + "ON CONFLICT (notification_id, user_id) DO UPDATE SET read_at = COALESCE(notification_user_state.read_at, now()), "
            + "dismissed_at = COALESCE(notification_user_state.dismissed_at, now())")
    int dismiss(@Param("nid") Long notificationId, @Param("uid") Long userId);

    /** Marks every notification this manager can still see and has not read as read, in one statement. */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO notification_user_state (notification_id, user_id, read_at) "
            + "SELECT n.id, :uid, now() FROM system_notifications n WHERE n.status = 'UNREAD' AND NOT EXISTS ("
            + "SELECT 1 FROM notification_user_state s WHERE s.notification_id = n.id AND s.user_id = :uid "
            + "AND (s.read_at IS NOT NULL OR s.dismissed_at IS NOT NULL)) "
            + "ON CONFLICT (notification_id, user_id) DO UPDATE SET read_at = COALESCE(notification_user_state.read_at, now())")
    int markAllRead(@Param("uid") Long userId);

    /** Clears from this manager's list everything they (or the system) have already read. Unread ones are never touched. */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO notification_user_state (notification_id, user_id, read_at, dismissed_at) "
            + "SELECT n.id, :uid, now(), now() FROM system_notifications n WHERE (n.status = 'READ' OR EXISTS ("
            + "SELECT 1 FROM notification_user_state r WHERE r.notification_id = n.id AND r.user_id = :uid AND r.read_at IS NOT NULL)) "
            + "AND NOT EXISTS (SELECT 1 FROM notification_user_state d WHERE d.notification_id = n.id AND d.user_id = :uid AND d.dismissed_at IS NOT NULL) "
            + "ON CONFLICT (notification_id, user_id) DO UPDATE SET dismissed_at = now()")
    int dismissRead(@Param("uid") Long userId);
}
