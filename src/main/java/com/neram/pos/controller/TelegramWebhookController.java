package com.neram.pos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neram.pos.service.TelegramBotClient;
import com.neram.pos.service.TelegramUpdateRouter;
import com.neram.pos.service.TelegramPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotClient botClient;
    private final TelegramUpdateRouter updateRouter;
    private final TelegramPollingService pollingService;

    @PostMapping("/webhook")
    public ResponseEntity<?> handleTelegramWebhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody JsonNode updateNode) {

        if (!botClient.verifyWebhookSecret(secretToken)) {
            log.warn("Unauthorized Telegram webhook attempt with invalid secret token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "Invalid secret token"));
        }

        pollingService.setWebhookActive(true);

        try {
            TelegramBotClient.IncomingTelegramMessage msg = botClient.parseUpdateNode(updateNode);
            if (msg != null) {
                updateRouter.route(
                        msg.getText(),
                        msg.getChatId(),
                        msg.getUsername(),
                        msg.getFirstName()
                );
            }
        } catch (Exception e) {
            log.error("Error processing Telegram webhook update: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }
}
