package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_items")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class InventoryItem implements com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit = "pcs";

    @Column(nullable = false)
    private Double quantity = 0.0;

    @Column(name = "min_quantity", nullable = false)
    private Double minQuantity = 0.0;

    @Column(name = "cost_per_unit", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal costPerUnit = java.math.BigDecimal.ZERO;

    @Column(name = "unit_volume")
    private Double unitVolume = 1.0;

    @Column(name = "volume_unit", length = 50)
    private String volumeUnit;

    @Column(name = "total_volume")
    private Double totalVolume = 0.0;

    @Column(name = "cost_per_volume", precision = 12, scale = 2)
    private java.math.BigDecimal costPerVolume = java.math.BigDecimal.ZERO;

    /** Optional pack: 1 pack = packSize stock units (e.g. case = 12 bottles). */
    @Column(name = "pack_name", length = 50)
    private String packName;

    @Column(name = "pack_size")
    private Double packSize;

    /** false = this item is issued by hand (milk, oil...): selling a dish with a recipe does not reduce its stock. */
    @Column(name = "auto_deduct", nullable = false)
    private Boolean autoDeduct = true;

    /** Optional shelf life: goods received on a purchase line without an expiry date expire this many days after the invoice date. */
    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    /** How many days before expiry the item is flagged "expiring soon". */
    @Column(name = "expiry_warn_days", nullable = false)
    private Integer expiryWarnDays = 2;

    @Column(name = "reorder_level", precision = 10, scale = 3, nullable = false)
    private java.math.BigDecimal reorderLevel = java.math.BigDecimal.ZERO;

    private String supplier;

    @Column(name = "item_category", length = 50, nullable = false)
    private String itemCategory = "Stock";

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Double getVolume() {
        return unitVolume;
    }

    public void setVolume(Double v) {
        this.unitVolume = v;
    }

    @Column(name = "outlet_id")
    private Long outletId;
}
