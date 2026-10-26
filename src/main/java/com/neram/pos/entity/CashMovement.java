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
@Table(name = "cash_movements")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(shift_id IN (SELECT p.id FROM cashier_shifts p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "movement_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MovementType movementType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "transfer_type", length = 50)
    @Builder.Default
    private String transferType = "MANUAL";

    @Column(name = "destination_account", length = 150)
    private String destinationAccount;

    @Column(name = "reference_number", length = 150)
    private String referenceNumber;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum MovementType {
        CASH_IN, CASH_OUT
    }
}
