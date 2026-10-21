package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionDTO {
    private Long id;
    private Long itemId;
    private String itemName;
    private String unit;
    private String itemCategory;
    private String transactionType; // STOCK_IN, STOCK_OUT, ADJUSTMENT
    private Double quantity;
    private Double previousStock;
    private Double newStock;
    private Long userId;
    private String userName;
    private String userRole;
    private Long supplierId;
    private String supplierName;
    private Long invoiceId;
    private String invoiceReference;
    private java.math.BigDecimal unitPrice;
    private Double unitVolume;
    private String volumeUnit;
    private Double totalVolume;
    private java.math.BigDecimal costPerVolume;
    private String notes;
    private LocalDateTime createdAt;
}
