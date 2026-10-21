package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "supplier_invoice_items")
public class SupplierInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "inventory_item_id")
    private Long inventoryItemId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal totalPrice;

    @Column(length = 50)
    @Builder.Default
    private String unit = "pcs";

    @Column(name = "item_category", length = 50)
    @Builder.Default
    private String itemCategory = "Stock";

    @Column(name = "unit_volume")
    @Builder.Default
    private Double unitVolume = 1.0;

    @Column(name = "volume_unit", length = 50)
    private String volumeUnit;

    @Column(name = "total_volume")
    private Double totalVolume;

    @Column(name = "cost_per_volume", precision = 12, scale = 2)
    private java.math.BigDecimal costPerVolume;

    @Column(name = "gst_rate")
    @Builder.Default
    private Double gstRate = 0.0;

    @Column(name = "gst_amount", precision = 12, scale = 2)
    @Builder.Default
    private java.math.BigDecimal gstAmount = java.math.BigDecimal.ZERO;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public Double getVolume() {
        return unitVolume;
    }

    public void setVolume(Double v) {
        this.unitVolume = v;
    }
}
