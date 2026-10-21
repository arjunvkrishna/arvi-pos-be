package com.neram.pos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPaymentDTO {
    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private Long supplierId;
    private String supplierName;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private String paymentMode;
    private String referenceNumber;
    private String notes;
    private Long createdBy;
    private LocalDateTime createdAt;
}
