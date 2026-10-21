package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_daily_send_locks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramDailySendLock {

    @Id
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "IN_PROGRESS";

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
