package com.neram.pos.repository;

import com.neram.pos.entity.TelegramDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TelegramDeliveryLogRepository extends JpaRepository<TelegramDeliveryLog, Long> {

    List<TelegramDeliveryLog> findTop100ByOrderBySentAtDesc();

    List<TelegramDeliveryLog> findByReportDateOrderBySentAtDesc(LocalDate reportDate);
}
