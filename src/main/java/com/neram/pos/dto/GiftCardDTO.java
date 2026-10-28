package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCardDTO {
    private Long id;
    private String code;
    private BigDecimal initialBalance;
    private BigDecimal currentBalance;
    private String status;
    private String paymentMode;
    private Long issuedToCustomerId;
    private String issuedToCustomerName;
    private LocalDate expiresAt;
    private LocalDateTime createdAt;
}
