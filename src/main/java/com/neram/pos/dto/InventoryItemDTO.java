package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemDTO {
    private Long id;
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
    private Boolean lowStock;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;

    public Double getVolume() {
        return volume != null ? volume : unitVolume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
        if (this.unitVolume == null) this.unitVolume = volume;
    }
}
