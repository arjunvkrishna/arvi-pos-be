package com.neram.pos.service;

import com.neram.pos.config.DatabaseSchemaEnsurer;
import com.neram.pos.dto.PrinterSettingDTO;
import com.neram.pos.entity.PrinterSetting;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The cafe Wi-Fi for direct printing is the admin's call; managers can see it and keep saving the rest of the printer settings. */
class DirectPrintAccessTest {

    private ThermalPrinterService service;
    private PrinterSettingRepository settings;
    private PrinterSetting stored;
    private AuditLogService audit;

    @BeforeEach
    void setUp() {
        settings = mock(PrinterSettingRepository.class);
        service = new ThermalPrinterService(settings, mock(BillRepository.class), mock(PrintJobRepository.class), mock(UserRepository.class),
                mock(PrintBridgeService.class), mock(GstSettingService.class), mock(DatabaseSchemaEnsurer.class), mock(MenuItemRepository.class), mock(CategoryRepository.class));
        audit = mock(AuditLogService.class);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        stored = new PrinterSetting();
        stored.setId(1L); stored.setHost("192.168.1.37"); stored.setPort(9100); stored.setEnabled(true);
        stored.setDirectPrintSsids("Cafe WiFi");
        when(settings.findAll()).thenReturn(List.of(stored));
        when(settings.findById(1L)).thenReturn(Optional.of(stored));
        when(settings.save(any(PrinterSetting.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static PrinterSettingDTO withSsids(String ssids) {
        return PrinterSettingDTO.builder().directPrintSsids(ssids).build();
    }

    @Test
    void an_admin_can_change_the_wifi_and_it_is_tidied() {
        service.updateSettings(withSsids(" Cafe 5G ,, cafe 5g, Staff WiFi "), true);
        assertEquals("Cafe 5G, Staff WiFi", stored.getDirectPrintSsids());
    }

    @Test
    void a_manager_cannot_change_it() {
        assertThrows(AccessDeniedException.class, () -> service.updateSettings(withSsids("Somewhere Else"), false));
        assertEquals("Cafe WiFi", stored.getDirectPrintSsids());
        verify(settings, never()).save(any());
    }

    @Test
    void a_manager_cannot_clear_it_either() {
        assertThrows(AccessDeniedException.class, () -> service.updateSettings(withSsids(""), false));
        assertEquals("Cafe WiFi", stored.getDirectPrintSsids());
    }

    @Test
    void a_manager_saving_other_settings_with_the_same_wifi_value_is_fine() {
        service.updateSettings(PrinterSettingDTO.builder().directPrintSsids("  Cafe WiFi ,").headerTitle("NERAM CAFE").build(), false);
        assertEquals("Cafe WiFi", stored.getDirectPrintSsids(), "an unchanged value (even with stray spaces or commas) is not a change");
        assertEquals("NERAM CAFE", stored.getHeaderTitle());
    }

    @Test
    void a_manager_who_does_not_send_the_wifi_leaves_it_alone() {
        service.updateSettings(PrinterSettingDTO.builder().headerTitle("NERAM").build(), false);
        assertEquals("Cafe WiFi", stored.getDirectPrintSsids());
    }

    @Test
    void an_admin_change_is_written_to_the_audit_log_with_the_old_and_new_names() {
        service.updateSettings(withSsids("Cafe 5G"), true);
        verify(audit).log("PRINTER_WIFI_CHANGED", "PRINTER_SETTING", 1L, "Cafe WiFi", "Cafe 5G");
    }

    @Test
    void a_refused_manager_attempt_is_logged_as_denied_and_nothing_changes() {
        assertThrows(AccessDeniedException.class, () -> service.updateSettings(withSsids("Elsewhere"), false));
        verify(audit).log("PRINTER_WIFI_CHANGE_DENIED", "PRINTER_SETTING", 1L, "Cafe WiFi", "Elsewhere");
        verify(audit, never()).log(eq("PRINTER_WIFI_CHANGED"), any(), any(), any(), any());
    }

    @Test
    void saving_the_same_wifi_again_writes_no_audit_entry() {
        service.updateSettings(withSsids("  Cafe WiFi "), true);
        verifyNoInteractions(audit);
    }

    @Test
    void a_failing_audit_write_never_stops_the_setting_from_saving() {
        doThrow(new RuntimeException("audit table locked")).when(audit).log(any(), any(), any(), any(), any());
        service.updateSettings(withSsids("Cafe 5G"), true);
        assertEquals("Cafe 5G", stored.getDirectPrintSsids());
    }

    @Test
    void the_transaction_belongs_to_the_save_and_not_to_the_permission_check() throws Exception {
        // A refused request throws; if that check ran inside the transaction, its own audit entry would be rolled back with it.
        var save = ThermalPrinterService.class.getMethod("updateSettings", PrinterSettingDTO.class);
        var checked = ThermalPrinterService.class.getMethod("updateSettings", PrinterSettingDTO.class, boolean.class);
        assertNotNull(save.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        assertNull(checked.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
}
