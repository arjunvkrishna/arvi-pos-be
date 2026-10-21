package com.neram.pos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierInvoiceDTO {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private Double gstRate;
    private BigDecimal gstAmount;
    private BigDecimal otherCharges;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private String status;
    private String description;
    private String itemsReceived;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Boolean isOverdue;
    private List<SupplierPaymentDTO> payments;
    private List<SupplierInvoiceItemDTO> items;
}
