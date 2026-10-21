package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_recipients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "telegram_chat_id", length = 100)
    private String telegramChatId;

    @Column(name = "telegram_username", length = 100)
    private String telegramUsername;

    @Column(name = "telegram_first_name", length = 100)
    private String telegramFirstName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.NOT_CONFIGURED;

    @Column(nullable = false)
    @Builder.Default
    private Boolean selected = true;

    @Column(name = "link_code", length = 64)
    private String linkCode;

    @Column(name = "link_code_expires_at")
    private LocalDateTime linkCodeExpiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "last_test_sent_at")
    private LocalDateTime lastTestSentAt;

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
        if (status == null) status = Status.NOT_CONFIGURED;
        if (selected == null) selected = true;
    }

    public enum Status {
        NOT_CONFIGURED,
        PENDING_VERIFICATION,
        VERIFIED
    }
}
