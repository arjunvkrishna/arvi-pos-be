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
@Table(name = "cafe_expenses")
public class CafeExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ExpenseType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    private Integer month;
    private Integer year;

    @Column(name = "paid_by")
    private String paidBy;

    @Column(name = "vendor_or_payee")
    private String vendorOrPayee;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(nullable = false)
    private String status = "PAID";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    @Column(name = "receipt_ref")
    private String receiptRef;

    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ExpenseType {
        RENT, WATER, ELECTRICITY, EMI, INTERNET, GAS, MAINTENANCE, MARKETING, MISC
    }
}
