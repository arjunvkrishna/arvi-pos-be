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
public class SalaryDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Integer month;
    private Integer year;
    private BigDecimal basic;
    private BigDecimal allowances;
    private BigDecimal deductions;
    private BigDecimal netSalary;
    /** advances given so far against this salary */
    private BigDecimal advance;
    /** what is still to be paid: net salary minus advances */
    private BigDecimal balance;
    private java.util.List<SalaryAdvanceDTO> advances;
    private Boolean paid;
    private LocalDate paidDate;
    private String paymentMode;
    private String notes;
    private LocalDateTime createdAt;
}
