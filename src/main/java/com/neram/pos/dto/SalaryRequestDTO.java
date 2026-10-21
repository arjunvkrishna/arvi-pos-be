package com.neram.pos.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalaryRequestDTO {
    private Long employeeId;
    private Integer month;
    private Integer year;
    private BigDecimal basic;
    private BigDecimal allowances;
    private BigDecimal deductions;
    private Boolean paid;
    private LocalDate paidDate;
    private String paymentMode;
    private String notes;
}
