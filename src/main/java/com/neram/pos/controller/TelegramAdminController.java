package com.neram.pos.controller;

import com.neram.pos.dto.*;
import com.neram.pos.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/telegram")
@PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
@RequiredArgsConstructor
public class TelegramAdminController {

    private final TelegramNotificationService notificationService;

    @GetMapping("/settings")
    public ResponseEntity<TelegramSettingDTO> getSettings() {
        return ResponseEntity.ok(notificationService.getSettingsDTO());
    }

    @PutMapping("/settings")
    public ResponseEntity<TelegramSettingDTO> updateSettings(@RequestBody TelegramSettingDTO dto) {
        return ResponseEntity.ok(notificationService.updateSettings(dto));
    }

    @GetMapping("/recipients")
    public ResponseEntity<List<TelegramRecipientDTO>> getRecipients(Pageable pageable) {
        return PagedResponse.of(notificationService.getAllManagerRecipients(pageable));
    }

    @PutMapping("/recipients/{userId}/toggle")
    public ResponseEntity<TelegramRecipientDTO> toggleRecipient(
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> body) {
        Boolean selected = body.getOrDefault("selected", true);
        return ResponseEntity.ok(notificationService.toggleRecipient(userId, selected));
    }

    @PostMapping("/recipients/{userId}/link-code")
    public ResponseEntity<TelegramLinkCodeDTO> generateLinkCode(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.generateLinkCode(userId));
    }

    @PutMapping("/recipients/{userId}/chat-id")
    public ResponseEntity<TelegramRecipientDTO> updateChatId(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String chatId = body.get("chatId");
        return ResponseEntity.ok(notificationService.updateChatIdManually(userId, chatId));
    }

    @DeleteMapping("/recipients/{userId}/unlink")
    public ResponseEntity<TelegramRecipientDTO> unlinkRecipient(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.unlinkRecipient(userId));
    }

    @PostMapping("/recipients/{userId}/test")
    public ResponseEntity<Map<String, Object>> sendTestMessage(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.sendTestMessage(userId));
    }

    @GetMapping("/preview")
    public ResponseEntity<TelegramPreviewDTO> previewLowStockReport() {
        return ResponseEntity.ok(notificationService.previewLowStockReport());
    }

    @PostMapping("/send-now")
    public ResponseEntity<Map<String, Object>> sendReportNow() {
        return ResponseEntity.ok(notificationService.executeDailyDispatch(true));
    }

    @GetMapping("/history")
    public ResponseEntity<List<TelegramDeliveryLogDTO>> getDeliveryHistory(Pageable pageable) {
        return PagedResponse.of(notificationService.getDeliveryHistory(pageable));
    }
}
