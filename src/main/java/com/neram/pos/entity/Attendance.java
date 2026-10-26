package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attendance")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(employee_id IN (SELECT p.id FROM employees p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private LocalDate date;

    @Convert(converter = com.neram.pos.config.LocalTimeAttributeConverter.class)
    @Column(name = "check_in")
    private LocalTime checkIn;

    @Convert(converter = com.neram.pos.config.LocalTimeAttributeConverter.class)
    @Column(name = "check_out")
    private LocalTime checkOut;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PRESENT;

    private String notes;

    @Column(name = "marked_by")
    private Long markedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status { PRESENT, ABSENT, HALF_DAY, LEAVE, HOLIDAY }
}
