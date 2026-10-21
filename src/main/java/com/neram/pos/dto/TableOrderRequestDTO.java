package com.neram.pos.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class TableOrderRequestDTO {
    private Long orderId;
    private String orderType;
    private String tableNumber;
    private String customerName;
    private String outdoorToken;
    private String takeawayToken;
    private BigDecimal parcelCharge;
    private String notes;
    private Double discountPercent;
    private Double taxPercent;
    private Boolean gstEnabled;
    private Double cgstPercent;
    private Double sgstPercent;
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {
        private Long menuItemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal price;
        private String customization;
        private Boolean isTakeaway;
    }
}
