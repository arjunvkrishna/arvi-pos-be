package com.neram.pos.service;

import com.neram.pos.dto.BillItemResponseDTO;
import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.dto.TableOrderRequestDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.GstSetting;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regression: dishes saved after the last KOT but before "send to kitchen" must all appear on the next KOT. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KotCumulativeChangesTest {

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
    @InjectMocks private BillService billService;

    private Bill bill;
    private MenuItem burger, shake, juice, fries;

    private MenuItem dish(long id, String name, double price) {
        MenuItem m = new MenuItem();
        m.setId(id);
        m.setName(name);
        m.setPrice(BigDecimal.valueOf(price));
        m.setIsAvailable(true);
        m.setActive(true);
        when(menuItemRepository.findById(id)).thenReturn(Optional.of(m));
        return m;
    }

    @BeforeEach
    void setUp() {
        burger = dish(1, "Burger", 150);
        shake = dish(2, "Shake", 120);
        juice = dish(3, "Juice", 90);
        fries = dish(4, "Fries", 80);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("waiter", "x", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        // An order whose first KOT (burger) was already printed.
        bill = new Bill();
        bill.setId(500L);
        bill.setBillNumber("NRM-1");
        bill.setKotNumber("NRM-1");
        bill.setTableNumber("T-01");
        bill.setOrderType(Bill.OrderType.TABLE);
        bill.setStatus(Bill.Status.KOT_PRINTED);
        bill.setKotPrintedAt(LocalDateTime.now().minusMinutes(5));
        bill.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        BillItem bi = new BillItem();
        bi.setId(1L);
        bi.setBill(bill);
        bi.setMenuItemId(1L);
        bi.setItemName("Burger");
        bi.setQuantity(1);
        bi.setPrintedQuantity(1);
        bi.setPrice(BigDecimal.valueOf(150));
        bi.setSubtotal(BigDecimal.valueOf(150));
        bi.setIsTakeaway(false);
        List<BillItem> items = new ArrayList<>();
        items.add(bi);
        bill.setItems(items);
        bill.setSubtotal(BigDecimal.valueOf(150));

        when(diningTableRepository.existsByNameIgnoreCaseAndActiveTrue("T-01")).thenReturn(true);
        when(billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.of(bill));
        when(billRepository.findById(500L)).thenReturn(Optional.of(bill));
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        when(billRefundRepository.sumSuccessfulRefundsByBillId(any())).thenReturn(BigDecimal.ZERO);
        when(gstSettingService.getSettings()).thenReturn(GstSetting.builder()
                .enabled(false).roundOffEnabled(false).cgstPercent(0.0).sgstPercent(0.0).build());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void save(MenuItem... dishes) {
        TableOrderRequestDTO dto = new TableOrderRequestDTO();
        dto.setTableNumber("T-01");
        List<TableOrderRequestDTO.OrderItemDTO> lines = new ArrayList<>();
        for (MenuItem m : dishes) {
            TableOrderRequestDTO.OrderItemDTO it = new TableOrderRequestDTO.OrderItemDTO();
            it.setMenuItemId(m.getId());
            it.setItemName(m.getName());
            it.setQuantity(m == burger && dishes.length > 3 ? 1 : 1);
            lines.add(it);
        }
        dto.setItems(lines);
        billService.saveOrUpdateTableOrder(dto);
    }

    private List<String> printedNow() {
        BillResponseDTO out = billService.printKitchenOrder(500L);
        return out.getLatestKotItems().stream().map(BillItemResponseDTO::getItemName).collect(Collectors.toList());
    }

    @Test
    @DisplayName("Save shake, then save juice, then send: the KOT lists BOTH shake and juice, not the burger already printed")
    void bothUnprintedDishesAppearOnTheNextKot() {
        save(burger, shake);
        save(burger, shake, juice);
        assertTrue(bill.getKotLastChanges().contains("ADDED: 1x Shake"), bill.getKotLastChanges());
        assertTrue(bill.getKotLastChanges().contains("ADDED: 1x Juice"), bill.getKotLastChanges());
        assertFalse(bill.getKotLastChanges().contains("Burger"));

        // The thermal printer builds its ticket from the same change log, so it receives both dishes too.
        List<String> thermalInput = BillService.parseKotChanges(bill.getKotLastChanges(), bill.getItems())
                .stream().map(BillItemResponseDTO::getItemName).sorted().collect(Collectors.toList());
        assertEquals(List.of("Juice", "Shake"), thermalInput);

        List<String> printed = printedNow();
        assertEquals(List.of("Shake", "Juice"), printed.stream().sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList()));
        assertTrue(bill.getItems().stream().allMatch(i -> i.getQuantity().equals(i.getPrintedQuantity())),
                "after printing, every dish is recorded as sent to the kitchen");
    }

    @Test
    @DisplayName("Several saves before printing are one KOT update; the next print cycle starts a new update number")
    void updateNumberAdvancesOncePerPrintCycle() {
        save(burger, shake);
        save(burger, shake, juice);
        assertEquals(1, bill.getKotUpdateCount());
        printedNow();
        save(burger, shake, juice, fries);
        assertEquals(2, bill.getKotUpdateCount());
        assertEquals(List.of("Fries"), printedNow());
    }

    @Test
    @DisplayName("A dish added and removed again before sending leaves nothing to print and no stale change line")
    void addedThenRemovedLeavesNothing() {
        save(burger, shake);
        save(burger);
        assertNull(bill.getKotLastChanges());
        assertTrue(bill.getItems().stream().allMatch(i -> i.getQuantity().equals(i.getPrintedQuantity())));
    }

    @Test
    @DisplayName("Quantity increase of an already printed dish still shows only the extra portion")
    void quantityIncreaseOfPrintedDish() {
        TableOrderRequestDTO dto = new TableOrderRequestDTO();
        dto.setTableNumber("T-01");
        TableOrderRequestDTO.OrderItemDTO it = new TableOrderRequestDTO.OrderItemDTO();
        it.setMenuItemId(1L);
        it.setItemName("Burger");
        it.setQuantity(3);
        dto.setItems(List.of(it));
        billService.saveOrUpdateTableOrder(dto);
        List<BillItemResponseDTO> items = billService.printKitchenOrder(500L).getLatestKotItems();
        assertEquals(1, items.size());
        assertEquals(2, items.get(0).getDeltaQuantity());
    }

    @Test
    @DisplayName("Legacy bill (KOT printed, no printed quantities recorded) keeps the old baseline instead of reprinting everything")
    void legacyBillWithoutPrintedQuantities() {
        bill.getItems().get(0).setPrintedQuantity(0);
        save(burger, shake);
        assertEquals("[+] ADDED: 1x Shake", bill.getKotLastChanges());
    }
}
