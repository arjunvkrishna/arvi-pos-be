package com.neram.pos.service;

import com.neram.pos.config.DatabaseSchemaEnsurer;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reprinting a kitchen ticket: only while the order is being prepared, marked REPRINT, and it changes nothing on the order. */
class KotReprintTest {

    private ThermalPrinterService service;
    private PrintBridgeService printBridgeService;
    private BillRepository billRepository;
    private MenuItemRepository menuItemRepository;
    private CategoryRepository categoryRepository;
    private Bill bill;
    private final LocalDateTime sentAt = LocalDateTime.of(2026, 10, 22, 14, 5);

    @BeforeEach
    void setUp() {
        PrinterSettingRepository settings = mock(PrinterSettingRepository.class);
        billRepository = mock(BillRepository.class);
        printBridgeService = mock(PrintBridgeService.class);
        menuItemRepository = mock(MenuItemRepository.class);
        categoryRepository = mock(CategoryRepository.class);
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

        bill = new Bill();
        bill.setId(7L); bill.setBillNumber("NRM-7"); bill.setKotNumber("NRM-7"); bill.setTableNumber("T-03");
        bill.setOrderType(Bill.OrderType.TABLE); bill.setStatus(Bill.Status.KOT_PRINTED); bill.setKotPrintedAt(sentAt); bill.setKotUpdateCount(0);
        when(billRepository.findById(7L)).thenReturn(Optional.of(bill));
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BillItem item(long id, long menuId, String name, int qty, long categoryId, String categoryName) {
        MenuItem m = new MenuItem();
        m.setId(menuId); m.setName(name); m.setCategoryId(categoryId);
        when(menuItemRepository.findById(menuId)).thenReturn(Optional.of(m));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(new Category(categoryId, categoryName, "", "fa-utensils", 1, true, LocalDateTime.now())));
        BillItem b = new BillItem();
        b.setId(id); b.setMenuItemId(menuId); b.setItemName(name); b.setQuantity(qty);
        return b;
    }

    private static String text(PrintJob j) { return new String(j.getPayload(), StandardCharsets.UTF_8); }

    @Test
    void only_an_order_being_prepared_can_be_reprinted() {
        for (Bill.Status st : List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED, Bill.Status.PAID, Bill.Status.CANCELLED)) {
            bill.setStatus(st);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.reprintKot(7L), st.name());
            assertFalse(e.getMessage().isBlank());
        }
        verify(printBridgeService, never()).enqueuePrintJob(any());
        assertThrows(IllegalArgumentException.class, () -> service.reprintKot(999L));
    }

    @Test
    void a_reprint_is_a_new_print_job_marked_REPRINT_with_the_same_dishes() {
        bill.setItems(new ArrayList<>(List.of(item(1, 11, "Chicken Biryani", 2, 10, "Main Course"), item(2, 12, "Butter Naan", 3, 10, "Main Course"))));

        PrintResultDTO r = service.reprintKot(7L);

        assertTrue(r.isSuccess());
        ArgumentCaptor<PrintJob> job = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, times(1)).enqueuePrintJob(job.capture());
        PrintJob j = job.getValue();
        assertEquals("KOT", j.getJobType());
        assertEquals("192.168.1.41", j.getTargetPrinterHost());
        assertTrue(j.getIdempotencyKey().startsWith("KOT_7_v0_RP"), "its own key, so it is never swallowed by the first ticket's key");
        assertNotEquals("KOT_7_v0", j.getIdempotencyKey());
        String t = text(j);
        assertTrue(t.contains("*** REPRINT ***"));
        assertTrue(t.contains("Copy printed"));
        assertTrue(t.contains("Chicken Biryani") && t.contains("Butter Naan"));
        assertTrue(t.contains("KITCHEN ORDER"));
    }

    @Test
    void the_order_itself_is_left_exactly_as_it_was() {
        bill.setItems(new ArrayList<>(List.of(item(1, 11, "Chicken Biryani", 2, 10, "Main Course"))));
        bill.getItems().get(0).setPrintedQuantity(2);

        service.reprintKot(7L);

        assertEquals(Bill.Status.KOT_PRINTED, bill.getStatus());
        assertEquals(sentAt, bill.getKotPrintedAt());
        assertEquals(0, bill.getKotUpdateCount());
        assertEquals(2, bill.getItems().get(0).getPrintedQuantity(), "what counts as already sent to the kitchen is untouched");
        verify(billRepository, never()).save(any(Bill.class));
    }

    @Test
    void a_double_tap_prints_only_one_copy() {
        bill.setItems(new ArrayList<>(List.of(item(1, 11, "Chicken Biryani", 1, 10, "Main Course"))));
        service.reprintKot(7L);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.reprintKot(7L));
        assertTrue(e.getMessage().toLowerCase().contains("wait"));
        verify(printBridgeService, times(1)).enqueuePrintJob(any());
    }

    @Test
    void food_and_juice_go_to_their_own_printers_each_marked_REPRINT() {
        bill.setItems(new ArrayList<>(List.of(item(1, 11, "Chicken Biryani", 1, 10, "Main Course"), item(2, 21, "Orange Juice", 2, 20, "Juices"))));

        PrintResultDTO r = service.reprintKot(7L);

        assertTrue(r.isSuccess());
        ArgumentCaptor<PrintJob> jobs = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, times(2)).enqueuePrintJob(jobs.capture());
        PrintJob kitchen = jobs.getAllValues().stream().filter(j -> "KOT".equals(j.getJobType())).findFirst().orElseThrow();
        PrintJob juice = jobs.getAllValues().stream().filter(j -> "JUICE_KOT".equals(j.getJobType())).findFirst().orElseThrow();
        assertEquals("192.168.1.41", kitchen.getTargetPrinterHost());
        assertEquals("192.168.1.42", juice.getTargetPrinterHost());
        assertTrue(text(kitchen).contains("*** REPRINT ***") && text(kitchen).contains("Chicken Biryani") && !text(kitchen).contains("Orange Juice"));
        assertTrue(text(juice).contains("*** REPRINT ***") && text(juice).contains("Orange Juice") && !text(juice).contains("Chicken Biryani"));
        assertNotEquals(kitchen.getIdempotencyKey(), juice.getIdempotencyKey());
    }

    @Test
    void the_normal_first_print_has_no_REPRINT_marker() {
        bill.setStatus(Bill.Status.ACTIVE);
        bill.setKotPrintedAt(null);
        bill.setItems(new ArrayList<>(List.of(item(1, 11, "Chicken Biryani", 1, 10, "Main Course"))));
        service.printKot(7L, "KOT_7_v0");
        ArgumentCaptor<PrintJob> job = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService).enqueuePrintJob(job.capture());
        assertFalse(text(job.getValue()).contains("REPRINT"));
        assertEquals(Bill.Status.KOT_PRINTED, bill.getStatus(), "the first print still marks the order as printed");
        assertNotNull(bill.getKotPrintedAt());
    }
}
