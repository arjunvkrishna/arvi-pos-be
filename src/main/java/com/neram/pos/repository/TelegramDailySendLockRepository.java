package com.neram.pos.repository;

import com.neram.pos.entity.TelegramDailySendLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Repository
public interface TelegramDailySendLockRepository extends JpaRepository<TelegramDailySendLock, LocalDate> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO telegram_daily_send_locks (report_date, status, started_at) " +
                   "VALUES (:reportDate, 'IN_PROGRESS', NOW()) " +
                   "ON CONFLICT (report_date) DO NOTHING", nativeQuery = true)
    int tryAcquireLock(@Param("reportDate") LocalDate reportDate);

    @Modifying
    @Transactional
    @Query(value = "UPDATE telegram_daily_send_locks SET status = 'COMPLETED', completed_at = NOW() WHERE report_date = :reportDate", nativeQuery = true)
    void markCompleted(@Param("reportDate") LocalDate reportDate);

    @Modifying
    @Transactional
    @Query(value = "UPDATE telegram_daily_send_locks SET status = 'FAILED' WHERE report_date = :reportDate", nativeQuery = true)
    void markFailed(@Param("reportDate") LocalDate reportDate);
}
