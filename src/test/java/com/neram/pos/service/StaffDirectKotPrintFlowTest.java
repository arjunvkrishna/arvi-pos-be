package com.neram.pos.service;

import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class StaffDirectKotPrintFlowTest {

    @Mock
    private BillRepository billRepository;

    @Mock
    private BillItemRepository billItemRepository;

    @Mock
    private DiningTableRepository diningTableRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private GstSettingService gstSettingService;

    @Mock
    private CashierShiftService cashierShiftService;

    @Mock
    private RecipeService recipeService;

    @Mock
    private BillRefundRepository billRefundRepository;

    @Mock
    private ThermalPrinterService thermalPrinterService;

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PrinterSettingRepository printerSettingRepository;

    @Mock
    private PromotionService promotionService;

    @InjectMocks
    private BillService billService;

    private Bill sampleBill;

    @BeforeEach
    void setUp() {
        sampleBill = new Bill();
        sampleBill.setId(201L);
        sampleBill.setBillNumber("NRM-20261013-0001");
        sampleBill.setKotNumber("NRM-20261013-0001");
        sampleBill.setTableNumber("T-02");
        sampleBill.setOrderType(Bill.OrderType.TABLE);
        sampleBill.setStatus(Bill.Status.ACTIVE);
        sampleBill.setSubtotal(BigDecimal.valueOf(350.00));
        sampleBill.setNetAmount(BigDecimal.valueOf(350.00));
        sampleBill.setCreatedAt(LocalDateTime.now());

        List<BillItem> items = new ArrayList<>();
        BillItem bi1 = new BillItem();
        bi1.setId(1L);
        bi1.setBill(sampleBill);
        bi1.setMenuItemId(10L);
        bi1.setItemName("Cappuccino");
        bi1.setQuantity(2);
        bi1.setPrintedQuantity(0);
        bi1.setPrice(BigDecimal.valueOf(175.00));
        bi1.setSubtotal(BigDecimal.valueOf(350.00));
        items.add(bi1);
        sampleBill.setItems(items);

        when(billRepository.findById(201L)).thenReturn(Optional.of(sampleBill));
        when(billRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(sampleBill));
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billRefundRepository.sumSuccessfulRefundsByBillId(anyLong())).thenReturn(BigDecimal.ZERO);
        GstSetting gstSetting = GstSetting.builder()
                .enabled(false)
                .roundOffEnabled(true)
                .cgstPercent(0.0)
                .sgstPercent(0.0)
                .build();
        when(gstSettingService.getSettings()).thenReturn(gstSetting);
    }

    @Test
    @DisplayName("Step 1 & 2: Staff can print KOT immediately after saving order without waiting for manager approval")
    void testStaffCanPrintKotImmediately() {
        assertEquals(Bill.Status.ACTIVE, sampleBill.getStatus());
        assertNull(sampleBill.getKotPrintedAt());
        assertEquals(0, sampleBill.getItems().get(0).getPrintedQuantity());

        BillResponseDTO result = billService.printKitchenOrder(201L);

        assertNotNull(result);
        assertEquals(Bill.Status.KOT_PRINTED.name(), result.getStatus());
        assertNotNull(sampleBill.getKotPrintedAt());
        assertEquals(2, sampleBill.getItems().get(0).getPrintedQuantity());
        assertEquals(Bill.Status.KOT_PRINTED, sampleBill.getStatus());

        // The kitchen ticket takes NO stock: stock follows the settled bill (payment), see BillStockSyncTest
        verify(recipeService, never()).settleBill(any());
        verify(auditLogService, times(1)).log(eq("PRINT_KOT"), eq("BILL"), eq(201L), isNull(), contains("Printed Kitchen Order Ticket (KOT)"));
    }

    @Test
    @DisplayName("Step 3: Staff can request bill immediately after KOT is printed")
    void testStaffCanRequestBillAfterKotPrinted() {
        // First print KOT
        billService.printKitchenOrder(201L);
        assertEquals(Bill.Status.KOT_PRINTED, sampleBill.getStatus());

        // Staff requests bill
        BillResponseDTO billReq = billService.requestBill(201L);

        assertNotNull(billReq);
        assertEquals(Bill.Status.BILL_REQUESTED.name(), billReq.getStatus());
        assertEquals(Bill.Status.BILL_REQUESTED, sampleBill.getStatus());
        verify(auditLogService, times(1)).log(eq("REQUEST_BILL"), eq("BILL"), eq(201L), isNull(), contains("Bill requested by staff"));
    }

    @Test
    @DisplayName("Bill request rejected if KOT has not been printed yet")
    void testRequestBillRejectedIfKotNotPrinted() {
        // Order is still ACTIVE, KOT not printed
        assertEquals(Bill.Status.ACTIVE, sampleBill.getStatus());
        assertNull(sampleBill.getKotPrintedAt());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            billService.requestBill(201L);
        });

        assertTrue(ex.getMessage().contains("Bill can only be requested after the order has been sent to kitchen and printed"));
    }

    @Test
    @DisplayName("Step 4: Manager prints the bill when bill is requested, preserving existing billing behavior")
    void testManagerPrintsBillAfterRequest() {
        // Complete Steps 1-3
        billService.printKitchenOrder(201L);
        billService.requestBill(201L);
        assertEquals(Bill.Status.BILL_REQUESTED, sampleBill.getStatus());

        // Step 4: Manager prints the bill
        BillResponseDTO printedBill = billService.printBill(201L, null);

        assertNotNull(printedBill);
        assertEquals(Bill.Status.BILL_PRINTED.name(), printedBill.getStatus());
        assertNotNull(sampleBill.getPrintedAt());

        // Confirm payment
        BillResponseDTO paidBill = billService.confirmPayment(201L, null);
        assertNotNull(paidBill);
        assertEquals(Bill.Status.PAID.name(), paidBill.getStatus());
    }

    @Test
    @DisplayName("KOT print retries with same idempotency key return existing job without creating duplicates")
    void testKotPrintRetryDoesNotCreateDuplicateJob() {
        String key = "KOT_201_v0";
        PrintJob existingJob = PrintJob.builder()
                .id(999L)
                .jobId("job-existing-123")
                .billId(201L)
                .jobType("KOT")
                .status(PrintJobStatus.PENDING)
                .idempotencyKey(key)
                .build();

        // PrintBridgeService returns the existing job if idempotency key matches
        when(printJobRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existingJob));

        PrintBridgeService realBridgeService = new PrintBridgeService(printJobRepository, billRepository, null, null);
        PrintJob submittedJob = PrintJob.builder()
                .jobId("job-new-456")
                .billId(201L)
                .jobType("KOT")
                .status(PrintJobStatus.PENDING)
                .idempotencyKey(key)
                .build();

        PrintJob resultJob = realBridgeService.enqueuePrintJob(submittedJob);

        assertEquals("job-existing-123", resultJob.getJobId());
        // Verify no new save was triggered for duplicate
        verify(printJobRepository, never()).save(submittedJob);
    }
}
