package com.neram.pos.dto;

import lombok.Data;

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
}
