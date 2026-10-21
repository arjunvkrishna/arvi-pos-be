package com.neram.pos.dto;

import lombok.Data;

@Data
public class InventoryItemRequestDTO {
    private String name;
    private String unit;
    private Double quantity;
    private Double minQuantity;
    private java.math.BigDecimal costPerUnit;
    private Double unitVolume;
    private Double volume;
    private String volumeUnit;
    private Double totalVolume;
    private java.math.BigDecimal costPerVolume;
    private String supplier;
    private String itemCategory;
    private Boolean active;

    public Double getEffectiveUnitVolume() {
        if (unitVolume != null && unitVolume > 0) return unitVolume;
        if (volume != null && volume > 0) return volume;
        return 1.0;
    }
}
