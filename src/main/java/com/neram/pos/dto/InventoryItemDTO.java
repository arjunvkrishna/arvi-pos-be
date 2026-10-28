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
    private java.math.BigDecimal reorderLevel;
    private java.math.BigDecimal costPerUnit;
    private Double unitVolume;
    private Double volume;
    private String volumeUnit;
    private Double totalVolume;
    private java.math.BigDecimal costPerVolume;
    private String packName;
    private Double packSize;
    private String supplier;
    private String itemCategory;
    private Boolean active;
    private Boolean lowStock;
    /** false = issued by hand; dishes selling do not reduce this item's stock */
    private Boolean autoDeduct;
    private Integer shelfLifeDays;
    private Integer expiryWarnDays;
    /** earliest expiry that matters (an expired or soon-to-expire batch first), with its status and the quantity affected */
    private java.time.LocalDate nextExpiryDate;
    /** null = no expiry tracked, OK, SOON or EXPIRED */
    private String expiryStatus;
    private Double expiringQuantity;
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
