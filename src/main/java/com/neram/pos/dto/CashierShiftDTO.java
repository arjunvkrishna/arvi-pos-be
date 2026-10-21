package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CashierShiftDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OpenShiftRequest {
        private String registerId;
        private BigDecimal openingFloat;
        private Long previousShiftId;
        private String carryForwardRef;
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CloseShiftRequest {
        private BigDecimal countedCash;
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CashMovementRequest {
        private String movementType; // CASH_IN, CASH_OUT
        private String transferType; // MANUAL_IN, PETTY_CASH, DRAWER_TO_BANK, etc.
        private BigDecimal amount;
        private String destinationAccount;
        private String referenceNumber;
        private String reason;
        private String approvedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferRequest {
        private BigDecimal amount;
        private String transferType; // DRAWER_TO_BANK, DRAWER_TO_SAFE, BANK_TO_DRAWER, SAFE_TO_DRAWER
        private String destinationAccount;
        private String referenceNumber;
        private String reason;
        private String approvedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CorrectVarianceRequest {
        private BigDecimal countedCash;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CashMovementResponse {
        private Long id;
        private Long shiftId;
        private String movementType;
        private String transferType;
        private BigDecimal amount;
        private String destinationAccount;
        private String referenceNumber;
        private String reason;
        private Long userId;
        private String userName;
        private String approvedBy;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftResponse {
        private Long id;
        private String registerId;
        private Long cashierId;
        private String cashierName;
        private LocalDateTime openedAt;
        private BigDecimal openingFloat;
        private String status;
        private LocalDateTime closedAt;
        private Long closingUserId;
        private String closingUserName;
        private BigDecimal countedCash;
        private BigDecimal expectedCash;
        private BigDecimal variance;
        private String notes;

        // Carry-forward & handover reference
        private Long previousShiftId;
        private String carryForwardRef;

        // Explicit correction audit trail
        private Boolean isCorrected;
        private BigDecimal correctedCountedCash;
        private BigDecimal correctedVariance;
        private String correctionReason;
        private Long correctedByUserId;
        private String correctedByUserName;
        private LocalDateTime correctedAt;

        // Sales and tender breakdowns
        private BigDecimal cashSales;
        private BigDecimal upiSales;
        private BigDecimal cardSales;
        private BigDecimal totalSales;

        // Drawer movement and refund totals
        private BigDecimal cashRefunds;
        private BigDecimal upiRefunds;
        private BigDecimal cardRefunds;
        private BigDecimal cashInTotal;
        private BigDecimal cashOutTotal;
        private BigDecimal transfersOutTotal;
        private BigDecimal transfersInTotal;

        private List<CashMovementResponse> movements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftSettingsDTO {
        private Boolean enabled;
        private LocalDateTime updatedAt;
        private Long updatedBy;
        private String updatedByName;

        private BigDecimal varianceThreshold;
        private BigDecimal largeMovementThreshold;
        private Boolean flagMissingCount;
        private Boolean flagTransfersWithoutRef;
    }
}
