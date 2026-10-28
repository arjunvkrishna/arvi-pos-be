package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipPoolCalculateRequestDTO {
    private LocalDate distributionDate;
    private BigDecimal overrideTotalTips;
    private List<EmployeeHoursInput> customHours;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmployeeHoursInput {
        private Long employeeId;
        private BigDecimal hoursWorked;
    }
}
