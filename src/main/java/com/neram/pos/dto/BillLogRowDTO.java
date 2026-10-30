package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the Bill Log (/bills/daily and /bills/monthly): only what the list shows and its action buttons need.
 * The full bill (items, tax split, KOT details) comes from GET /bills/{id} when a row is opened.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillLogRowDTO {
    private Long id;
    private String billNumber;
    private String orderType;
    private String tableNumber;
    private String outdoorToken;
    private String takeawayToken;
    private String customerName;
    private String createdByName;
    private String printedByName;
    private LocalDateTime createdAt;
    private int itemCount;
    private String paymentMode;
    /** "CASH ₹100.00 + UPI ₹200.00" when the bill was paid in more than one way; null otherwise */
    private String paymentSummary;
    private BigDecimal netAmount;
    private String status;
    private BigDecimal totalRefunded;
    private LocalDateTime deletedAt;
    private String deletedByName;
    private String deleteReason;
}
