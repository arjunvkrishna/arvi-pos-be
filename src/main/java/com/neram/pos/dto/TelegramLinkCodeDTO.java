package com.neram.pos.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramLinkCodeDTO {
    private Long userId;
    private String username;
    private String managerName;
    private String linkCode;
    private LocalDateTime expiresAt;
    private String botUsername;
    private String instructions;
    private String deepLink;
}
