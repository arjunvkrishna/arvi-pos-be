package com.neram.pos.repository;

import com.neram.pos.entity.SystemNotification;
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
}
