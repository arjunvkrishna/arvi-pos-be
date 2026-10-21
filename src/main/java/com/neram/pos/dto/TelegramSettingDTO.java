package com.neram.pos.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramSettingDTO {
    private Boolean enabled;
    private String dailySendTime;
    private String timezone;
    private LocalDate lastRunDate;
    private boolean botConfigured;
    private String botUsername;
    private long verifiedRecipientsCount;
    private long totalManagersCount;
}
