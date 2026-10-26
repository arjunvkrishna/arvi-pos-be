package com.neram.pos.service;

import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.dto.ChangeLocationRequestDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.GstSetting;
import com.neram.pos.entity.SystemNotification;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderLocationChangeTest {

    @Mock private BillRepository billRepository;
    @Mock private BillItemRepository billItemRepository;
    @Mock private DiningTableRepository diningTableRepository;
    @Mock private UserRepository userRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private GstSettingService gstSettingService;
    @Mock private CashierShiftService cashierShiftService;
    @Mock private RecipeService recipeService;
    @Mock private BillRefundRepository billRefundRepository;
    @Mock private PromotionService promotionService;
    @Mock private SystemNotificationRepository notificationRepository;
    @Mock private NotificationStreamService notificationStream;
    @InjectMocks private BillService billService;

    @BeforeEach
    void setUp() {
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        when(diningTableRepository.existsByNameIgnoreCaseAndActiveTrue("T-01")).thenReturn(true);
        when(diningTableRepository.existsByNameIgnoreCaseAndActiveTrue("T-02")).thenReturn(true);
        when(billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(gstSettingService.getSettings()).thenReturn(GstSetting.builder()
                .enabled(false).roundOffEnabled(false).cgstPercent(0.0).sgstPercent(0.0).build());
    }

    private BillItem item(Long menuId, String name, int qty, int printed, String price) {
        BillItem i = new BillItem();
        i.setMenuItemId(menuId);
        i.setItemName(name);
        i.setQuantity(qty);
        i.setPrintedQuantity(printed);
        i.setPrice(new BigDecimal(price));
        i.setSubtotal(new BigDecimal(price).multiply(BigDecimal.valueOf(qty)));
        i.setIsTakeaway(false);
        return i;
    }

    private Bill tableBill(long id, String table, Bill.Status status, BillItem... items) {
        Bill b = new Bill();
        b.setId(id);
        b.setBillNumber("B" + id);
        b.setOrderType(Bill.OrderType.TABLE);
        b.setTableNumber(table);
        b.setStatus(status);
        b.setItems(new ArrayList<>(List.of(items)));
        BigDecimal sub = BigDecimal.ZERO;
        for (BillItem i : items) { i.setBill(b); sub = sub.add(i.getSubtotal()); }
        b.setSubtotal(sub);
        when(billRepository.findByIdForUpdate(id)).thenReturn(Optional.of(b));
        return b;
    }

    private ChangeLocationRequestDTO toTable(String table) {
        ChangeLocationRequestDTO dto = new ChangeLocationRequestDTO();
        dto.setOrderType("TABLE");
        dto.setTableNumber(table);
        return dto;
    }

    @Test
    @DisplayName("Table to free table: only the table changes")
    void tableToFreeTable() {
        Bill b = tableBill(1, "T-01", Bill.Status.ACTIVE, item(1L, "Fries", 2, 0, "100"));
        BillResponseDTO out = billService.changeOrderLocation(1L, toTable("T-02"));
        assertEquals("T-02", out.getTableNumber());
        assertEquals(Bill.Status.ACTIVE, b.getStatus());
        verify(diningTableRepository).lockActiveByNameIgnoreCase("T-02");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Moving to the table it is already on is rejected")
    void sameTableRejected() {
        tableBill(1, "T-01", Bill.Status.ACTIVE, item(1L, "Fries", 1, 0, "100"));
        assertThrows(IllegalArgumentException.class, () -> billService.changeOrderLocation(1L, toTable("t-01")));
    }

    @Test
    @DisplayName("Unknown table is rejected")
    void unknownTableRejected() {
        tableBill(1, "T-01", Bill.Status.ACTIVE, item(1L, "Fries", 1, 0, "100"));
        assertThrows(IllegalArgumentException.class, () -> billService.changeOrderLocation(1L, toTable("T-99")));
    }

    @Test
    @DisplayName("Occupied target table is rejected unless merge is requested")
    void occupiedTargetRejected() {
        tableBill(1, "T-01", Bill.Status.ACTIVE, item(1L, "Fries", 1, 0, "100"));
        Bill other = tableBill(2, "T-02", Bill.Status.ACTIVE, item(2L, "Tea", 1, 0, "20"));
        when(billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(eq("T-02"), any())).thenReturn(Optional.of(other));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> billService.changeOrderLocation(1L, toTable("T-02")));
        assertTrue(e.getMessage().contains("already has an active order"));
    }

    @Test
    @DisplayName("Table to outdoor needs a customer name and issues an outdoor token")
    void tableToOutdoor() {
        Bill b = tableBill(1, "T-01", Bill.Status.ACTIVE, item(1L, "Fries", 1, 0, "100"));
        when(billRepository.findMaxOutdoorTokenNumber()).thenReturn(4);
        ChangeLocationRequestDTO dto = new ChangeLocationRequestDTO();
        dto.setOrderType("OUTDOOR");
        assertThrows(IllegalArgumentException.class, () -> billService.changeOrderLocation(1L, dto));

        dto.setCustomerName("Asha");
        billService.changeOrderLocation(1L, dto);
        assertEquals(Bill.OrderType.OUTDOOR, b.getOrderType());
        assertNull(b.getTableNumber());
        assertEquals("OUT-005", b.getOutdoorToken());
    }

    @Test
    @DisplayName("Outdoor to table clears the outdoor token and frees the outdoor slot")
    void outdoorToTable() {
        Bill b = tableBill(1, null, Bill.Status.ACTIVE, item(1L, "Fries", 1, 0, "100"));
        b.setOrderType(Bill.OrderType.OUTDOOR);
        b.setOutdoorToken("OUT-003");
        b.setCustomerName("Asha");
        billService.changeOrderLocation(1L, toTable("T-01"));
        assertEquals(Bill.OrderType.TABLE, b.getOrderType());
        assertEquals("T-01", b.getTableNumber());
        assertNull(b.getOutdoorToken());
        assertEquals("Asha", b.getCustomerName());
    }

    @Test
    @DisplayName("Orders whose bill is requested/printed, or that are closed, cannot be moved")
    void blockedStatuses() {
        for (Bill.Status s : new Bill.Status[]{Bill.Status.BILL_REQUESTED, Bill.Status.BILL_PRINTED, Bill.Status.PAID, Bill.Status.CANCELLED}) {
            tableBill(1, "T-01", s, item(1L, "Fries", 1, 0, "100"));
            assertThrows(IllegalStateException.class, () -> billService.changeOrderLocation(1L, toTable("T-02")), s.name());
        }
    }

    @Test
    @DisplayName("Moving after the KOT was printed raises a notification for the kitchen")
    void kotPrintedRaisesNotification() {
        Bill b = tableBill(1, "T-01", Bill.Status.KOT_PRINTED, item(1L, "Fries", 1, 1, "100"));
        b.setKotPrintedAt(java.time.LocalDateTime.now());
        billService.changeOrderLocation(1L, toTable("T-02"));
        verify(notificationRepository).save(any(SystemNotification.class));
    }

    @Test
    @DisplayName("Merge sums matching dishes, keeps printed quantities, moves stock usage and closes the source")
    void mergeIntoOccupiedTable() {
        Bill source = tableBill(1, "T-01", Bill.Status.KOT_PRINTED, item(1L, "Fries", 2, 2, "100"), item(3L, "Coke", 1, 1, "40"));
        source.setKotPrintedAt(java.time.LocalDateTime.now());
        Bill target = tableBill(2, "T-02", Bill.Status.ACTIVE, item(1L, "Fries", 1, 0, "100"), item(2L, "Tea", 1, 0, "20"));
        when(billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(eq("T-02"), any())).thenReturn(Optional.of(target));

        ChangeLocationRequestDTO dto = toTable("T-02");
        dto.setMergeIfOccupied(true);
        BillResponseDTO out = billService.changeOrderLocation(1L, dto);

        assertEquals(2L, out.getId());
        assertEquals(3, target.getItems().size());
        BillItem fries = target.getItems().stream().filter(i -> i.getMenuItemId() == 1L).findFirst().orElseThrow();
        assertEquals(3, fries.getQuantity());
        assertEquals(2, fries.getPrintedQuantity());
        assertEquals(0, new BigDecimal("300.00").compareTo(fries.getSubtotal()));
        assertEquals(0, new BigDecimal("360.00").compareTo(target.getSubtotal()));
        assertEquals(Bill.Status.KOT_PRINTED, target.getStatus());
        assertEquals(Bill.Status.CANCELLED, source.getStatus());
        assertTrue(source.getVoidReason().contains("B2"));
        verify(recipeService).transferConsumption(1L, 2L);
    }

    @Test
    @DisplayName("Merge is refused while a KOT is waiting to print on either order")
    void mergeBlockedWhileKotPending() {
        tableBill(1, "T-01", Bill.Status.KOT_REQUESTED, item(1L, "Fries", 1, 0, "100"));
        Bill target = tableBill(2, "T-02", Bill.Status.ACTIVE, item(2L, "Tea", 1, 0, "20"));
        when(billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(eq("T-02"), any())).thenReturn(Optional.of(target));
        ChangeLocationRequestDTO dto = toTable("T-02");
        dto.setMergeIfOccupied(true);
        assertThrows(IllegalStateException.class, () -> billService.changeOrderLocation(1L, dto));
        verify(recipeService, never()).transferConsumption(anyLong(), anyLong());
    }
}
