package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Part of a month's salary handed over early. Balance to pay = net salary minus the advances. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "salary_advances")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(salary_id IN (SELECT s.id FROM salaries s WHERE s.employee_id IN (SELECT e.id FROM employees e WHERE e.outlet_id = :outletId OR e.outlet_id IS NULL)))")
public class SalaryAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salary_id", nullable = false)
    private Long salaryId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "given_date", nullable = false)
    private LocalDate givenDate;

    @Column(name = "given_by")
    private String givenBy;

    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
