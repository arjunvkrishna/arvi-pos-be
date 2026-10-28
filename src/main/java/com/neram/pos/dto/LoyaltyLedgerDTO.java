package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyLedgerDTO {
    private Long id;
    private Long customerId;
    private Long billId;
    private String type;
    private Integer points;
    private Integer balanceAfter;
    private String note;
    private LocalDateTime createdAt;
}
