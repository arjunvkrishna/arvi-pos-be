package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BillRefundDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefundRequest {
        private Long billId;
        private BigDecimal amount;
        private String paymentMode; // CASH, UPI, CARD
        private String reason;
        private String idempotencyKey;
        private String externalReference;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefundResponse {
        private Long id;
        private Long billId;
        private String billNumber;
        private BigDecimal amount;
        private String paymentMode;
        private String reason;
        private Long refundedBy;
        private String refundedByName;
        private LocalDateTime refundedAt;
        private String status;
        private String idempotencyKey;
        private String externalReference;
        private Long shiftId;
        private BigDecimal originalBillNetAmount;
        private BigDecimal remainingRefundableAmount;
    }
}
