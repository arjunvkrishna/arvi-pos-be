package com.neram.pos.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SupplierPaymentRequestDTO {
    private Long invoiceId;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private String paymentMode;
    private String referenceNumber;
    private String notes;
}
