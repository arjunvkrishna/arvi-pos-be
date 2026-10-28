package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "gift_cards")
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "initial_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialBalance;

    @Column(name = "current_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentBalance;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "payment_mode", nullable = false, length = 50)
    private String paymentMode;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_to_customer_id")
    private Long issuedToCustomerId;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status {
        ACTIVE,
        EXHAUSTED,
        VOIDED
    }
}
