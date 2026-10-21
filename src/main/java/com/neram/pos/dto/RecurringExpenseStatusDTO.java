package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpenseStatusDTO {
    private Long templateId;
    private String title;
    private String type;
    private BigDecimal expectedAmount;
    private Integer dueDay;
    private String vendorOrPayee;
    private String paymentMethod;
    private String status; // 'PAID', 'PENDING', 'UNRECORDED', 'OVERDUE'
    private Boolean isRecorded;
    private Long expenseId;
    private LocalDate expenseDate;
    private BigDecimal recordedAmount;
}
