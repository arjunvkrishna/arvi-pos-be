package com.neram.pos.service;

import com.neram.pos.config.DatabaseSchemaEnsurer;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The payment QR on the bill must stay a scannable QR code after it is turned into thermal-printer dots. */
class PaymentQrRasterTest {

    private BufferedImage source() {
        return QrFixture.image();
    }

    private static boolean isDark(BufferedImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        return (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF)) < 128;
    }

    @Test
    void the_picture_is_read_as_a_QR_code_with_its_caption() throws Exception {
        PaymentQrRaster.Parsed p = PaymentQrRaster.parse(source());
        assertNotNull(p);
        assertEquals(29, p.size(), "a version 3 QR code has 29 x 29 modules");
        int n = p.size();
        assertTrue(p.modules[0][0] && p.modules[0][n - 1] && p.modules[n - 1][0], "the three finder patterns");
        assertFalse(p.modules[n - 1][n - 1], "no finder pattern in the fourth corner");
        // finder pattern: 7x7 ring, white ring, 3x3 centre
        for (int i = 0; i < 7; i++) assertTrue(p.modules[0][i] && p.modules[6][i] && p.modules[i][0] && p.modules[i][6]);
        assertFalse(p.modules[1][1] || p.modules[1][5] || p.modules[5][1]);
        assertTrue(p.modules[3][3]);
        assertNotNull(p.caption, "the UPI id underneath is kept");
    }

    @Test
    void the_printed_picture_has_the_exact_same_modules_at_a_whole_number_of_dots_and_a_white_margin() throws Exception {
        PaymentQrRaster.Parsed p = PaymentQrRaster.parse(source());
        for (int paper : new int[]{576, 384}) {
            BufferedImage out = PaymentQrRaster.compose(p, paper);
            int n = p.size();
            int m = PaymentQrRaster.dotsPerModule(paper, n);
            assertTrue(m >= 6, "modules of at least 6 dots (0.75 mm) still scan easily, got " + m + " for " + paper);
            if (paper == 576) assertEquals(7, m, "60% of the 12-dot size on 80 mm paper");
            assertTrue(out.getWidth() <= paper, "fits the paper");
            int quiet = 4 * m;
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                // every dot of a module has the module's colour (sharp edges), and it matches the original code
                for (int dy : new int[]{0, m - 1}) for (int dx : new int[]{0, m - 1}) {
                    assertEquals(p.modules[j][i], isDark(out, quiet + i * m + dx, quiet + j * m + dy), "module " + i + "," + j + " at " + paper);
                }
            }
            for (int y = 0; y < quiet; y++) for (int x = 0; x < out.getWidth(); x++) assertFalse(isDark(out, x, y), "top margin");
            for (int y = 0; y < quiet + n * m; y++) for (int x = 0; x < quiet; x++) assertFalse(isDark(out, x, y) || isDark(out, out.getWidth() - 1 - x, y), "side margins");
            for (int x = 0; x < out.getWidth(); x++) for (int y = quiet + n * m; y < quiet + n * m + quiet / 2; y++) assertFalse(isDark(out, x, y), "margin under the code");
        }
    }

    @Test
    void the_printer_bytes_decode_back_to_the_same_picture_centred_on_the_paper() throws Exception {
        PaymentQrRaster.Parsed p = PaymentQrRaster.parse(source());
        int paper = 576;
        BufferedImage img = PaymentQrRaster.compose(p, paper);
        byte[] cmd = PaymentQrRaster.toEscPos(img, paper);
        assertEquals(0x1D, cmd[0] & 0xFF); assertEquals(0x76, cmd[1] & 0xFF); assertEquals(0x30, cmd[2] & 0xFF); assertEquals(0, cmd[3]);
        int bytesPerRow = (cmd[4] & 0xFF) | ((cmd[5] & 0xFF) << 8);
        int rows = (cmd[6] & 0xFF) | ((cmd[7] & 0xFF) << 8);
        assertEquals(paper / 8, bytesPerRow);
        assertEquals(img.getHeight(), rows);
        assertEquals(8 + bytesPerRow * rows, cmd.length);
        int pad = (bytesPerRow - (img.getWidth() + 7) / 8) / 2;
        for (int y = 0; y < rows; y += 3) for (int x = 0; x < img.getWidth(); x++) {
            int bit = (cmd[8 + y * bytesPerRow + pad + x / 8] >> (7 - x % 8)) & 1;
            assertEquals(isDark(img, x, y) ? 1 : 0, bit, "dot " + x + "," + y);
        }
    }

    @Test
    void a_picture_that_is_not_a_QR_code_is_refused_so_the_bill_still_prints() {
        assertNull(PaymentQrRaster.parse(null));
        BufferedImage blank = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) blank.setRGB(x, y, 0xFFFFFF);
        assertNull(PaymentQrRaster.parse(blank));
        BufferedImage wide = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 300; x++) wide.setRGB(x, y, x % 20 < 10 ? 0 : 0xFFFFFF);
        assertNull(PaymentQrRaster.parse(wide));
    }

    // ---- on the printed bill ----

    private ThermalPrinterService service;
    private PrintBridgeService printBridgeService;
    private PrinterSettingRepository settingsRepo;
    private PrinterSetting setting;
    private Bill bill;

    @BeforeEach
    void setUp() {
        PrinterSettingRepository settings = mock(PrinterSettingRepository.class);
        settingsRepo = settings;
        BillRepository billRepository = mock(BillRepository.class);
        printBridgeService = mock(PrintBridgeService.class);
        service = new ThermalPrinterService(settings, billRepository, mock(PrintJobRepository.class), mock(UserRepository.class),
                printBridgeService, mock(GstSettingService.class), mock(DatabaseSchemaEnsurer.class), mock(MenuItemRepository.class), mock(CategoryRepository.class));
        PrinterSetting s = new PrinterSetting();
        s.setId(1L); s.setHost("192.168.1.37"); s.setPort(9100); s.setEnabled(true);
        s.setBillHost("192.168.1.37"); s.setBillPort(9100); s.setBillEnabled(true);
        s.setPaperWidthMm(80); s.setLogoEnabled(false); s.setFooterMessage("THANK YOU! VISIT AGAIN");
        setting = s;
        when(settings.save(any(PrinterSetting.class))).thenAnswer(i -> i.getArgument(0));
        when(settings.findAll()).thenReturn(List.of(s));
        when(settings.findById(1L)).thenReturn(Optional.of(s));
        // The cafe's own uploaded payment QR (nothing ships with the backend any more)
        BrandingService branding = mock(BrandingService.class);
        when(branding.effectiveForContext(BrandingAsset.Kind.PAYMENT_QR)).thenReturn(Optional.of(
                new BrandingService.Image(QrFixture.png(), "image/png", java.time.LocalDateTime.now(), null)));
        service.setBrandingService(branding);
        when(printBridgeService.isBridgeOnline()).thenReturn(true);
        when(printBridgeService.enqueuePrintJob(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));

        bill = new Bill();
        bill.setId(5L); bill.setBillNumber("NRM-5"); bill.setTableNumber("T-02"); bill.setOrderType(Bill.OrderType.TABLE);
        bill.setStatus(Bill.Status.BILL_PRINTED);
        BillItem it = new BillItem();
        it.setId(1L); it.setItemName("Chicken Biryani"); it.setQuantity(1); it.setPrice(BigDecimal.valueOf(220)); it.setSubtotal(BigDecimal.valueOf(220));
        bill.setItems(new ArrayList<>(List.of(it)));
        bill.setSubtotal(BigDecimal.valueOf(220)); bill.setNetAmount(BigDecimal.valueOf(220));
        when(billRepository.findById(5L)).thenReturn(Optional.of(bill));
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = from; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    @Test
    void the_bill_has_the_QR_under_the_payment_mode_and_above_the_thank_you_line() {
        PrintResultDTO r = service.printBill(5L, "KEY");
        assertTrue(r.isSuccess());
        ArgumentCaptor<PrintJob> job = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService).enqueuePrintJob(job.capture());
        byte[] data = job.getValue().getPayload();

        int payMode = indexOf(data, "Payment Mode:".getBytes(StandardCharsets.UTF_8), 0);
        int scan = indexOf(data, "SCAN TO PAY (UPI)".getBytes(StandardCharsets.UTF_8), 0);
        int raster = indexOf(data, new byte[]{0x1D, 0x76, 0x30, 0x00}, scan);
        int thanks = indexOf(data, "THANK YOU! VISIT AGAIN".getBytes(StandardCharsets.UTF_8), 0);
        assertTrue(payMode > 0 && scan > payMode, "QR block comes after the payment mode");
        assertTrue(raster > scan, "the QR picture follows its label");
        assertTrue(thanks > raster, "and sits above the thank-you line");
    }

    private boolean billHasQr() {
        service.printBill(5L, "KEY" + System.nanoTime());
        ArgumentCaptor<PrintJob> job = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, atLeastOnce()).enqueuePrintJob(job.capture());
        byte[] data = job.getValue().getPayload();
        return indexOf(data, "SCAN TO PAY (UPI)".getBytes(StandardCharsets.UTF_8), 0) >= 0;
    }

    @Test
    void the_qr_is_on_unless_it_is_switched_off_in_the_printer_settings() {
        setting.setPaymentQrEnabled(null);                       // an older settings row: counts as on
        assertTrue(billHasQr());

        setting.setPaymentQrEnabled(true);
        assertTrue(billHasQr());

        setting.setPaymentQrEnabled(false);
        assertFalse(billHasQr(), "switched off: the bill prints without the QR and without its label");
    }

    @Test
    void the_switch_is_saved_and_returned_with_the_printer_settings() {
        assertTrue(service.getSettingsDTO().getPaymentQrEnabled(), "on by default");

        com.neram.pos.dto.PrinterSettingDTO off = com.neram.pos.dto.PrinterSettingDTO.builder().paymentQrEnabled(false).build();
        assertFalse(service.updateSettings(off).getPaymentQrEnabled());
        ArgumentCaptor<PrinterSetting> saved = ArgumentCaptor.forClass(PrinterSetting.class);
        verify(settingsRepo, atLeastOnce()).save(saved.capture());
        assertFalse(saved.getValue().getPaymentQrEnabled());
        assertFalse(service.getSettingsDTO().getPaymentQrEnabled());

        // a save that does not mention the switch (an older screen) leaves it as it was
        com.neram.pos.dto.PrinterSettingDTO other = com.neram.pos.dto.PrinterSettingDTO.builder().footerMessage("THANKS").build();
        assertFalse(service.updateSettings(other).getPaymentQrEnabled());

        assertTrue(service.updateSettings(com.neram.pos.dto.PrinterSettingDTO.builder().paymentQrEnabled(true).build()).getPaymentQrEnabled());
    }

    @Test
    void the_kitchen_ticket_has_no_QR() {
        bill.setStatus(Bill.Status.KOT_PRINTED);
        bill.setKotPrintedAt(java.time.LocalDateTime.now());
        bill.setKotUpdateCount(0);
        service.printKot(5L, "KOT_KEY");
        ArgumentCaptor<PrintJob> job = ArgumentCaptor.forClass(PrintJob.class);
        verify(printBridgeService, atLeastOnce()).enqueuePrintJob(job.capture());
        for (PrintJob j : job.getAllValues()) {
            assertEquals(-1, indexOf(j.getPayload(), "SCAN TO PAY".getBytes(StandardCharsets.UTF_8), 0));
        }
    }
}
