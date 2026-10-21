package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierInvoiceItemDTO {
    private Long id;
    private Long invoiceId;
    private Long inventoryItemId;
    private String productName;
    private Double quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String unit;
    private String itemCategory;
    private Double unitVolume;
    private Double volume;
    private String volumeUnit;
    private Double totalVolume;
    private BigDecimal costPerVolume;
    private Double gstRate;
    private BigDecimal gstAmount;
    private LocalDateTime createdAt;

    public Double getEffectiveUnitVolume() {
        if (unitVolume != null && unitVolume > 0) return unitVolume;
        if (volume != null && volume > 0) return volume;
        return 1.0;
    }

    public Double getVolume() {
        return volume != null ? volume : unitVolume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
        if (this.unitVolume == null) this.unitVolume = volume;
    }
}
