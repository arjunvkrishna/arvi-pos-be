package com.neram.pos.repository;

import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrintJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    Optional<PrintJob> findByJobId(String jobId);

    Optional<PrintJob> findByIdempotencyKey(String idempotencyKey);

    List<PrintJob> findByBillIdAndJobTypeAndStatusIn(Long billId, String jobType, List<PrintJobStatus> statuses);

    @Query("SELECT j FROM PrintJob j WHERE j.status = :status AND j.leaseExpiresAt < :now")
    List<PrintJob> findExpiredClaimedJobs(
            @Param("status") PrintJobStatus status,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("UPDATE PrintJob j SET j.payload = null WHERE j.status IN ('COMPLETED', 'FAILED') AND j.completedAt < :threshold AND j.payload IS NOT NULL")
    int purgePayloadsOlderThan(@Param("threshold") LocalDateTime threshold);

    long countByStatus(PrintJobStatus status);

    List<PrintJob> findTop50ByOrderByCreatedAtDesc();
}
