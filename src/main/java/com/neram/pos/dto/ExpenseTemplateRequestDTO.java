package com.neram.pos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ExpenseTemplateRequestDTO {
    private String title;
    private String type;
    private BigDecimal amount;
    private String frequency;
    private Integer dueDay;
    private String vendorOrPayee;
    private String paymentMethod;
    private String notes;
    private Boolean isActive;
}
