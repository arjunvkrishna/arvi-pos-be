package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponDTO {
    private Long id;
    private String code;
    private String description;
    private String discountType; // PERCENTAGE, FIXED_AMOUNT
    private BigDecimal discountValue;
    private BigDecimal minOrderValue;
    private BigDecimal maxDiscountAmount;
    private Integer usageLimit;
    private Integer usageLimitPerCustomer;
    private Integer usedCount;
    private Instant validFrom;
    private Instant validUntil;
    private Boolean isActive;
    private Long createdBy;
    private Instant createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidationResponse {
        private boolean valid;
        private Long couponId;
        private String code;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal calculatedDiscount;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidateRequest {
        private String code;
        private Long customerId;
        private BigDecimal orderAmount;
    }
}
