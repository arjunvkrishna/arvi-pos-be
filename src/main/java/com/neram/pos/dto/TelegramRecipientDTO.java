package com.neram.pos.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramRecipientDTO {
    private Long id;
    private Long userId;
    private String username;
    private String employeeName;
    private String employeePhone;
    private String telegramChatId;
    private String telegramChatIdMasked;
    private String telegramUsername;
    private String telegramFirstName;
    private String status;
    private Boolean selected;
    private LocalDateTime linkCodeExpiresAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime lastTestSentAt;
}
