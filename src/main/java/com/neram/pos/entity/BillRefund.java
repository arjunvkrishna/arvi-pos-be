package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bill_refunds")
public class BillRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_mode", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Bill.PaymentMode paymentMode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "refunded_by")
    private Long refundedBy;

    @Column(name = "refunded_by_name", length = 100)
    private String refundedByName;

    @Column(name = "refunded_at", nullable = false)
    @Builder.Default
    private LocalDateTime refundedAt = LocalDateTime.now();

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RefundStatus status = RefundStatus.SUCCESS;

    @Column(name = "idempotency_key", unique = true, length = 150)
    private String idempotencyKey;

    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum RefundStatus {
        SUCCESS, PENDING, FAILED
    }
}
