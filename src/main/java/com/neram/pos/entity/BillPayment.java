package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One part of a bill that was paid in more than one way (for example cash plus UPI). Bills paid one way have no rows here. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_payments")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(bill_id IN (SELECT p.id FROM bills p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
public class BillPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(nullable = false, length = 10)
    private String mode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
