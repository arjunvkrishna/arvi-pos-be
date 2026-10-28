package com.neram.pos.service;

import com.neram.pos.dto.CouponDTO;
import com.neram.pos.entity.Coupon;
import com.neram.pos.entity.CouponUsage;
import com.neram.pos.repository.CouponRepository;
import com.neram.pos.repository.CouponUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponServiceTest {
    @Mock private CouponRepository coupons;
    @Mock private CouponUsageRepository usages;

    private CouponService svc;
    private Coupon welcome;

    @BeforeEach
    void setUp() {
        svc = new CouponService(coupons, usages);
        welcome = Coupon.builder().id(4L).code("WELCOME10").discountType("PERCENTAGE").discountValue(new BigDecimal("10"))
                .minOrderValue(new BigDecimal("200")).maxDiscountAmount(new BigDecimal("50")).usageLimitPerCustomer(1)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS)).validUntil(Instant.now().plus(30, ChronoUnit.DAYS)).build();
        when(coupons.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(welcome));
        when(coupons.findByCodeForUpdate("WELCOME10")).thenReturn(Optional.of(welcome));
        when(coupons.incrementUsedCountSafely(eq(4L), any())).thenReturn(1);
    }

    @Test
    void percentageDiscountIsCappedAtTheMaximum() {
        CouponDTO.ValidationResponse r = svc.validateCoupon("welcome10", 1L, new BigDecimal("1000"));
        assertTrue(r.isValid());
        assertEquals(0, new BigDecimal("50.00").compareTo(r.getCalculatedDiscount()));
    }

    @Test
    void fixedDiscountNeverExceedsTheOrder() {
        welcome.setDiscountType("FIXED_AMOUNT");
        welcome.setDiscountValue(new BigDecimal("500"));
        welcome.setMinOrderValue(BigDecimal.ZERO);
        CouponDTO.ValidationResponse r = svc.validateCoupon("WELCOME10", 1L, new BigDecimal("120"));
        assertEquals(0, new BigDecimal("120.00").compareTo(r.getCalculatedDiscount()));
    }

    @Test
    void rejectsBelowMinimumExpiredInactiveAndExhausted() {
        assertFalse(svc.validateCoupon("WELCOME10", 1L, new BigDecimal("100")).isValid());

        welcome.setValidUntil(Instant.now().minusSeconds(5));
        assertFalse(svc.validateCoupon("WELCOME10", 1L, new BigDecimal("500")).isValid());

        welcome.setValidUntil(null);
        welcome.setIsActive(false);
        assertFalse(svc.validateCoupon("WELCOME10", 1L, new BigDecimal("500")).isValid());

        welcome.setIsActive(true);
        welcome.setUsageLimit(10);
        welcome.setUsedCount(10);
        assertFalse(svc.validateCoupon("WELCOME10", 1L, new BigDecimal("500")).isValid());
    }

    @Test
    void perCustomerLimitNeedsACustomerAndIsEnforced() {
        assertFalse(svc.validateCoupon("WELCOME10", null, new BigDecimal("500")).isValid());

        when(usages.countByCouponIdAndCustomerId(4L, 9L)).thenReturn(1L);
        assertFalse(svc.validateCoupon("WELCOME10", 9L, new BigDecimal("500")).isValid());
        assertThrows(IllegalArgumentException.class, () -> svc.applyCouponSafely("WELCOME10", 9L, 100L, new BigDecimal("500")));
        verify(usages, never()).save(any());
    }

    @Test
    void applyIncrementsAtomicallyAndRecordsTheUsage() {
        BigDecimal d = svc.applyCouponSafely("welcome10", 9L, 100L, new BigDecimal("300"));
        assertEquals(0, new BigDecimal("30.00").compareTo(d));
        verify(usages).save(argThat((CouponUsage u) -> u.getBillId() == 100L && u.getCouponId() == 4L));
    }

    @Test
    void applyFailsWhenTheAtomicIncrementLosesTheRace() {
        when(coupons.incrementUsedCountSafely(eq(4L), any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> svc.applyCouponSafely("WELCOME10", 9L, 100L, new BigDecimal("300")));
        verify(usages, never()).save(any());
    }

    @Test
    void revertingABillFreesTheCouponUse() {
        CouponUsage u = CouponUsage.builder().couponId(4L).billId(100L).build();
        when(usages.findByBillId(100L)).thenReturn(Optional.of(u));
        svc.revertCouponForBill(100L);
        verify(coupons).decrementUsedCountSafely(4L);
        verify(usages).delete(u);
    }
}
