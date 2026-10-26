package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "salaries")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(employee_id IN (SELECT p.id FROM employees p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
public class Salary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal basic = java.math.BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal allowances = java.math.BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal deductions = java.math.BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal netSalary = java.math.BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean paid = false;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_mode")
    private String paymentMode = "CASH";

    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
