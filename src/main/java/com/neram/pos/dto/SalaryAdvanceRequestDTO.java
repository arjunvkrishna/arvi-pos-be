package com.neram.pos.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalaryAdvanceRequestDTO {
    private Long employeeId;
    private Integer month;
    private Integer year;
    private BigDecimal amount;
    private String note;
}
