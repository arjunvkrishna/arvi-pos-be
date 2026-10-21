package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "daily_send_time", nullable = false, length = 8)
    @Builder.Default
    private String dailySendTime = "09:00";

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = false;
        if (dailySendTime == null || dailySendTime.isBlank()) dailySendTime = "09:00";
        if (timezone == null || timezone.isBlank()) timezone = "Asia/Kolkata";
    }
}
