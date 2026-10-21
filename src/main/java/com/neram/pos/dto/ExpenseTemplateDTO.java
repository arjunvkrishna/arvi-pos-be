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
public class ExpenseTemplateDTO {
    private Long id;
    private String title;
    private String type;
    private BigDecimal amount;
    private String frequency;
    private Integer dueDay;
    private String vendorOrPayee;
    private String paymentMethod;
    private String notes;
    private Boolean isActive;
    private LocalDate lastGeneratedDate;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
