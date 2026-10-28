package com.neram.pos.entity;

import jakarta.persistence.*;
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
@Entity
@Table(name = "coupon_usages", uniqueConstraints = {
        @UniqueConstraint(name = "uq_coupon_bill", columnNames = {"coupon_id", "bill_id"})
})
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "discount_applied", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountApplied;

    @Column(name = "used_at", nullable = false)
    @Builder.Default
    private Instant usedAt = Instant.now();
}
