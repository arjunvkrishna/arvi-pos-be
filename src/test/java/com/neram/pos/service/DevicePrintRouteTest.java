package com.neram.pos.service;

import com.neram.pos.config.DatabaseSchemaEnsurer;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** "Print from this phone": tickets are held for the phone that asked, with the printer address, instead of going to the cloud bridge. */
class DevicePrintRouteTest {

    private ThermalPrinterService service;
    private PrintBridgeService printBridgeService;

    @BeforeEach
    void setUp() {
        PrinterSettingRepository settings = mock(PrinterSettingRepository.class);
        BillRepository billRepository = mock(BillRepository.class);
        printBridgeService = mock(PrintBridgeService.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        service = new ThermalPrinterService(settings, billRepository, mock(PrintJobRepository.class), mock(UserRepository.class),
                printBridgeService, mock(GstSettingService.class), mock(DatabaseSchemaEnsurer.class), menuItemRepository, categoryRepository);

        PrinterSetting s = new PrinterSetting();
        s.setId(1L); s.setHost("192.168.1.37"); s.setPort(9100); s.setEnabled(true);
        s.setKotHost("192.168.1.41"); s.setKotPort(9100); s.setKotEnabled(true);
        s.setJuiceKotHost("192.168.1.42"); s.setJuiceKotPort(9100); s.setJuiceKotEnabled(true);
        s.setPaperWidthMm(80); s.setLogoEnabled(false);
        when(settings.findAll()).thenReturn(List.of(s));
        when(settings.findById(1L)).thenReturn(Optional.of(s));
        when(printBridgeService.isBridgeOnline()).thenReturn(true);
        when(printBridgeService.enqueuePrintJob(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));

        Bill bill = new Bill();
        bill.setId(7L); bill.setBillNumber("NRM-7"); bill.setKotNumber("NRM-7"); bill.setTableNumber("T-03");
        bill.setOrderType(Bill.OrderType.TABLE); bill.setStatus(Bill.Status.KOT_PRINTED);
        bill.setKotPrintedAt(LocalDateTime.of(2026, 10, 22, 14, 5)); bill.setKotUpdateCount(0);
        MenuItem m = new MenuItem();
        m.setId(11L); m.setName("Chicken Biryani"); m.setCategoryId(10L);
        when(menuItemRepository.findById(11L)).thenReturn(Optional.of(m));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(new Category(10L, "Main Course", "", "fa-utensils", 1, true, LocalDateTime.now())));
        BillItem b = new BillItem();
        b.setId(1L); b.setMenuItemId(11L); b.setItemName("Chicken Biryani"); b.setQuantity(2);
        bill.setItems(new ArrayList<>(List.of(b)));
        when(billRepository.findById(7L)).thenReturn(Optional.of(bill));
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void closeScope() {
        DevicePrintScope scope = DevicePrintScope.current();
        if (scope != null) scope.close();
    }

    @Test
    void without_a_scope_the_ticket_waits_for_the_cloud_bridge_as_before() {
        service.reprintKot(7L);

        ArgumentCaptor<PrintJob> job = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService).enqueuePrintJob(job.capture());
        assertEquals(PrintJobStatus.PENDING, job.getValue().getStatus());
        assertNull(job.getValue().getClaimedByBridgeId());
    }

    @Test
    void in_a_scope_the_ticket_is_held_for_the_phone_and_its_bytes_and_printer_are_handed_back() {
        DevicePrintScope scope = DevicePrintScope.open("device:waiter1:abc123");

        PrintResultDTO r = service.reprintKot(7L);

        ArgumentCaptor<PrintJob> job = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService).enqueuePrintJob(job.capture());
        PrintJob j = job.getValue();
        assertEquals(PrintJobStatus.CLAIMED, j.getStatus(), "CLAIMED, so the bridge's claim query (PENDING/RETRY_PENDING) skips it");
        assertEquals("device:waiter1:abc123", j.getClaimedByBridgeId());
        assertNotNull(j.getLeaseExpiresAt(), "if the phone vanishes the lease expiry gives the job to the bridge");
        assertTrue(j.getLeaseExpiresAt().isAfter(LocalDateTime.now().plusSeconds(DevicePrintScope.LEASE_SECONDS - 5)));
        assertEquals(1, j.getAttemptCount());

        assertTrue(r.isSuccess());
        assertEquals(1, scope.jobs().size());
        var handed = scope.jobs().get(0);
        assertEquals(j.getJobId(), handed.getJobId());
        assertEquals("192.168.1.41", handed.getHost());
        assertEquals(9100, handed.getPort());
        assertArrayEquals(j.getPayload(), Base64.getDecoder().decode(handed.getDataBase64()));
    }

    @Test
    void a_repeated_tap_that_returns_an_existing_job_is_not_handed_to_the_phone() {
        PrintJob already = PrintJob.builder().jobId("old-job").jobType("KOT").targetPrinterHost("192.168.1.41").targetPrinterPort(9100)
                .payload(new byte[]{1}).status(PrintJobStatus.PENDING).build();
        when(printBridgeService.enqueuePrintJob(any(PrintJob.class))).thenReturn(already);
        DevicePrintScope scope = DevicePrintScope.open("device:waiter1");

        PrintResultDTO r = service.reprintKot(7L);

        assertTrue(scope.jobs().isEmpty(), "that job belongs to the bridge queue; printing it here would print twice");
        assertEquals("old-job", r.getJobId());
    }

    @Test
    void phone_ids_are_made_safe_and_ssid_lists_are_tidied() {
        assertEquals("device:waiter_1:abc-12", DevicePrintScope.deviceIdFor("waiter_1", "abc-12"));
        assertEquals("device:staff", DevicePrintScope.deviceIdFor(null, null));
        assertEquals("device:ab:cd", DevicePrintScope.deviceIdFor("a b", "c;d"));
        assertTrue(DevicePrintScope.deviceIdFor("x".repeat(500), "y".repeat(500)).length() <= 100);

        assertEquals("Cafe WiFi, Cafe 5G", DevicePrintScope.cleanSsidList("  Cafe WiFi ,, cafe wifi\n Cafe 5G "));
        assertEquals("", DevicePrintScope.cleanSsidList(null));
        assertEquals("", DevicePrintScope.cleanSsidList(" , ,"));
    }
}
