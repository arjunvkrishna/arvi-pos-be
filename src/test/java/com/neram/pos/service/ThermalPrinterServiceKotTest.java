package com.neram.pos.service;

import com.neram.pos.config.DatabaseSchemaEnsurer;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrinterSetting;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.PrintJobRepository;
import com.neram.pos.repository.PrinterSettingRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ThermalPrinterServiceKotTest {

    private ThermalPrinterService thermalPrinterService;
    private PrinterSettingRepository printerSettingRepository;
    private BillRepository billRepository;
    private PrintJobRepository printJobRepository;
    private UserRepository userRepository;
    private PrintBridgeService printBridgeService;
    private GstSettingService gstSettingService;
    private DatabaseSchemaEnsurer databaseSchemaEnsurer;
    private MenuItemRepository menuItemRepository;
    private CategoryRepository categoryRepository;

    private Bill sampleBill;
    private PrinterSetting defaultSetting;

    @BeforeEach
    void setUp() {
        printerSettingRepository = mock(PrinterSettingRepository.class);
        billRepository = mock(BillRepository.class);
        printJobRepository = mock(PrintJobRepository.class);
        userRepository = mock(UserRepository.class);
        printBridgeService = mock(PrintBridgeService.class);
        gstSettingService = mock(GstSettingService.class);
        databaseSchemaEnsurer = mock(DatabaseSchemaEnsurer.class);
        menuItemRepository = mock(MenuItemRepository.class);
        categoryRepository = mock(CategoryRepository.class);

        thermalPrinterService = new ThermalPrinterService(
                printerSettingRepository,
                billRepository,
                printJobRepository,
                userRepository,
                printBridgeService,
                gstSettingService,
                databaseSchemaEnsurer,
                menuItemRepository,
                categoryRepository
        );

        defaultSetting = new PrinterSetting();
        defaultSetting.setId(1L);
        defaultSetting.setHost("192.168.1.37");
        defaultSetting.setPort(9100);
        defaultSetting.setEnabled(true);
        defaultSetting.setBillHost("192.168.1.37");
        defaultSetting.setBillPort(9100);
        defaultSetting.setBillEnabled(true);
        defaultSetting.setKotHost("192.168.1.41");
        defaultSetting.setKotPort(9100);
        defaultSetting.setKotEnabled(true);
        defaultSetting.setJuiceKotHost("192.168.1.42");
        defaultSetting.setJuiceKotPort(9100);
        defaultSetting.setJuiceKotEnabled(true);
        defaultSetting.setJuiceKotHeaderTitle("*** JUICE ORDER ***");
        defaultSetting.setJuiceKotSubheader("NERAM - JUICE BAR");
        defaultSetting.setJuiceKotFooterMessage("--- END OF JUICE KOT ---");
        defaultSetting.setPaperWidthMm(80);
        defaultSetting.setAutoCut(true);
        defaultSetting.setHeaderTitle("NERAM");
        defaultSetting.setFooterMessage("THANK YOU");

        when(printerSettingRepository.findAll()).thenReturn(List.of(defaultSetting));
        when(printerSettingRepository.findById(1L)).thenReturn(Optional.of(defaultSetting));
        when(printerSettingRepository.save(any(PrinterSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(printBridgeService.isBridgeOnline()).thenReturn(true);
        when(printBridgeService.enqueuePrintJob(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sampleBill = new Bill();
        sampleBill.setId(101L);
        sampleBill.setBillNumber("NRM-20261008-0001");
        sampleBill.setKotNumber("NRM-20261008-0001");
        sampleBill.setTableNumber("T-04");
        sampleBill.setOrderType(Bill.OrderType.TABLE);
        sampleBill.setStatus(Bill.Status.ACTIVE);
        sampleBill.setKotUpdateCount(0);
        sampleBill.setNotes("Extra crispy");

        when(billRepository.findById(101L)).thenReturn(Optional.of(sampleBill));
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testIsJuiceCategory() {
        assertTrue(thermalPrinterService.isJuiceCategory("Juice"));
        assertTrue(thermalPrinterService.isJuiceCategory("Juices"));
        assertTrue(thermalPrinterService.isJuiceCategory("JUICE"));
        assertTrue(thermalPrinterService.isJuiceCategory("juices"));
        assertTrue(thermalPrinterService.isJuiceCategory("Fresh Juice"));
        assertTrue(thermalPrinterService.isJuiceCategory("Fresh Juices Bar"));
        assertTrue(thermalPrinterService.isJuiceCategory("Cold Juices"));

        assertFalse(thermalPrinterService.isJuiceCategory("Starters"));
        assertFalse(thermalPrinterService.isJuiceCategory("Biryani"));
        assertFalse(thermalPrinterService.isJuiceCategory("Main Course"));
        assertFalse(thermalPrinterService.isJuiceCategory("Hot Beverages"));
        assertFalse(thermalPrinterService.isJuiceCategory("Desserts"));
        assertFalse(thermalPrinterService.isJuiceCategory(null));
        assertFalse(thermalPrinterService.isJuiceCategory("   "));
    }

    @Test
    void testFoodOnlyOrder_RoutesOnlyToKitchenKot() {
        Category foodCat = new Category(10L, "Main Course", "Food items", "fa-utensils", 1, true, LocalDateTime.now());
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(foodCat));

        MenuItem item1 = new MenuItem();
        item1.setId(1L);
        item1.setName("Paneer Butter Masala");
        item1.setCategoryId(10L);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(item1));

        MenuItem item2 = new MenuItem();
        item2.setId(2L);
        item2.setName("Butter Naan");
        item2.setCategoryId(10L);
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(item2));

        List<BillItem> items = new ArrayList<>();
        BillItem bi1 = new BillItem();
        bi1.setId(1001L);
        bi1.setMenuItemId(1L);
        bi1.setItemName("Paneer Butter Masala");
        bi1.setQuantity(2);
        items.add(bi1);

        BillItem bi2 = new BillItem();
        bi2.setId(1002L);
        bi2.setMenuItemId(2L);
        bi2.setItemName("Butter Naan");
        bi2.setQuantity(3);
        items.add(bi2);

        sampleBill.setItems(items);

        PrintResultDTO result = thermalPrinterService.printKot(101L, "KEY_FOOD");

        assertTrue(result.isSuccess());
        assertEquals("KOT", result.getPrintType());
        assertEquals("192.168.1.41", result.getHost());
        assertEquals(9100, result.getPort());

        ArgumentCaptor<PrintJob> jobCaptor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, times(1)).enqueuePrintJob(jobCaptor.capture());

        PrintJob enqueuedJob = jobCaptor.getValue();
        assertEquals("KOT", enqueuedJob.getJobType());
        assertEquals("192.168.1.41", enqueuedJob.getTargetPrinterHost());
        assertEquals(9100, enqueuedJob.getTargetPrinterPort());
        assertEquals("KEY_FOOD", enqueuedJob.getIdempotencyKey());

        String payloadStr = new String(enqueuedJob.getPayload(), StandardCharsets.UTF_8);
        assertTrue(payloadStr.contains("Paneer Butter Masala"));
        assertTrue(payloadStr.contains("Butter Naan"));
        assertTrue(payloadStr.contains("KITCHEN ORDER"));
        assertFalse(payloadStr.contains("JUICE ORDER"));
    }

    @Test
    void testJuiceOnlyOrder_RoutesOnlyToJuiceKot_NoKitchenJob() {
        Category juiceCat = new Category(20L, "Juices", "Fresh juices", "fa-wine-glass", 2, true, LocalDateTime.now());
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(juiceCat));

        MenuItem item1 = new MenuItem();
        item1.setId(5L);
        item1.setName("Watermelon Juice");
        item1.setCategoryId(20L);
        when(menuItemRepository.findById(5L)).thenReturn(Optional.of(item1));

        MenuItem item2 = new MenuItem();
        item2.setId(6L);
        item2.setName("Orange Juice");
        item2.setCategoryId(20L);
        when(menuItemRepository.findById(6L)).thenReturn(Optional.of(item2));

        List<BillItem> items = new ArrayList<>();
        BillItem bi1 = new BillItem();
        bi1.setId(2001L);
        bi1.setMenuItemId(5L);
        bi1.setItemName("Watermelon Juice");
        bi1.setQuantity(1);
        items.add(bi1);

        BillItem bi2 = new BillItem();
        bi2.setId(2002L);
        bi2.setMenuItemId(6L);
        bi2.setItemName("Orange Juice");
        bi2.setQuantity(2);
        items.add(bi2);

        sampleBill.setItems(items);

        PrintResultDTO result = thermalPrinterService.printKot(101L, "KEY_JUICE");

        assertTrue(result.isSuccess());
        assertEquals("JUICE_KOT", result.getPrintType());
        assertEquals("192.168.1.42", result.getHost());
        assertEquals(9100, result.getPort());

        ArgumentCaptor<PrintJob> jobCaptor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, times(1)).enqueuePrintJob(jobCaptor.capture());

        PrintJob enqueuedJob = jobCaptor.getValue();
        assertEquals("JUICE_KOT", enqueuedJob.getJobType());
        assertEquals("192.168.1.42", enqueuedJob.getTargetPrinterHost());
        assertEquals(9100, enqueuedJob.getTargetPrinterPort());
        assertEquals("KEY_JUICE", enqueuedJob.getIdempotencyKey());

        String payloadStr = new String(enqueuedJob.getPayload(), StandardCharsets.UTF_8);
        assertTrue(payloadStr.contains("Watermelon Juice"));
        assertTrue(payloadStr.contains("Orange Juice"));
        assertTrue(payloadStr.contains("JUICE ORDER"));
        assertFalse(payloadStr.contains("KITCHEN ORDER"));
    }

    @Test
    void testMixedOrder_SplitsKotIntoKitchenAndJuice() {
        Category foodCat = new Category(10L, "Main Course", "Food items", "fa-utensils", 1, true, LocalDateTime.now());
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(foodCat));

        Category juiceCat = new Category(20L, "Juices", "Fresh juices", "fa-wine-glass", 2, true, LocalDateTime.now());
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(juiceCat));

        MenuItem itemFood = new MenuItem();
        itemFood.setId(1L);
        itemFood.setName("Chicken Biryani");
        itemFood.setCategoryId(10L);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(itemFood));

        MenuItem itemJuice = new MenuItem();
        itemJuice.setId(5L);
        itemJuice.setName("Mango Juice");
        itemJuice.setCategoryId(20L);
        when(menuItemRepository.findById(5L)).thenReturn(Optional.of(itemJuice));

        List<BillItem> items = new ArrayList<>();
        BillItem biFood = new BillItem();
        biFood.setId(3001L);
        biFood.setMenuItemId(1L);
        biFood.setItemName("Chicken Biryani");
        biFood.setQuantity(2);
        items.add(biFood);

        BillItem biJuice = new BillItem();
        biJuice.setId(3002L);
        biJuice.setMenuItemId(5L);
        biJuice.setItemName("Mango Juice");
        biJuice.setQuantity(1);
        items.add(biJuice);

        sampleBill.setItems(items);

        PrintResultDTO result = thermalPrinterService.printKot(101L, "KEY_MIXED");

        assertTrue(result.isSuccess());
        assertEquals("KOT_SPLIT", result.getPrintType());
        assertTrue(result.getHost().contains("192.168.1.41"));
        assertTrue(result.getHost().contains("192.168.1.42"));

        ArgumentCaptor<PrintJob> jobCaptor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, times(2)).enqueuePrintJob(jobCaptor.capture());

        List<PrintJob> enqueuedJobs = jobCaptor.getAllValues();
        assertEquals(2, enqueuedJobs.size());

        PrintJob kitchenJob = enqueuedJobs.stream()
                .filter(j -> "KOT".equals(j.getJobType()))
                .findFirst()
                .orElseThrow();
        assertEquals("192.168.1.41", kitchenJob.getTargetPrinterHost());
        assertEquals(9100, kitchenJob.getTargetPrinterPort());
        assertEquals("KEY_MIXED_KITCHEN", kitchenJob.getIdempotencyKey());
        String kitchenPayload = new String(kitchenJob.getPayload(), StandardCharsets.UTF_8);
        assertTrue(kitchenPayload.contains("Chicken Biryani"));
        assertFalse(kitchenPayload.contains("Mango Juice"));
        assertTrue(kitchenPayload.contains("KITCHEN ORDER"));

        PrintJob juiceJob = enqueuedJobs.stream()
                .filter(j -> "JUICE_KOT".equals(j.getJobType()))
                .findFirst()
                .orElseThrow();
        assertEquals("192.168.1.42", juiceJob.getTargetPrinterHost());
        assertEquals(9100, juiceJob.getTargetPrinterPort());
        assertEquals("KEY_MIXED_JUICE", juiceJob.getIdempotencyKey());
        String juicePayload = new String(juiceJob.getPayload(), StandardCharsets.UTF_8);
        assertTrue(juicePayload.contains("Mango Juice"));
        assertFalse(juicePayload.contains("Chicken Biryani"));
        assertTrue(juicePayload.contains("JUICE ORDER"));
    }

    @Test
    void testMixedOrder_BillContainsAllItems() {
        Category foodCat = new Category(10L, "Main Course", "Food items", "fa-utensils", 1, true, LocalDateTime.now());
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(foodCat));

        Category juiceCat = new Category(20L, "Juices", "Fresh juices", "fa-wine-glass", 2, true, LocalDateTime.now());
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(juiceCat));

        MenuItem itemFood = new MenuItem();
        itemFood.setId(1L);
        itemFood.setName("Chicken Biryani");
        itemFood.setCategoryId(10L);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(itemFood));

        MenuItem itemJuice = new MenuItem();
        itemJuice.setId(5L);
        itemJuice.setName("Mango Juice");
        itemJuice.setCategoryId(20L);
        when(menuItemRepository.findById(5L)).thenReturn(Optional.of(itemJuice));

        List<BillItem> items = new ArrayList<>();
        BillItem biFood = new BillItem();
        biFood.setId(3001L);
        biFood.setMenuItemId(1L);
        biFood.setItemName("Chicken Biryani");
        biFood.setQuantity(2);
        biFood.setPrice(java.math.BigDecimal.valueOf(220));
        biFood.setSubtotal(java.math.BigDecimal.valueOf(440));
        items.add(biFood);

        BillItem biJuice = new BillItem();
        biJuice.setId(3002L);
        biJuice.setMenuItemId(5L);
        biJuice.setItemName("Mango Juice");
        biJuice.setQuantity(1);
        biJuice.setPrice(java.math.BigDecimal.valueOf(90));
        biJuice.setSubtotal(java.math.BigDecimal.valueOf(90));
        items.add(biJuice);

        sampleBill.setItems(items);
        sampleBill.setSubtotal(java.math.BigDecimal.valueOf(530));
        sampleBill.setNetAmount(java.math.BigDecimal.valueOf(530));

        PrintResultDTO billResult = thermalPrinterService.printBill(101L, "BILL_KEY");

        assertTrue(billResult.isSuccess());
        assertEquals("BILL", billResult.getPrintType());
        assertEquals("192.168.1.37", billResult.getHost());

        ArgumentCaptor<PrintJob> jobCaptor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, times(1)).enqueuePrintJob(jobCaptor.capture());

        PrintJob billJob = jobCaptor.getValue();
        assertEquals("BILL", billJob.getJobType());
        String billPayload = new String(billJob.getPayload(), StandardCharsets.UTF_8);
        assertTrue(billPayload.contains("Chicken Biryani"), "Customer bill must contain food items");
        assertTrue(billPayload.contains("Mango Juice"), "Customer bill must contain juice items");
    }

    @Test
    void testReprintsPreserveSameSplitAndTargetPrinters() {
        Category foodCat = new Category(10L, "Main Course", "Food items", "fa-utensils", 1, true, LocalDateTime.now());
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(foodCat));

        Category juiceCat = new Category(20L, "Juices", "Fresh juices", "fa-wine-glass", 2, true, LocalDateTime.now());
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(juiceCat));

        MenuItem itemFood = new MenuItem();
        itemFood.setId(1L);
        itemFood.setName("Paneer Butter Masala");
        itemFood.setCategoryId(10L);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(itemFood));

        MenuItem itemJuice = new MenuItem();
        itemJuice.setId(5L);
        itemJuice.setName("Lime Juice");
        itemJuice.setCategoryId(20L);
        when(menuItemRepository.findById(5L)).thenReturn(Optional.of(itemJuice));

        List<BillItem> items = new ArrayList<>();
        BillItem biFood = new BillItem();
        biFood.setId(4001L);
        biFood.setMenuItemId(1L);
        biFood.setItemName("Paneer Butter Masala");
        biFood.setQuantity(1);
        items.add(biFood);

        BillItem biJuice = new BillItem();
        biJuice.setId(4002L);
        biJuice.setMenuItemId(5L);
        biJuice.setItemName("Lime Juice");
        biJuice.setQuantity(2);
        items.add(biJuice);

        sampleBill.setItems(items);

        // First print
        thermalPrinterService.printKot(101L, "REPRINT_KEY_V1");

        // Reprint with new key
        thermalPrinterService.printKot(101L, "REPRINT_KEY_V2");

        ArgumentCaptor<PrintJob> jobCaptor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, times(4)).enqueuePrintJob(jobCaptor.capture());

        List<PrintJob> jobs = jobCaptor.getAllValues();
        assertEquals(4, jobs.size());

        assertEquals("REPRINT_KEY_V1_KITCHEN", jobs.get(0).getIdempotencyKey());
        assertEquals("192.168.1.41", jobs.get(0).getTargetPrinterHost());

        assertEquals("REPRINT_KEY_V1_JUICE", jobs.get(1).getIdempotencyKey());
        assertEquals("192.168.1.42", jobs.get(1).getTargetPrinterHost());

        assertEquals("REPRINT_KEY_V2_KITCHEN", jobs.get(2).getIdempotencyKey());
        assertEquals("192.168.1.41", jobs.get(2).getTargetPrinterHost());

        assertEquals("REPRINT_KEY_V2_JUICE", jobs.get(3).getIdempotencyKey());
        assertEquals("192.168.1.42", jobs.get(3).getTargetPrinterHost());
    }

    @Test
    void testBuildWatermarkedKotRaster_UpdatedNotice_80mm() {
        List<String> changes = List.of(
                "[+] ADDED: 2x Paneer Butter Masala",
                "[*] INCREASED: Butter Naan (Qty: 2 -> 4, +2)"
        );

        byte[] raster = thermalPrinterService.buildWatermarkedKotRaster(
                sampleBill,
                "UPDATED",
                changes,
                LocalDateTime.now(),
                null,
                80
        );

        assertNotNull(raster, "Raster output should not be null");
        assertTrue(raster.length > 500, "Raster should contain ESC/POS image data");

        // Verify ESC @ (Initialize)
        assertEquals(0x1B, raster[0]);
        assertEquals(0x40, raster[1]);

        // Verify GS v 0 0 (Raster bit image)
        assertEquals(0x1D, raster[2]);
        assertEquals(0x76, raster[3]);
        assertEquals(0x30, raster[4]);
        assertEquals(0x00, raster[5]);

        // Verify 80mm row byte width = 576 / 8 = 72 bytes
        int bytesPerRow = (raster[6] & 0xFF) + ((raster[7] & 0xFF) * 256);
        assertEquals(72, bytesPerRow, "80mm paper should have 72 bytes per row (576 dots)");

        // Verify cut paper command at the end (GS V 66 0)
        assertEquals(0x1D, raster[raster.length - 4]);
        assertEquals(0x56, raster[raster.length - 3]);
        assertEquals(0x42, raster[raster.length - 2]);
        assertEquals(0x00, raster[raster.length - 1]);
    }

    @Test
    void testBuildWatermarkedKotRaster_CancelledNotice_58mm() {
        byte[] raster = thermalPrinterService.buildWatermarkedKotRaster(
                sampleBill,
                "CANCELLED",
                List.of(),
                LocalDateTime.now(),
                "Customer changed mind",
                58
        );

        assertNotNull(raster, "Raster output should not be null");
        assertTrue(raster.length > 500, "Raster should contain ESC/POS image data");

        // Verify 58mm row byte width = 384 / 8 = 48 bytes
        int bytesPerRow = (raster[6] & 0xFF) + ((raster[7] & 0xFF) * 256);
        assertEquals(48, bytesPerRow, "58mm paper should have 48 bytes per row (384 dots)");

        // Verify cut command
        assertEquals(0x1D, raster[raster.length - 4]);
        assertEquals(0x56, raster[raster.length - 3]);
        assertEquals(0x42, raster[raster.length - 2]);
        assertEquals(0x00, raster[raster.length - 1]);
    }

    @Test
    void testParseJuiceKotCategoryIds() {
        Set<Long> ids = thermalPrinterService.parseJuiceKotCategoryIds(" 12, 34,abc, 56 ,, ");
        assertEquals(3, ids.size());
        assertTrue(ids.contains(12L));
        assertTrue(ids.contains(34L));
        assertTrue(ids.contains(56L));

        assertTrue(thermalPrinterService.parseJuiceKotCategoryIds(null).isEmpty());
        assertTrue(thermalPrinterService.parseJuiceKotCategoryIds("").isEmpty());
    }

    @Test
    void testDynamicCategoryRouting_MojitoAndShakes_RoutesToJuiceKot() {
        // PrinterSetting configured with category 35 (Mojitos) and 42 (Milkshakes)
        defaultSetting.setJuiceKotCategoryIds("35,42");

        MenuItem mojitoItem = new MenuItem();
        mojitoItem.setId(301L);
        mojitoItem.setName("Virgin Mojito");
        mojitoItem.setCategoryId(35L);
        when(menuItemRepository.findById(301L)).thenReturn(Optional.of(mojitoItem));

        MenuItem shakeItem = new MenuItem();
        shakeItem.setId(302L);
        shakeItem.setName("Belgian Chocolate Shake");
        shakeItem.setCategoryId(42L);
        when(menuItemRepository.findById(302L)).thenReturn(Optional.of(shakeItem));

        MenuItem foodItem = new MenuItem();
        foodItem.setId(303L);
        foodItem.setName("Chicken Biryani");
        foodItem.setCategoryId(10L); // Main Course (not in 35,42)
        when(menuItemRepository.findById(303L)).thenReturn(Optional.of(foodItem));

        assertTrue(thermalPrinterService.isJuiceItem(301L, "Virgin Mojito", defaultSetting));
        assertTrue(thermalPrinterService.isJuiceItem(302L, "Belgian Chocolate Shake", defaultSetting));
        assertFalse(thermalPrinterService.isJuiceItem(303L, "Chicken Biryani", defaultSetting));

        // Mixed order with Mojito and Biryani splits cleanly
        List<BillItem> items = new ArrayList<>();
        BillItem bi1 = new BillItem();
        bi1.setId(3001L);
        bi1.setMenuItemId(301L);
        bi1.setItemName("Virgin Mojito");
        bi1.setQuantity(1);
        items.add(bi1);

        BillItem bi2 = new BillItem();
        bi2.setId(3002L);
        bi2.setMenuItemId(303L);
        bi2.setItemName("Chicken Biryani");
        bi2.setQuantity(1);
        items.add(bi2);

        sampleBill.setItems(items);

        PrintResultDTO result = thermalPrinterService.printKot(101L, "KEY_DYNAMIC_SPLIT");
        assertTrue(result.isSuccess());
        assertEquals("KOT_SPLIT", result.getPrintType());
    }

    @Test
    void testDynamicCategoryRouting_Unconfigured_FallsBackToName() {
        defaultSetting.setJuiceKotCategoryIds("");

        Category juiceCat = new Category(20L, "Juices", "Fresh juices", "fa-wine-glass", 2, true, LocalDateTime.now());
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(juiceCat));

        MenuItem juiceItem = new MenuItem();
        juiceItem.setId(501L);
        juiceItem.setName("Lime Juice");
        juiceItem.setCategoryId(20L);
        when(menuItemRepository.findById(501L)).thenReturn(Optional.of(juiceItem));

        assertTrue(thermalPrinterService.isJuiceItem(501L, "Lime Juice", defaultSetting));
    }
}
