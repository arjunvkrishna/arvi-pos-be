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
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(invoice_id IN (SELECT p.id FROM supplier_invoices p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
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

    /** PACK when entered in packs (e.g. cases), else UNIT/null. quantity/unitPrice are as entered. */
    @Column(name = "purchase_mode", length = 10)
    private String purchaseMode;

    @Column(name = "pack_name", length = 50)
    private String packName;

    @Column(name = "pack_size")
    private Double packSize;

    /** Stock units added by this line; null on older rows means same as quantity. */
    @Column(name = "stock_quantity")
    private Double stockQuantity;

    /** Optional expiry date of the goods on this line. */
    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;

    public double stockQty() {
        return stockQuantity != null ? stockQuantity : (quantity != null ? quantity : 0.0);
    }

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
