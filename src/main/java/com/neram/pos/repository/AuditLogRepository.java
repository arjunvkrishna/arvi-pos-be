package com.neram.pos.repository;

import com.neram.pos.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findAllByOrderByPerformedAtDesc();
    List<AuditLog> findByEntityTypeOrderByPerformedAtDesc(String entityType);
    List<AuditLog> findTop200ByOrderByPerformedAtDesc();
}
