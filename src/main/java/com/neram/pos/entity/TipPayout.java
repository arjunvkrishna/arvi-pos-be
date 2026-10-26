package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Tips handed over to a staff member. Owed tips = tips received on their bills minus payouts. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tip_payouts")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(staff_id IN (SELECT p.id FROM employees p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
public class TipPayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "staff_name", nullable = false, length = 100)
    private String staffName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(length = 200)
    private String note;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
