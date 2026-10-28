package com.neram.pos.service;

import com.neram.pos.dto.CouponDTO;
import com.neram.pos.entity.Coupon;
import com.neram.pos.entity.CouponUsage;
import com.neram.pos.repository.CouponRepository;
import com.neram.pos.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    @Transactional(readOnly = true)
    public List<CouponDTO> getAllCoupons() {
        return couponRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CouponDTO getCouponById(Long id) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found with id: " + id));
        return toDTO(c);
    }

    @Transactional
    public CouponDTO createCoupon(CouponDTO dto, Long userId) {
        if (dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Coupon code is required.");
        }
        String code = dto.getCode().trim().toUpperCase();
        if (couponRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new IllegalArgumentException("Coupon with code '" + code + "' already exists.");
        }
        if (dto.getDiscountType() == null || (!dto.getDiscountType().equalsIgnoreCase("PERCENTAGE") && !dto.getDiscountType().equalsIgnoreCase("FIXED_AMOUNT"))) {
            throw new IllegalArgumentException("Discount type must be PERCENTAGE or FIXED_AMOUNT.");
        }
        if (dto.getDiscountValue() == null || dto.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero.");
        }
        if ("PERCENTAGE".equalsIgnoreCase(dto.getDiscountType()) && dto.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100%.");
        }

        Coupon c = Coupon.builder()
                .code(code)
                .description(dto.getDescription())
                .discountType(dto.getDiscountType().toUpperCase())
                .discountValue(dto.getDiscountValue())
                .minOrderValue(dto.getMinOrderValue() != null ? dto.getMinOrderValue() : BigDecimal.ZERO)
                .maxDiscountAmount(dto.getMaxDiscountAmount())
                .usageLimit(dto.getUsageLimit())
                .usageLimitPerCustomer(dto.getUsageLimitPerCustomer() != null ? dto.getUsageLimitPerCustomer() : 1)
                .usedCount(0)
                .validFrom(dto.getValidFrom() != null ? dto.getValidFrom() : Instant.now())
                .validUntil(dto.getValidUntil())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        return toDTO(couponRepository.save(c));
    }

    @Transactional
    public CouponDTO updateCoupon(Long id, CouponDTO dto) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found with id: " + id));

        if (dto.getDescription() != null) c.setDescription(dto.getDescription());
        if (dto.getMinOrderValue() != null) c.setMinOrderValue(dto.getMinOrderValue());
        if (dto.getMaxDiscountAmount() != null) c.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        if (dto.getUsageLimit() != null) c.setUsageLimit(dto.getUsageLimit());
        if (dto.getUsageLimitPerCustomer() != null) c.setUsageLimitPerCustomer(dto.getUsageLimitPerCustomer());
        if (dto.getValidFrom() != null) c.setValidFrom(dto.getValidFrom());
        if (dto.getValidUntil() != null) c.setValidUntil(dto.getValidUntil());
        if (dto.getIsActive() != null) c.setIsActive(dto.getIsActive());

        return toDTO(couponRepository.save(c));
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public CouponDTO.ValidationResponse validateCoupon(String rawCode, Long customerId, BigDecimal orderAmount) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return CouponDTO.ValidationResponse.builder().valid(false).message("Coupon code cannot be empty.").build();
        }
        String code = rawCode.trim().toUpperCase();
        Coupon c = couponRepository.findByCodeIgnoreCase(code).orElse(null);
        if (c == null) {
            return CouponDTO.ValidationResponse.builder().valid(false).code(code).message("Invalid coupon code.").build();
        }

        Instant now = Instant.now();
        if (!Boolean.TRUE.equals(c.getIsActive())) {
            return CouponDTO.ValidationResponse.builder().valid(false).code(code).message("This coupon is no longer active.").build();
        }
        if (c.getValidFrom().isAfter(now)) {
            return CouponDTO.ValidationResponse.builder().valid(false).code(code).message("This coupon is not yet valid.").build();
        }
        if (c.getValidUntil() != null && c.getValidUntil().isBefore(now)) {
            return CouponDTO.ValidationResponse.builder().valid(false).code(code).message("This coupon has expired.").build();
        }
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) {
            return CouponDTO.ValidationResponse.builder().valid(false).code(code).message("Coupon usage limit has been reached.").build();
        }

        BigDecimal amount = orderAmount != null ? orderAmount : BigDecimal.ZERO;
        if (c.getMinOrderValue() != null && amount.compareTo(c.getMinOrderValue()) < 0) {
            return CouponDTO.ValidationResponse.builder().valid(false).code(code)
                    .message(String.format("Minimum order amount of ₹%.2f required to use this coupon.", c.getMinOrderValue()))
                    .build();
        }

        if (c.getUsageLimitPerCustomer() != null) {
            if (customerId == null) {
                return CouponDTO.ValidationResponse.builder().valid(false).code(code)
                        .message("Customer phone or profile required for this coupon.").build();
            }
            long usedByCust = couponUsageRepository.countByCouponIdAndCustomerId(c.getId(), customerId);
            if (usedByCust >= c.getUsageLimitPerCustomer()) {
                return CouponDTO.ValidationResponse.builder().valid(false).code(code)
                        .message("You have reached the maximum usage limit for this coupon.").build();
            }
        }

        BigDecimal discount = calculateDiscount(c, amount);

        return CouponDTO.ValidationResponse.builder()
                .valid(true)
                .couponId(c.getId())
                .code(c.getCode())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .calculatedDiscount(discount)
                .message("Coupon applied successfully!")
                .build();
    }

    @Transactional
    public BigDecimal applyCouponSafely(String rawCode, Long customerId, Long billId, BigDecimal orderAmount) {
        String code = rawCode != null ? rawCode.trim().toUpperCase() : null;
        if (code == null || billId == null) return BigDecimal.ZERO;

        Coupon c = couponRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid coupon code: " + code));

        Instant now = Instant.now();
        if (!Boolean.TRUE.equals(c.getIsActive())) {
            throw new IllegalArgumentException("This coupon is no longer active.");
        }
        if (c.getValidFrom().isAfter(now) || (c.getValidUntil() != null && c.getValidUntil().isBefore(now))) {
            throw new IllegalArgumentException("Coupon is expired or not yet valid.");
        }
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) {
            throw new IllegalArgumentException("Coupon usage limit has been reached.");
        }

        BigDecimal amount = orderAmount != null ? orderAmount : BigDecimal.ZERO;
        if (c.getMinOrderValue() != null && amount.compareTo(c.getMinOrderValue()) < 0) {
            throw new IllegalArgumentException(String.format("Minimum order value of ₹%.2f required.", c.getMinOrderValue()));
        }

        if (c.getUsageLimitPerCustomer() != null) {
            if (customerId == null) {
                throw new IllegalArgumentException("Customer profile required for this coupon.");
            }
            long used = couponUsageRepository.countByCouponIdAndCustomerId(c.getId(), customerId);
            if (used >= c.getUsageLimitPerCustomer()) {
                throw new IllegalArgumentException("Coupon usage limit per customer exceeded.");
            }
        }

        int updated = couponRepository.incrementUsedCountSafely(c.getId(), now);
        if (updated == 0) {
            throw new IllegalArgumentException("Coupon usage limit reached or coupon inactive.");
        }

        BigDecimal discount = calculateDiscount(c, amount);

        CouponUsage usage = CouponUsage.builder()
                .couponId(c.getId())
                .customerId(customerId)
                .billId(billId)
                .discountApplied(discount)
                .usedAt(now)
                .build();
        couponUsageRepository.save(usage);

        return discount;
    }

    @Transactional
    public void revertCouponForBill(Long billId) {
        if (billId == null) return;
        couponUsageRepository.findByBillId(billId).ifPresent(usage -> {
            couponRepository.decrementUsedCountSafely(usage.getCouponId());
            couponUsageRepository.delete(usage);
            log.info("Reverted coupon {} usage for bill {}", usage.getCouponId(), billId);
        });
    }

    private BigDecimal calculateDiscount(Coupon c, BigDecimal amount) {
        BigDecimal discount;
        if ("PERCENTAGE".equalsIgnoreCase(c.getDiscountType())) {
            discount = amount.multiply(c.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (c.getMaxDiscountAmount() != null && discount.compareTo(c.getMaxDiscountAmount()) > 0) {
                discount = c.getMaxDiscountAmount();
            }
        } else {
            discount = c.getDiscountValue().min(amount);
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    public CouponDTO toDTO(Coupon c) {
        return CouponDTO.builder()
                .id(c.getId())
                .code(c.getCode())
                .description(c.getDescription())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .minOrderValue(c.getMinOrderValue())
                .maxDiscountAmount(c.getMaxDiscountAmount())
                .usageLimit(c.getUsageLimit())
                .usageLimitPerCustomer(c.getUsageLimitPerCustomer())
                .usedCount(c.getUsedCount())
                .validFrom(c.getValidFrom())
                .validUntil(c.getValidUntil())
                .isActive(c.getIsActive())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
