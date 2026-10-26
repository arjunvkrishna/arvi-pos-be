package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class BillRequestDTO {
    private String tableNumber;
    private String customerName;
    private String orderType;
    private String outdoorToken;
    private String takeawayToken;
    private Double discountPercent;
    private Double taxPercent;
    private Boolean gstEnabled;
    private Double cgstPercent;
    private Double sgstPercent;
    private String paymentMode;
    private java.math.BigDecimal parcelCharge;
    /** optional tip, recorded when payment is confirmed */
    private java.math.BigDecimal tipAmount;
    /** paid in more than one way: the parts must add up to the net amount */
    private List<PaymentPart> payments;
    private String notes;
    private List<BillItemRequestDTO> items;

    @Data
    public static class BillItemRequestDTO {
        private Long menuItemId;
        private String itemName;
        private Integer quantity;
        private java.math.BigDecimal price;
        private String customization;
        private Boolean isTakeaway;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentPart {
        private String mode;
        private java.math.BigDecimal amount;
        private String reference;
    }
}
