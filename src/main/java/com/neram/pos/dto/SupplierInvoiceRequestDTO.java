package com.neram.pos.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class SupplierInvoiceRequestDTO {
    private Long supplierId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private Double gstRate;
    private BigDecimal gstAmount;
    private BigDecimal otherCharges;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private String description;
    private String itemsReceived;
    private List<SupplierInvoiceItemDTO> items;
}

