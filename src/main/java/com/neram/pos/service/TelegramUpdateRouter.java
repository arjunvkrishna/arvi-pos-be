package com.neram.pos.service;

import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * One Telegram bot serves all cafes. A link code looks like {@code <cafe-code>_<RANDOM>}; this picks the cafe from
 * the prefix and hands the plain code to that cafe's own database. Codes without a known cafe are rejected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateRouter {

    /** Cafe codes never contain an underscore, so the first one separates cafe from code. */
    public static final char CODE_SEPARATOR = '_';

    private final TenantRegistry tenantRegistry;
    private final TelegramNotificationService notificationService;
    private final TelegramBotClient botClient;

    public boolean route(String text, long chatId, String username, String firstName) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2 || !(parts[0].startsWith("/link") || parts[0].startsWith("/start"))) {
            return false;
        }
        String shown = parts[1].trim();
        int sep = shown.indexOf(CODE_SEPARATOR);
        Optional<TenantRecord> tenant = sep > 0
                ? tenantRegistry.findByCode(shown.substring(0, sep).toLowerCase(Locale.ROOT)).filter(TenantRecord::isActive)
                : Optional.empty();
        if (tenant.isEmpty() || sep == shown.length() - 1) {
            try {
                botClient.sendMessage(String.valueOf(chatId),
                        "⚠️ <b>Invalid Linking Code</b>\n\nThe code you entered is invalid or has already been used. Please request a new code from your POS administrator.");
            } catch (Exception ignored) {
                // best-effort reply
            }
            return false;
        }
        String plainCode = shown.substring(sep + 1);
        return TenantContext.callAs(tenant.get().info(),
                () -> notificationService.processIncomingTelegramUpdate("/link " + plainCode, chatId, username, firstName));
    }
}
