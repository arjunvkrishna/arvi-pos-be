package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryAdvanceDTO {
    private Long id;
    private BigDecimal amount;
    private LocalDate givenDate;
    private String givenBy;
    private String note;
}
