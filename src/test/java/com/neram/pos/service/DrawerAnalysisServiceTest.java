package com.neram.pos.service;

import com.neram.pos.dto.CashierShiftDTO;
import com.neram.pos.entity.CashierShift;
import com.neram.pos.entity.ShiftSetting;
import com.neram.pos.entity.User;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrawerAnalysisServiceTest {

    @Mock
    private CashierShiftRepository shiftRepository;

    @Mock
    private CashMovementRepository movementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ShiftSettingRepository shiftSettingRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private BillRefundRepository billRefundRepository;

    @Mock
    private CafeExpenseRepository cafeExpenseRepository;

    @InjectMocks
    private CashierShiftService shiftService;

    @BeforeEach
    void setUp() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");
        when(authentication.getPrincipal()).thenReturn("admin");
        SecurityContextHolder.setContext(securityContext);

        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRole(User.Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
    }

    @Test
    @DisplayName("1. Traceable Reconciliation Formula includes Float, Sales, In, Refunds, Out, and Transfers")
    void testCalculateExpectedCashReconciliationFormula() {
        CashierShift shift = CashierShift.builder()
                .id(1L)
                .openingFloat(new BigDecimal("10000.00"))
                .cashSales(new BigDecimal("15000.00"))
                .cashInTotal(new BigDecimal("500.00"))
                .cashRefunds(new BigDecimal("1000.00"))
                .cashOutTotal(new BigDecimal("5000.00"))
                .transfersOutTotal(new BigDecimal("10000.00"))
                .transfersInTotal(new BigDecimal("2000.00"))
                .status(CashierShift.ShiftStatus.OPEN)
                .build();

        // Formula: 10000 + 15000 + 500 - 1000 - 5000 - 10000 + 2000 = 11,500.00
        BigDecimal expectedCash = shiftService.calculateExpectedCash(shift);
        assertEquals(new BigDecimal("11500.00"), expectedCash);
    }

    @Test
    @DisplayName("2. Card and UPI sales/refunds are isolated and NEVER added to physical drawer cash")
    void testCardAndUpiSalesDoNotAffectPhysicalDrawerCash() {
        CashierShift shift = CashierShift.builder()
                .id(2L)
                .openingFloat(new BigDecimal("5000.00"))
                .cashSales(new BigDecimal("2000.00"))
                .upiSales(new BigDecimal("25000.00"))
                .cardSales(new BigDecimal("18000.00"))
                .cashRefunds(new BigDecimal("0.00"))
                .upiRefunds(new BigDecimal("1500.00"))
                .cardRefunds(new BigDecimal("700.00"))
                .cashInTotal(BigDecimal.ZERO)
                .cashOutTotal(BigDecimal.ZERO)
                .transfersOutTotal(BigDecimal.ZERO)
                .transfersInTotal(BigDecimal.ZERO)
                .status(CashierShift.ShiftStatus.OPEN)
                .build();

        // Only opening float (5,000) + cash sales (2,000) belong to drawer = 7,000.00
        BigDecimal expectedCash = shiftService.calculateExpectedCash(shift);
        assertEquals(new BigDecimal("7000.00"), expectedCash);
    }

    @Test
    @DisplayName("3. Drawer to Bank deposit reduces expected cash without treating transfer as an expense")
    void testDrawerToBankTransferReducesExpectedCash() {
        CashierShift shift = CashierShift.builder()
                .id(3L)
                .openingFloat(new BigDecimal("10000.00"))
                .cashSales(new BigDecimal("15000.00"))
                .cashOutTotal(new BigDecimal("5000.00")) // Petty cash paid
                .transfersOutTotal(BigDecimal.ZERO)
                .transfersInTotal(BigDecimal.ZERO)
                .cashInTotal(BigDecimal.ZERO)
                .cashRefunds(BigDecimal.ZERO)
                .status(CashierShift.ShiftStatus.OPEN)
                .build();

        // Before deposit: 10,000 + 15,000 - 5,000 = 20,000.00
        BigDecimal beforeDeposit = shiftService.calculateExpectedCash(shift);
        assertEquals(new BigDecimal("20000.00"), beforeDeposit);

        // After ₹10,000 deposit to bank: transfersOutTotal becomes 10,000.00
        shift.setTransfersOutTotal(new BigDecimal("10000.00"));
        BigDecimal afterDeposit = shiftService.calculateExpectedCash(shift);
        assertEquals(new BigDecimal("10000.00"), afterDeposit);
    }

    @Test
    @DisplayName("4. Explicit variance correction preserves original figures and creates audit trail")
    void testExplicitVarianceCorrectionPreservesOriginalRecord() {
        User managerUser = new User();
        managerUser.setId(10L);
        managerUser.setUsername("manager1");
        managerUser.setRole(User.Role.MANAGER);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(managerUser));

        CashierShift closedShift = CashierShift.builder()
                .id(4L)
                .openingFloat(new BigDecimal("10000.00"))
                .cashSales(new BigDecimal("5000.00"))
                .expectedCash(new BigDecimal("15000.00"))
                .countedCash(new BigDecimal("13000.00"))
                .variance(new BigDecimal("-2000.00")) // Shortage
                .status(CashierShift.ShiftStatus.CLOSED)
                .closedAt(LocalDateTime.now())
                .isCorrected(false)
                .build();

        when(shiftRepository.findById(4L)).thenReturn(Optional.of(closedShift));
        when(shiftRepository.save(any(CashierShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));
        SecurityContext sec = SecurityContextHolder.createEmptyContext();
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("manager1");
        when(auth.getPrincipal()).thenReturn("manager1");
        sec.setAuthentication(auth);
        SecurityContextHolder.setContext(sec);

        CashierShiftDTO.CorrectVarianceRequest correctReq = CashierShiftDTO.CorrectVarianceRequest.builder()
                .countedCash(new BigDecimal("14800.00"))
                .reason("Count recounts found ₹1,800 misplaced in drawer envelope")
                .build();

        CashierShiftDTO.ShiftResponse res = shiftService.correctVariance(4L, correctReq);

        // Verifies original figures preserved
        assertEquals(new BigDecimal("13000.00"), closedShift.getCountedCash());
        assertEquals(new BigDecimal("-2000.00"), closedShift.getVariance());

        // Verifies correction audit trail recorded
        assertTrue(closedShift.getIsCorrected());
        assertEquals(new BigDecimal("14800.00"), closedShift.getCorrectedCountedCash());
        assertEquals(new BigDecimal("-200.00"), closedShift.getCorrectedVariance());
        assertEquals("Count recounts found ₹1,800 misplaced in drawer envelope", closedShift.getCorrectionReason());
        assertEquals(10L, closedShift.getCorrectedByUserId());
        assertEquals("manager1", closedShift.getCorrectedByUserName());
        assertNotNull(closedShift.getCorrectedAt());

        verify(auditLogService, times(1)).log(eq("CORRECT_SHIFT_VARIANCE"), eq("SHIFT"), eq(4L), any(), any());
    }
}
