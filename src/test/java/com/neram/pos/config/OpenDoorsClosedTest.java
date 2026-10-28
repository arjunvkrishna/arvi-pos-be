package com.neram.pos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neram.pos.controller.CashierShiftController;
import com.neram.pos.controller.PrintBridgeController;
import com.neram.pos.dto.CashierShiftDTO;
import com.neram.pos.service.CashierShiftService;
import com.neram.pos.service.PrintBridgeService;
import com.neram.pos.service.PrinterScanService;
import com.neram.pos.service.TelegramBotClient;
import com.neram.pos.service.ThermalPrinterService;
import com.neram.pos.tenancy.BridgeTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The doors that open without a login (print bridge, Telegram webhook) must stay shut until their secret is configured, and the admin-only switch must stay admin-only. */
class OpenDoorsClosedTest {

    private static PrintBridgeController bridge(boolean tokenAccepted) {
        BridgeTokenVerifier verifier = mock(BridgeTokenVerifier.class);
        when(verifier.verify(any())).thenReturn(tokenAccepted);
        return new PrintBridgeController(mock(PrintBridgeService.class), mock(ThermalPrinterService.class),
                mock(PrinterScanService.class), verifier);
    }

    @Test
    @DisplayName("print bridge: a token the cafe's verifier refuses never reaches the config, in any of the three places")
    void bridgeRefusesWhatTheVerifierRefuses() {
        PrintBridgeController c = bridge(false);
        assertEquals(401, c.getBridgePrinterConfig(null, null, null).getStatusCode().value());
        assertEquals(401, c.getBridgePrinterConfig("anything", null, null).getStatusCode().value());
        assertEquals(401, c.getBridgePrinterConfig(null, "anything", null).getStatusCode().value());
        assertEquals(401, c.getBridgePrinterConfig(null, null, "Bearer anything").getStatusCode().value());
    }

    @Test
    @DisplayName("print bridge: an accepted token works whichever header the bridge sends it in")
    void bridgeAcceptsWhatTheVerifierAccepts() {
        PrintBridgeController c = bridge(true);
        assertNotEquals(401, c.getBridgePrinterConfig("s3cret-token", null, null).getStatusCode().value());
        assertNotEquals(401, c.getBridgePrinterConfig(null, "s3cret-token", null).getStatusCode().value());
        assertNotEquals(401, c.getBridgePrinterConfig(null, null, "Bearer s3cret-token").getStatusCode().value());
    }

    @Test
    @DisplayName("print bridge: with no cafe selected, or a cafe with no token, the real verifier refuses everyone")
    void bridgeVerifierFailsClosed() {
        com.neram.pos.tenancy.TenantRegistry registry = mock(com.neram.pos.tenancy.TenantRegistry.class);
        BridgeTokenVerifier real = new BridgeTokenVerifier(registry, mock(com.neram.pos.tenancy.TenantCrypto.class));
        com.neram.pos.tenancy.TenantContext.clear();
        assertFalse(real.verify("neram-printer-bridge-secret-2024"), "no cafe selected");

        com.neram.pos.tenancy.TenantContext.set(new com.neram.pos.tenancy.TenantInfo(9, "nobridge"));
        try {
            when(registry.findById(9L)).thenReturn(java.util.Optional.of(new com.neram.pos.tenancy.TenantRecord(9, "nobridge", "N",
                    "ACTIVE", "PRO", 1, null, null, null, "h", 5432, "db", "u", "x", null, null, null, null, null)));
            assertFalse(real.verify("neram-printer-bridge-secret-2024"), "the old default that used to be in the source must not work");
            assertFalse(real.verify(""));
            assertFalse(real.verify(null));
        } finally {
            com.neram.pos.tenancy.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("telegram webhook: with no secret configured every call is refused; with one only the matching header passes")
    void telegramWebhookSecret() {
        TelegramBotClient none = new TelegramBotClient("", "", new ObjectMapper());
        assertFalse(none.verifyWebhookSecret(null));
        assertFalse(none.verifyWebhookSecret(""));
        assertFalse(none.verifyWebhookSecret("anything"));

        TelegramBotClient set = new TelegramBotClient("", "hook-secret", new ObjectMapper());
        assertTrue(set.verifyWebhookSecret("hook-secret"));
        assertFalse(set.verifyWebhookSecret("hook-secreT"));
        assertFalse(set.verifyWebhookSecret(null));
        assertFalse(set.verifyWebhookSecret(""));
    }

    private static Authentication who(String... authorities) {
        return new UsernamePasswordAuthenticationToken("u", "x", AuthorityUtils.createAuthorityList(authorities));
    }

    @Test
    @DisplayName("shift module: a manager can tune the thresholds but cannot switch the module on or off; an admin can")
    void shiftModuleSwitchIsAdminOnly() {
        CashierShiftService service = mock(CashierShiftService.class);
        CashierShiftController controller = new CashierShiftController(service);

        CashierShiftDTO.ShiftSettingsDTO off = new CashierShiftDTO.ShiftSettingsDTO();
        off.setEnabled(false);
        assertThrows(AccessDeniedException.class, () -> controller.updateSettings(off, who("ROLE_MANAGER")));
        assertThrows(AccessDeniedException.class, () -> controller.updateSettings(off, null));
        verify(service, never()).updateSettings(any(CashierShiftDTO.ShiftSettingsDTO.class));

        CashierShiftDTO.ShiftSettingsDTO thresholds = new CashierShiftDTO.ShiftSettingsDTO();
        thresholds.setVarianceThreshold(new java.math.BigDecimal("50"));
        controller.updateSettings(thresholds, who("ROLE_MANAGER"));
        verify(service).updateSettings(thresholds);

        controller.updateSettings(off, who("ROLE_ADMIN"));
        verify(service).updateSettings(off);
    }
}
