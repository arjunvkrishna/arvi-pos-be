package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipPoolDistributionDTO {
    private Long id;
    private LocalDate distributionDate;
    private BigDecimal totalTips;
    private BigDecimal totalHours;
    private BigDecimal ratePerHour;
    private Long createdBy;
    private Instant createdAt;
    private List<TipPoolEmployeeShareDTO> shares;
}
