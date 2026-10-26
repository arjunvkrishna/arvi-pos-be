package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Stock still on hand from one delivery, with its expiry (GET /api/inventory/expiry). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryBatchDTO {
    private Long itemId;
    private String itemName;
    private String unit;
    private String packName;
    private Double packSize;
    private LocalDate expiryDate;
    /** stock units still on hand from this delivery */
    private Double quantity;
    /** negative = already expired */
    private Long daysLeft;
    /** EXPIRED, SOON or OK */
    private String status;
    private Long invoiceId;
    private String invoiceNumber;
    private LocalDate receivedOn;
}
