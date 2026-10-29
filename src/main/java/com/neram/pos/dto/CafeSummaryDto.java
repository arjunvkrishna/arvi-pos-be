package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CafeSummaryDto {
    private Long id;
    private String name;
    private String code;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String subscriptionPlan;
    private String status;
    private Integer maxOutlets;
    /** Staff logins the plan allows. */
    private Integer maxUsers;
    private String dbName;
    private String schemaVersion;
    private String lastError;
    private Long outletsCount;
    private Long usersCount;
    private Long totalBillsCount;
    private BigDecimal totalGmv;
    private LocalDateTime createdAt;
}
