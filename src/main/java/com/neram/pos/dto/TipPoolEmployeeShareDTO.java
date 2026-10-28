package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipPoolEmployeeShareDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private BigDecimal hoursWorked;
    private BigDecimal tipAmount;
    private Boolean paid;
    private Instant paidAt;
}
