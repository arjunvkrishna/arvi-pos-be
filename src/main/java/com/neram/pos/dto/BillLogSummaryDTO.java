package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Totals shown above the Bill Log for the bills that match its filters (soft-deleted bills are never counted). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillLogSummaryDTO {
    private long billCount;
    /** Paid and partly refunded bills: net amount minus what was refunded. */
    private BigDecimal revenue;
    private BigDecimal average;
}
