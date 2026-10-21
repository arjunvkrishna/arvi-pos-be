package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_delivery_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "recipient_id")
    private Long recipientId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "manager_name", nullable = false, length = 100)
    private String managerName;

    @Column(name = "chat_id_masked", length = 50)
    private String chatIdMasked;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_description", length = 500)
    private String errorDescription;

    @Column(name = "items_count", nullable = false)
    @Builder.Default
    private Integer itemsCount = 0;

    @Column(name = "delivery_type", nullable = false, length = 20)
    @Builder.Default
    private String deliveryType = "DAILY_REPORT";

    @Column(name = "sent_at", nullable = false)
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}
