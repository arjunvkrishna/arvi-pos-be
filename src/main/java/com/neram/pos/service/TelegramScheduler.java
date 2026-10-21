package com.neram.pos.service;

import com.neram.pos.entity.TelegramSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramScheduler {

    private final TelegramNotificationService notificationService;

    @Scheduled(cron = "0 * * * * *")
    public void scheduleDailyLowStockReportCheck() {
        try {
            TelegramSetting s = notificationService.getOrCreateSettings();
            if (!Boolean.TRUE.equals(s.getEnabled())) {
                return;
            }

            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(s.getTimezone() != null ? s.getTimezone() : "Asia/Kolkata");
            } catch (Exception e) {
                zoneId = ZoneId.of("Asia/Kolkata");
            }

            ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);
            String currentHHmm = nowZoned.format(DateTimeFormatter.ofPattern("HH:mm"));

            if (currentHHmm.equals(s.getDailySendTime())) {
                log.info("Telegram low-stock daily report scheduled trigger firing at {} ({})", currentHHmm, zoneId);
                notificationService.executeDailyDispatch(false);
            }
        } catch (Exception e) {
            log.error("Error in Telegram daily low-stock scheduled task: {}", e.getMessage());
        }
    }
}
