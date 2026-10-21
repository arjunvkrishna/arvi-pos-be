package com.neram.pos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramPollingService {

    private final TelegramBotClient botClient;
    private final TelegramNotificationService notificationService;

    private Long lastOffset = null;
    private boolean webhookActive = false;

    public void setWebhookActive(boolean active) {
        this.webhookActive = active;
    }

    @Scheduled(fixedDelay = 4000, initialDelay = 10000)
    public void pollTelegramUpdates() {
        if (!botClient.isConfigured() || webhookActive) {
            return;
        }

        try {
            List<TelegramBotClient.IncomingTelegramMessage> updates = botClient.getUpdates(lastOffset, 2);
            for (TelegramBotClient.IncomingTelegramMessage msg : updates) {
                lastOffset = msg.getUpdateId() + 1;
                notificationService.processIncomingTelegramUpdate(
                        msg.getText(),
                        msg.getChatId(),
                        msg.getUsername(),
                        msg.getFirstName()
                );
            }
        } catch (Exception e) {
            log.debug("Telegram polling cycle failed: {}", e.getMessage());
        }
    }
}
