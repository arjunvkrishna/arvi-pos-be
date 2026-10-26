package com.neram.pos.repository;

import com.neram.pos.entity.SystemNotification;
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

    /** Bulk delete by status in one statement (no entity loading). Returns the number of rows removed. */
    @Modifying
    @Transactional
    @Query("DELETE FROM SystemNotification n WHERE n.status = :status")
    int deleteAllByStatus(@Param("status") SystemNotification.Status status);
}
