package com.neram.pos.repository;

import com.neram.pos.entity.ReportExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportExportJobRepository extends JpaRepository<ReportExportJob, UUID> {

    @Query(value = "SELECT * FROM report_export_jobs WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<ReportExportJob> findNextPendingJob();

    List<ReportExportJob> findByStatusAndStartedAtBefore(String status, Instant cutoff);

    List<ReportExportJob> findAllByOrderByCreatedAtDesc();

    @Query("SELECT j FROM ReportExportJob j WHERE j.createdAt < :cutoff")
    List<ReportExportJob> findOlderThan(@Param("cutoff") Instant cutoff);
}
