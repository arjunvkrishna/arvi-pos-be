package com.neram.pos.service;

import com.neram.pos.dto.*;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final TelegramSettingRepository settingRepository;
    private final TelegramRecipientRepository recipientRepository;
    private final TelegramDeliveryLogRepository deliveryLogRepository;
    private final TelegramDailySendLockRepository sendLockRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final TelegramBotClient botClient;
    private final AuditLogService auditLogService;

    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ============================================================
    // 1. SETTINGS MANAGEMENT
    // ============================================================

    public TelegramSetting getOrCreateSettings() {
        return settingRepository.getSettings().orElseGet(() -> {
            TelegramSetting s = TelegramSetting.builder()
                    .id(1L)
                    .enabled(false)
                    .dailySendTime("09:00")
                    .timezone("Asia/Kolkata")
                    .build();
            return settingRepository.save(s);
        });
    }

    public TelegramSettingDTO getSettingsDTO() {
        TelegramSetting s = getOrCreateSettings();
        long verifiedCount = recipientRepository.countBySelectedTrueAndStatus(TelegramRecipient.Status.VERIFIED);
        long managerCount = userRepository.findByRoleInAndActiveTrue(List.of(User.Role.MANAGER)).size();

        return TelegramSettingDTO.builder()
                .enabled(s.getEnabled())
                .dailySendTime(s.getDailySendTime())
                .timezone(s.getTimezone())
                .lastRunDate(s.getLastRunDate())
                .botConfigured(botClient.isConfigured())
                .botUsername(botClient.getBotUsername())
                .verifiedRecipientsCount(verifiedCount)
                .totalManagersCount(managerCount)
                .build();
    }

    @Transactional
    public TelegramSettingDTO updateSettings(TelegramSettingDTO dto) {
        TelegramSetting s = getOrCreateSettings();
        String oldVal = String.format("enabled=%s, sendTime=%s, tz=%s", s.getEnabled(), s.getDailySendTime(), s.getTimezone());

        if (dto.getEnabled() != null && dto.getEnabled()) {
            long verifiedRecipients = recipientRepository.countBySelectedTrueAndStatus(TelegramRecipient.Status.VERIFIED);
            if (verifiedRecipients == 0) {
                throw new IllegalArgumentException("Cannot enable low-stock Telegram notifications: At least one active manager must have a verified Telegram account.");
            }
            s.setEnabled(true);
        } else if (dto.getEnabled() != null) {
            s.setEnabled(false);
        }

        if (dto.getDailySendTime() != null && !dto.getDailySendTime().isBlank()) {
            String time = dto.getDailySendTime().trim();
            if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                throw new IllegalArgumentException("Invalid daily send time format. Expected HH:mm in 24-hour format (e.g. 09:00).");
            }
            s.setDailySendTime(time);
        }

        if (dto.getTimezone() != null && !dto.getTimezone().isBlank()) {
            try {
                ZoneId.of(dto.getTimezone().trim());
                s.setTimezone(dto.getTimezone().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid timezone identifier: " + dto.getTimezone());
            }
        }

        s = settingRepository.save(s);
        String newVal = String.format("enabled=%s, sendTime=%s, tz=%s", s.getEnabled(), s.getDailySendTime(), s.getTimezone());
        auditLogService.log("UPDATE_SETTINGS", "TELEGRAM_SETTINGS", s.getId(), oldVal, newVal);

        return getSettingsDTO();
    }

    // ============================================================
    // 2. RECIPIENT MANAGEMENT
    // ============================================================

    public List<TelegramRecipientDTO> getAllManagerRecipients() {
        List<User> activeManagers = userRepository.findByRoleInAndActiveTrue(List.of(User.Role.MANAGER));

        return activeManagers.stream().map(manager -> {
            TelegramRecipient r = recipientRepository.findByUserId(manager.getId()).orElseGet(() -> {
                TelegramRecipient newRec = TelegramRecipient.builder()
                        .userId(manager.getId())
                        .status(TelegramRecipient.Status.NOT_CONFIGURED)
                        .selected(true)
                        .build();
                return recipientRepository.save(newRec);
            });

            String empName = "Manager " + manager.getUsername();
            String empPhone = null;
            if (manager.getEmployeeId() != null) {
                Optional<Employee> emp = employeeRepository.findById(manager.getEmployeeId());
                if (emp.isPresent()) {
                    empName = emp.get().getName();
                    empPhone = emp.get().getPhone();
                }
            }

            String maskedChatId = maskChatId(r.getTelegramChatId());

            return TelegramRecipientDTO.builder()
                    .id(r.getId())
                    .userId(manager.getId())
                    .username(manager.getUsername())
                    .employeeName(empName)
                    .employeePhone(empPhone)
                    .telegramChatId(r.getTelegramChatId())
                    .telegramChatIdMasked(maskedChatId)
                    .telegramUsername(r.getTelegramUsername())
                    .telegramFirstName(r.getTelegramFirstName())
                    .status(r.getStatus().name())
                    .selected(r.getSelected())
                    .linkCodeExpiresAt(r.getLinkCodeExpiresAt())
                    .verifiedAt(r.getVerifiedAt())
                    .lastTestSentAt(r.getLastTestSentAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public TelegramRecipientDTO toggleRecipient(Long userId, Boolean selected) {
        User manager = validateAndGetManager(userId);
        TelegramRecipient r = getOrCreateRecipient(manager.getId());

        boolean oldSelected = r.getSelected();
        r.setSelected(Boolean.TRUE.equals(selected));
        r = recipientRepository.save(r);

        auditLogService.log("TOGGLE_RECIPIENT", "TELEGRAM_RECIPIENT", r.getId(),
                "selected=" + oldSelected, "selected=" + r.getSelected());

        return toDTO(r, manager);
    }

    @Transactional
    public TelegramLinkCodeDTO generateLinkCode(Long userId) {
        User manager = validateAndGetManager(userId);
        TelegramRecipient r = getOrCreateRecipient(manager.getId());

        String code = generateSecureCode(6);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        r.setLinkCode(code);
        r.setLinkCodeExpiresAt(expiresAt);
        r.setStatus(TelegramRecipient.Status.PENDING_VERIFICATION);
        recipientRepository.save(r);

        auditLogService.log("GENERATE_LINK_CODE", "TELEGRAM_RECIPIENT", r.getId(),
                "status=" + r.getStatus(), "status=PENDING_VERIFICATION");

        String botUsername = botClient.getBotUsername();
        String botTag = (botUsername != null) ? "@" + botUsername : "the configured bot";
        String deepLink = (botUsername != null) ? "https://t.me/" + botUsername + "?start=" + code : null;
        String instructions = String.format("Instruct manager %s to open Telegram, message %s, and send: /link %s (or click the direct link). Code expires in 15 minutes.",
                manager.getUsername(), botTag, code);

        String managerName = getManagerName(manager);

        return TelegramLinkCodeDTO.builder()
                .userId(manager.getId())
                .username(manager.getUsername())
                .managerName(managerName)
                .linkCode(code)
                .expiresAt(expiresAt)
                .botUsername(botUsername)
                .instructions(instructions)
                .deepLink(deepLink)
                .build();
    }

    @Transactional
    public boolean processIncomingTelegramUpdate(String text, long chatId, String username, String firstName) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        String code = null;

        if (trimmed.startsWith("/link")) {
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                code = parts[1].trim();
            }
        } else if (trimmed.startsWith("/start")) {
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                code = parts[1].trim();
            }
        }

        if (code == null || code.isBlank()) {
            return false;
        }

        Optional<TelegramRecipient> opt = recipientRepository.findByLinkCode(code.toUpperCase());
        if (opt.isEmpty()) {
            try {
                botClient.sendMessage(String.valueOf(chatId),
                        "⚠️ <b>Invalid Linking Code</b>\n\nThe code you entered is invalid or has already been used. Please request a new code from your NERAM POS administrator.");
            } catch (Exception ignored) {}
            return false;
        }

        TelegramRecipient r = opt.get();
        if (r.getLinkCodeExpiresAt() == null || r.getLinkCodeExpiresAt().isBefore(LocalDateTime.now())) {
            try {
                botClient.sendMessage(String.valueOf(chatId),
                        "⚠️ <b>Expired Linking Code</b>\n\nThis linking code has expired. Please ask your administrator to generate a new linking code.");
            } catch (Exception ignored) {}
            return false;
        }

        // Successfully link the account!
        r.setTelegramChatId(String.valueOf(chatId));
        r.setTelegramUsername(username);
        r.setTelegramFirstName(firstName);
        r.setStatus(TelegramRecipient.Status.VERIFIED);
        r.setVerifiedAt(LocalDateTime.now());
        r.setLinkCode(null);
        r.setLinkCodeExpiresAt(null);
        recipientRepository.save(r);

        auditLogService.log("LINK_VERIFIED", "TELEGRAM_RECIPIENT", r.getId(),
                "status=PENDING_VERIFICATION", "status=VERIFIED, chatId=" + maskChatId(String.valueOf(chatId)));

        try {
            botClient.sendMessage(String.valueOf(chatId),
                    "✅ <b>Account Successfully Linked!</b>\n\nWelcome to NERAM POS! Your Telegram account is now verified. You will receive daily low-stock inventory summaries at the scheduled send time.");
        } catch (Exception e) {
            log.warn("Failed to send verification confirmation to chat ID {}: {}", chatId, e.getMessage());
        }

        return true;
    }

    @Transactional
    public TelegramRecipientDTO updateChatIdManually(Long userId, String chatId) {
        User manager = validateAndGetManager(userId);
        TelegramRecipient r = getOrCreateRecipient(manager.getId());

        if (chatId == null || chatId.trim().isBlank()) {
            throw new IllegalArgumentException("Chat ID cannot be empty");
        }

        String cleanedChatId = chatId.trim();
        String oldChatId = maskChatId(r.getTelegramChatId());
        r.setTelegramChatId(cleanedChatId);
        r.setStatus(TelegramRecipient.Status.VERIFIED);
        r.setVerifiedAt(LocalDateTime.now());
        r.setLinkCode(null);
        r.setLinkCodeExpiresAt(null);
        r = recipientRepository.save(r);

        auditLogService.log("MANUAL_CHAT_ID_UPDATE", "TELEGRAM_RECIPIENT", r.getId(),
                "chatId=" + oldChatId, "chatId=" + maskChatId(cleanedChatId));

        return toDTO(r, manager);
    }

    @Transactional
    public TelegramRecipientDTO unlinkRecipient(Long userId) {
        User manager = validateAndGetManager(userId);
        TelegramRecipient r = getOrCreateRecipient(manager.getId());

        String oldChatId = maskChatId(r.getTelegramChatId());
        r.setTelegramChatId(null);
        r.setTelegramUsername(null);
        r.setTelegramFirstName(null);
        r.setStatus(TelegramRecipient.Status.NOT_CONFIGURED);
        r.setVerifiedAt(null);
        r.setLinkCode(null);
        r.setLinkCodeExpiresAt(null);
        r = recipientRepository.save(r);

        auditLogService.log("UNLINK_RECIPIENT", "TELEGRAM_RECIPIENT", r.getId(),
                "chatId=" + oldChatId, "status=NOT_CONFIGURED");

        return toDTO(r, manager);
    }

    // ============================================================
    // 3. TEST MESSAGE
    // ============================================================

    public Map<String, Object> sendTestMessage(Long userId) {
        User manager = validateAndGetManager(userId);
        TelegramRecipient r = recipientRepository.findByUserId(manager.getId())
                .orElseThrow(() -> new IllegalArgumentException("Recipient record not found for manager: " + manager.getUsername()));

        if (r.getStatus() != TelegramRecipient.Status.VERIFIED || r.getTelegramChatId() == null || r.getTelegramChatId().isBlank()) {
            throw new IllegalStateException("Cannot send test message: Manager Telegram account is not verified.");
        }

        TelegramSetting s = getOrCreateSettings();
        ZoneId zoneId = ZoneId.of(s.getTimezone() != null ? s.getTimezone() : "Asia/Kolkata");
        ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);
        String timestampStr = nowZoned.format(DateTimeFormatter.ofPattern("dd MMM yyyy | hh:mm a"));
        String managerName = getManagerName(manager);

        String testText = String.format(
                "🔔 <b>NERAM POS — Test Notification</b>\n" +
                "📅 <b>Date:</b> %s (%s)\n" +
                "────────────────────────────\n" +
                "Hello <b>%s</b>,\n\n" +
                "This is a test notification confirming that your Telegram connection is active and working properly.\n" +
                "You will receive daily low-stock alerts at <b>%s</b> when items drop below minimum inventory thresholds.\n" +
                "────────────────────────────\n" +
                "<i>NERAM Cafe &amp; Restaurant POS System</i>",
                timestampStr, s.getTimezone(), escapeHtml(managerName), s.getDailySendTime()
        );

        String errorDesc = null;
        boolean success = false;
        try {
            botClient.sendMessage(r.getTelegramChatId(), testText);
            success = true;
            r.setLastTestSentAt(LocalDateTime.now());
            recipientRepository.save(r);
        } catch (Exception e) {
            errorDesc = (e.getMessage() != null && e.getMessage().length() > 480)
                    ? e.getMessage().substring(0, 480) : e.getMessage();
            log.error("Failed to send test message to manager {}: {}", manager.getUsername(), errorDesc);
        }

        // Record in delivery logs
        TelegramDeliveryLog logEntry = TelegramDeliveryLog.builder()
                .reportDate(nowZoned.toLocalDate())
                .recipientId(r.getId())
                .userId(manager.getId())
                .managerName(managerName)
                .chatIdMasked(maskChatId(r.getTelegramChatId()))
                .status(success ? "SUCCESS" : "FAILED")
                .errorDescription(errorDesc)
                .itemsCount(0)
                .deliveryType("TEST_MESSAGE")
                .sentAt(LocalDateTime.now())
                .build();
        deliveryLogRepository.save(logEntry);

        auditLogService.log("SEND_TEST_MESSAGE", "TELEGRAM_RECIPIENT", r.getId(),
                "status=" + r.getStatus(), "result=" + (success ? "SUCCESS" : "FAILED"));

        if (!success) {
            throw new RuntimeException("Test message delivery failed: " + errorDesc);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Test message delivered successfully to " + managerName + " (" + maskChatId(r.getTelegramChatId()) + ")");
        return resp;
    }

    // ============================================================
    // 4. REPORT PREVIEW & DAILY SEND
    // ============================================================

    public TelegramPreviewDTO previewLowStockReport() {
        TelegramSetting s = getOrCreateSettings();
        ZoneId zoneId = ZoneId.of(s.getTimezone() != null ? s.getTimezone() : "Asia/Kolkata");
        ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);

        List<InventoryItem> lowStockItems = inventoryItemRepository.findLowStockItems();
        String reportText = buildLowStockReportText(lowStockItems, nowZoned, s);

        List<TelegramPreviewDTO.LowStockItemDetail> itemDetails = lowStockItems.stream().map(i ->
                TelegramPreviewDTO.LowStockItemDetail.builder()
                        .name(i.getName())
                        .currentQuantity(i.getQuantity())
                        .minQuantity(i.getMinQuantity())
                        .unit(i.getUnit())
                        .supplier((i.getSupplier() != null && !i.getSupplier().isBlank()) ? i.getSupplier() : "N/A")
                        .build()
        ).collect(Collectors.toList());

        return TelegramPreviewDTO.builder()
                .itemCount(lowStockItems.size())
                .reportDate(nowZoned.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                .formattedReport(reportText)
                .items(itemDetails)
                .build();
    }

    @Transactional
    public Map<String, Object> executeDailyDispatch(boolean forceManual) {
        TelegramSetting s = getOrCreateSettings();
        ZoneId zoneId = ZoneId.of(s.getTimezone() != null ? s.getTimezone() : "Asia/Kolkata");
        LocalDate today = ZonedDateTime.now(zoneId).toLocalDate();

        if (!s.getEnabled() && !forceManual) {
            return Map.of("executed", false, "reason", "Feature is disabled in settings");
        }

        // Try to acquire distributed lock for today
        int acquired = sendLockRepository.tryAcquireLock(today);
        if (acquired == 0 && !forceManual) {
            log.info("Daily low-stock report for date {} already processed or locked by another worker.", today);
            return Map.of("executed", false, "reason", "Already sent or lock active for today");
        }

        List<InventoryItem> lowStockItems = inventoryItemRepository.findLowStockItems();
        String reportText = buildLowStockReportText(lowStockItems, ZonedDateTime.now(zoneId), s);

        List<TelegramRecipient> verifiedRecipients = recipientRepository
                .findBySelectedTrueAndStatus(TelegramRecipient.Status.VERIFIED);

        int sentCount = 0;
        int failedCount = 0;

        for (TelegramRecipient r : verifiedRecipients) {
            // Verify manager is still active
            Optional<User> userOpt = userRepository.findById(r.getUserId());
            if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getActive())) {
                continue;
            }

            User manager = userOpt.get();
            String managerName = getManagerName(manager);
            boolean success = false;
            String errorDesc = null;

            try {
                botClient.sendMessage(r.getTelegramChatId(), reportText);
                success = true;
                sentCount++;
            } catch (Exception e) {
                failedCount++;
                errorDesc = (e.getMessage() != null && e.getMessage().length() > 480)
                        ? e.getMessage().substring(0, 480) : e.getMessage();
                log.error("Failed to send daily low-stock report to manager {}: {}", manager.getUsername(), errorDesc);
            }

            TelegramDeliveryLog logEntry = TelegramDeliveryLog.builder()
                    .reportDate(today)
                    .recipientId(r.getId())
                    .userId(manager.getId())
                    .managerName(managerName)
                    .chatIdMasked(maskChatId(r.getTelegramChatId()))
                    .status(success ? "SUCCESS" : "FAILED")
                    .errorDescription(errorDesc)
                    .itemsCount(lowStockItems.size())
                    .deliveryType("DAILY_REPORT")
                    .sentAt(LocalDateTime.now())
                    .build();
            deliveryLogRepository.save(logEntry);
        }

        sendLockRepository.markCompleted(today);
        s.setLastRunDate(today);
        settingRepository.save(s);

        auditLogService.log("DAILY_DISPATCH", "TELEGRAM_REPORT", 1L,
                "date=" + today, String.format("sent=%d, failed=%d, lowStockItems=%d", sentCount, failedCount, lowStockItems.size()));

        Map<String, Object> result = new HashMap<>();
        result.put("executed", true);
        result.put("reportDate", today.toString());
        result.put("sentCount", sentCount);
        result.put("failedCount", failedCount);
        result.put("totalRecipients", verifiedRecipients.size());
        result.put("lowStockItemsCount", lowStockItems.size());
        return result;
    }

    public List<TelegramDeliveryLogDTO> getDeliveryHistory() {
        return deliveryLogRepository.findTop100ByOrderBySentAtDesc().stream().map(l ->
                TelegramDeliveryLogDTO.builder()
                        .id(l.getId())
                        .reportDate(l.getReportDate())
                        .managerName(l.getManagerName())
                        .chatIdMasked(l.getChatIdMasked())
                        .status(l.getStatus())
                        .errorDescription(l.getErrorDescription())
                        .itemsCount(l.getItemsCount())
                        .deliveryType(l.getDeliveryType())
                        .sentAt(l.getSentAt())
                        .build()
        ).collect(Collectors.toList());
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private String buildLowStockReportText(List<InventoryItem> items, ZonedDateTime now, TelegramSetting s) {
        String timestampStr = now.format(DateTimeFormatter.ofPattern("dd MMM yyyy | hh:mm a"));
        StringBuilder sb = new StringBuilder();

        sb.append("<b>📦 NERAM POS — Daily Low-Stock Report</b>\n");
        sb.append("📅 <b>Date:</b> ").append(timestampStr).append(" (").append(s.getTimezone()).append(")\n");
        sb.append("────────────────────────────\n");

        if (items.isEmpty()) {
            sb.append("✅ <b>All Good!</b> All active inventory items are adequately stocked above minimum levels.\n");
            sb.append("────────────────────────────\n");
            sb.append("<i>NERAM Cafe &amp; Restaurant POS System</i>");
            return sb.toString();
        }

        sb.append("⚠️ <b>").append(items.size()).append(" item(s) require attention:</b>\n\n");

        int index = 1;
        for (InventoryItem item : items) {
            String supplier = (item.getSupplier() != null && !item.getSupplier().isBlank())
                    ? escapeHtml(item.getSupplier()) : "N/A";

            sb.append(index++).append(". <b>").append(escapeHtml(item.getName())).append("</b>\n");
            sb.append("   • Stock: <code>").append(item.getQuantity()).append(" ").append(item.getUnit()).append("</code>");
            sb.append(" (Min: ").append(item.getMinQuantity()).append(" ").append(item.getUnit()).append(")\n");
            sb.append("   • Supplier: ").append(supplier).append("\n\n");
        }

        sb.append("────────────────────────────\n");
        sb.append("<i>Please review and replenish low-stock items with suppliers.</i>");
        return sb.toString();
    }

    private User validateAndGetManager(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        if (u.getRole() != User.Role.MANAGER && u.getRole() != User.Role.ADMIN) {
            throw new IllegalArgumentException("User is not a manager: " + u.getUsername());
        }
        if (!Boolean.TRUE.equals(u.getActive())) {
            throw new IllegalArgumentException("User account is inactive: " + u.getUsername());
        }
        return u;
    }

    private TelegramRecipient getOrCreateRecipient(Long userId) {
        return recipientRepository.findByUserId(userId).orElseGet(() -> {
            TelegramRecipient r = TelegramRecipient.builder()
                    .userId(userId)
                    .status(TelegramRecipient.Status.NOT_CONFIGURED)
                    .selected(true)
                    .build();
            return recipientRepository.save(r);
        });
    }

    private TelegramRecipientDTO toDTO(TelegramRecipient r, User manager) {
        String empName = getManagerName(manager);
        return TelegramRecipientDTO.builder()
                .id(r.getId())
                .userId(manager.getId())
                .username(manager.getUsername())
                .employeeName(empName)
                .telegramChatId(r.getTelegramChatId())
                .telegramChatIdMasked(maskChatId(r.getTelegramChatId()))
                .telegramUsername(r.getTelegramUsername())
                .telegramFirstName(r.getTelegramFirstName())
                .status(r.getStatus().name())
                .selected(r.getSelected())
                .linkCodeExpiresAt(r.getLinkCodeExpiresAt())
                .verifiedAt(r.getVerifiedAt())
                .lastTestSentAt(r.getLastTestSentAt())
                .build();
    }

    private String getManagerName(User manager) {
        if (manager.getEmployeeId() != null) {
            Optional<Employee> emp = employeeRepository.findById(manager.getEmployeeId());
            if (emp.isPresent() && emp.get().getName() != null && !emp.get().getName().isBlank()) {
                return emp.get().getName();
            }
        }
        return manager.getUsername();
    }

    private String maskChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return null;
        }
        String clean = chatId.trim();
        if (clean.length() <= 4) {
            return "***" + clean;
        }
        return "***" + clean.substring(clean.length() - 4);
    }

    private String generateSecureCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
