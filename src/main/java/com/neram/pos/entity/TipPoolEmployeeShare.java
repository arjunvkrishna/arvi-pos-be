package com.neram.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "tip_pool_employee_shares")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(employee_id IN (SELECT e.id FROM employees e WHERE e.outlet_id = :outletId OR e.outlet_id IS NULL))")
public class TipPoolEmployeeShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distribution_id", nullable = false)
    private TipPoolDistribution distribution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "hours_worked", nullable = false, precision = 6, scale = 2)
    private BigDecimal hoursWorked;

    @Column(name = "tip_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal tipAmount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean paid = false;

    @Column(name = "paid_at")
    private Instant paidAt;
}
