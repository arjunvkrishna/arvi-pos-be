package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CafeExpenseDTO {
    private Long id;
    private String type;
    private String description;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private Integer month;
    private Integer year;
    private String paidBy;
    private String vendorOrPayee;
    private String paymentMethod;
    private String status;
    private LocalDate dueDate;
    private Long templateId;
    private Boolean isRecurring;
    private String receiptRef;
    private String notes;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
}
