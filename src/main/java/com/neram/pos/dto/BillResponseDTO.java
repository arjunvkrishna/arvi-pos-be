package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillResponseDTO {
    private Long id;
    private String billNumber;
    private String orderType;
    private String tableNumber;
    private String customerName;
    private Long customerId;
    private String customerPhone;
    private java.time.LocalDate businessDate;
    private String outdoorToken;
    private String takeawayToken;
    private java.math.BigDecimal subtotal;
    private Double discountPercent;
    private java.math.BigDecimal discountAmount;
    private Double taxPercent;
    private java.math.BigDecimal taxAmount;
    private Boolean gstEnabled;
    private String gstin;
    private Double cgstPercent;
    private Double sgstPercent;
    private java.math.BigDecimal cgstAmount;
    private java.math.BigDecimal sgstAmount;
    private java.math.BigDecimal roundOff;
    private Long promotionId;
    private String promotionName;
    private Double promotionPercentage;
    private java.math.BigDecimal promotionDiscountAmount;
    private Long couponId;
    private java.math.BigDecimal couponDiscount;
    private java.math.BigDecimal netAmount;
    /** tip recorded at payment (not part of netAmount) */
    private java.math.BigDecimal tipAmount;
    /** set when the bill was paid in more than one way */
    private java.util.List<BillRequestDTO.PaymentPart> payments;
    private String paymentSummary;
    private String paymentMode;
    private String status;
    private String notes;
    private Long createdBy;
    private String createdByName;
    private Long printedBy;
    private String printedByName;
    private LocalDateTime createdAt;
    private LocalDateTime printedAt;
    private LocalDateTime kotPrintedAt;
    private String kotNumber;
    private Long shiftId;
    private Long voidedBy;
    private String voidedByName;
    private LocalDateTime voidedAt;
    private String voidReason;
    private LocalDateTime deletedAt;
    private String deletedByName;
    private String deleteReason;
    private java.math.BigDecimal refundedAmount;
    private java.math.BigDecimal totalRefunded;
    private java.math.BigDecimal remainingRefundableAmount;
    private List<BillItemResponseDTO> items;
    private List<BillItemResponseDTO> latestKotItems;
    private Integer kotUpdateCount;
    private LocalDateTime kotUpdatedAt;
    private LocalDateTime kotCancelledAt;
    private String kotNoticeType;
    private List<String> kotChangesSummary;
    private java.math.BigDecimal parcelCharge;
    private Boolean hasParcel;
}
