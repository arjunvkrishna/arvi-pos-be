package com.neram.pos.service;

import com.neram.pos.dto.PromotionRequestDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.GstSetting;
import com.neram.pos.entity.Promotion;
import com.neram.pos.repository.PromotionRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PromotionBillingTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PromotionService promotionService;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(PromotionService.ZONE_KOLKATA);
    }

    @Test
    void testPromotionPercentageValidation() {
        // Less than or equal to 0% -> rejected
        PromotionRequestDTO zeroPct = PromotionRequestDTO.builder()
                .name("Zero Discount")
                .discountPercentage(0.0)
                .startDateTime(now)
                .endDateTime(now.plusDays(1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> promotionService.createPromotion(zeroPct, 1L, "admin"));

        // Negative percentage -> rejected
        PromotionRequestDTO negPct = PromotionRequestDTO.builder()
                .name("Negative Discount")
                .discountPercentage(-10.0)
                .startDateTime(now)
                .endDateTime(now.plusDays(1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> promotionService.createPromotion(negPct, 1L, "admin"));

        // Greater than 100% -> rejected
        PromotionRequestDTO overPct = PromotionRequestDTO.builder()
                .name("Over 100 Discount")
                .discountPercentage(105.0)
                .startDateTime(now)
                .endDateTime(now.plusDays(1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> promotionService.createPromotion(overPct, 1L, "admin"));
    }

    @Test
    void testPromotionDateValidation() {
        // End before start -> rejected
        PromotionRequestDTO invalidEnd = PromotionRequestDTO.builder()
                .name("Invalid Dates")
                .discountPercentage(10.0)
                .startDateTime(now.plusDays(2))
                .endDateTime(now.plusDays(1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> promotionService.createPromotion(invalidEnd, 1L, "admin"));

        // End equal to start -> rejected
        PromotionRequestDTO equalDates = PromotionRequestDTO.builder()
                .name("Equal Dates")
                .discountPercentage(10.0)
                .startDateTime(now)
                .endDateTime(now)
                .build();
        assertThrows(IllegalArgumentException.class, () -> promotionService.createPromotion(equalDates, 1L, "admin"));
    }

    @Test
    void testPromotionOverlapValidation() {
        LocalDateTime start1 = now.plusHours(1);
        LocalDateTime end1 = now.plusHours(5);

        Promotion existingActive = Promotion.builder()
                .id(1L)
                .name("Existing Campaign")
                .discountPercentage(10.0)
                .startDateTime(start1)
                .endDateTime(end1)
                .isActive(true)
                .build();

        when(promotionRepository.findByIsActiveTrue()).thenReturn(List.of(existingActive));

        // Overlapping promotion (start during existing) -> rejected
        PromotionRequestDTO overlapping = PromotionRequestDTO.builder()
                .name("Overlapping Campaign")
                .discountPercentage(15.0)
                .startDateTime(now.plusHours(3))
                .endDateTime(now.plusHours(7))
                .isActive(true)
                .build();
        assertThrows(IllegalArgumentException.class, () -> promotionService.createPromotion(overlapping, 1L, "admin"));

        // Exact boundary overlap (inclusive) -> rejected
        PromotionRequestDTO boundaryOverlap = PromotionRequestDTO.builder()
                .name("Boundary Overlap")
                .discountPercentage(15.0)
                .startDateTime(end1)
                .endDateTime(now.plusHours(8))
                .isActive(true)
                .build();
        assertThrows(IllegalArgumentException.class, () -> promotionService.createPromotion(boundaryOverlap, 1L, "admin"));

        // Inactive overlapping promotion -> allowed
        PromotionRequestDTO inactiveOverlapping = PromotionRequestDTO.builder()
                .name("Inactive Overlapping")
                .discountPercentage(15.0)
                .startDateTime(now.plusHours(3))
                .endDateTime(now.plusHours(7))
                .isActive(false)
                .build();
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(i -> {
            Promotion p = i.getArgument(0);
            p.setId(2L);
            return p;
        });
        assertDoesNotThrow(() -> promotionService.createPromotion(inactiveOverlapping, 1L, "admin"));
    }

    @Test
    void testPromotionCalculationOnTotalIncludingGst() {
        // Example: If total including GST is ₹100 and promotion is 5%, subtract ₹5 and charge ₹95
        BigDecimal subtotal = BigDecimal.valueOf(95.24);
        BigDecimal gstAmount = BigDecimal.valueOf(4.76);
        BigDecimal totalIncludingGst = subtotal.add(gstAmount); // 100.00
        assertEquals(new BigDecimal("100.00"), totalIncludingGst.setScale(2, RoundingMode.HALF_UP));

        double promoPct = 5.0;
        BigDecimal promoDiscount = totalIncludingGst.multiply(BigDecimal.valueOf(promoPct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("5.00"), promoDiscount);

        BigDecimal finalNet = totalIncludingGst.subtract(promoDiscount).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("95.00"), finalNet);
    }
}
