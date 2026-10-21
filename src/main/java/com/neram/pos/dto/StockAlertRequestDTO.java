package com.neram.pos.dto;

import lombok.Data;

@Data
public class StockAlertRequestDTO {
    private Long inventoryItemId;
    private String itemName;
    private Double currentQuantity;
    private Double minQuantity;
    private String unit;
    private String note;
    private String customMessage;

    public String getEffectiveNote() {
        if (customMessage != null && !customMessage.isBlank()) return customMessage;
        return note;
    }
}
