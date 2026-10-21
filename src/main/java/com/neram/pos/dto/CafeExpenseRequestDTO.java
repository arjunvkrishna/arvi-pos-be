package com.neram.pos.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CafeExpenseRequestDTO {
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
}
