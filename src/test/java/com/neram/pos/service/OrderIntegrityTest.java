package com.neram.pos.service;

import com.neram.pos.dto.BillRequestDTO;
import com.neram.pos.dto.TableOrderRequestDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.GstSetting;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderIntegrityTest {

    @Mock private BillRepository billRepository;
    @Mock private BillItemRepository billItemRepository;
    @Mock private DiningTableRepository diningTableRepository;
    @Mock private UserRepository userRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Spy private MenuBranchService menuBranchService = MenuBranchStub.cafeWide();
    @Mock private AuditLogService auditLogService;
    @Mock private GstSettingService gstSettingService;
    @Mock private CashierShiftService cashierShiftService;
    @Mock private RecipeService recipeService;
    @Mock private BillRefundRepository billRefundRepository;
    @Mock private PromotionService promotionService;
    @InjectMocks private BillService billService;

    private MenuItem fries;

    @BeforeEach
    void setUp() {
        fries = new MenuItem();
        fries.setId(1L);
        fries.setName("Fries");
        fries.setPrice(BigDecimal.valueOf(180));
        fries.setIsAvailable(true);
        fries.setActive(true);
        when(diningTableRepository.existsByNameIgnoreCaseAndActiveTrue("T-01")).thenReturn(true);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(fries));
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        when(billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(gstSettingService.getSettings()).thenReturn(GstSetting.builder()
                .enabled(false).roundOffEnabled(false).cgstPercent(0.0).sgstPercent(0.0).build());
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    /** Asserts a rejection caused by the order rules, not by an unrelated precondition such as an unknown table. */
    private void assertRejected(org.junit.jupiter.api.function.Executable call, String messagePart) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
        assertTrue(e.getMessage().toLowerCase().contains(messagePart.toLowerCase()),
                "Expected message containing '" + messagePart + "' but was: " + e.getMessage());
    }

    private TableOrderRequestDTO tableOrder(Long menuItemId, Integer qty, BigDecimal price) {
        TableOrderRequestDTO dto = new TableOrderRequestDTO();
        dto.setTableNumber("T-01");
        TableOrderRequestDTO.OrderItemDTO it = new TableOrderRequestDTO.OrderItemDTO();
        it.setMenuItemId(menuItemId);
        it.setItemName("Fries");
        it.setQuantity(qty);
        it.setPrice(price);
        dto.setItems(List.of(it));
        return dto;
    }

    @Test
    @DisplayName("Staff cannot underprice: the menu price replaces whatever the client sends")
    void staffPriceComesFromMenu() {
        loginAs("EMPLOYEE");
        billService.saveOrUpdateTableOrder(tableOrder(1L, 2, BigDecimal.ONE));
        ArgumentCaptor<Bill> saved = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository, atLeastOnce()).save(saved.capture());
        Bill bill = saved.getValue();
        assertEquals(0, BigDecimal.valueOf(180).compareTo(bill.getItems().get(0).getPrice()));
        assertEquals(0, BigDecimal.valueOf(360).compareTo(bill.getSubtotal()));
    }

    @Test
    @DisplayName("Zero, negative and absurd quantities are rejected")
    void badQuantitiesRejected() {
        loginAs("EMPLOYEE");
        for (int qty : new int[]{0, -2, 1000}) {
            assertRejected(() -> billService.saveOrUpdateTableOrder(tableOrder(1L, qty, null)), "quantity");
        }
    }

    @Test
    @DisplayName("A new order must contain at least one item")
    void emptyNewOrderRejected() {
        loginAs("EMPLOYEE");
        TableOrderRequestDTO dto = tableOrder(1L, 1, null);
        dto.setItems(List.of());
        assertRejected(() -> billService.saveOrUpdateTableOrder(dto), "at least one item");
    }

    @Test
    @DisplayName("Unavailable dishes cannot be added to a new order")
    void unavailableDishRejected() {
        loginAs("EMPLOYEE");
        fries.setIsAvailable(false);
        assertRejected(() -> billService.saveOrUpdateTableOrder(tableOrder(1L, 1, null)), "unavailable");
    }

    @Test
    @DisplayName("Staff cannot add custom (non-menu) items; unknown menu ids are rejected")
    void customAndUnknownItemsRejected() {
        loginAs("EMPLOYEE");
        assertRejected(() -> billService.saveOrUpdateTableOrder(tableOrder(null, 1, BigDecimal.TEN)), "custom items");
        assertRejected(() -> billService.saveOrUpdateTableOrder(tableOrder(99L, 1, null)), "not found");
    }

    @Test
    @DisplayName("Discounts outside 0-100% are rejected even for managers")
    void discountOutOfRangeRejected() {
        loginAs("MANAGER");
        for (double pct : new double[]{150.0, -5.0}) {
            TableOrderRequestDTO dto = tableOrder(1L, 1, null);
            dto.setDiscountPercent(pct);
            assertRejected(() -> billService.saveOrUpdateTableOrder(dto), "discount");
        }
    }

    @Test
    @DisplayName("GST rates above 100% are rejected on a bill")
    void gstRateOutOfRangeRejected() {
        loginAs("MANAGER");
        TableOrderRequestDTO dto = tableOrder(1L, 1, null);
        dto.setGstEnabled(true);
        dto.setCgstPercent(2.5);
        dto.setSgstPercent(200.0);
        assertRejected(() -> billService.saveOrUpdateTableOrder(dto), "sgst");
    }

    @Test
    @DisplayName("Direct billing requires at least one item")
    void directBillRequiresItems() {
        loginAs("MANAGER");
        BillRequestDTO dto = new BillRequestDTO();
        dto.setTableNumber("T-01");
        dto.setOrderType("TABLE");
        dto.setItems(List.of());
        assertRejected(() -> billService.createBill(dto), "at least one item");
    }
}
