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
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType; // STOCK_IN, STOCK_OUT, ADJUSTMENT

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "previous_stock", nullable = false)
    private Double previousStock;

    @Column(name = "new_stock", nullable = false)
    private Double newStock;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_reference", length = 150)
    private String invoiceReference;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private java.math.BigDecimal unitPrice;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "bill_item_id")
    private Long billItemId;

    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(length = 50)
    private String unit;

    @Column(name = "item_category", length = 50)
    private String itemCategory;

    @Column(name = "unit_volume")
    private Double unitVolume;

    @Column(name = "volume_unit", length = 50)
    private String volumeUnit;

    @Column(name = "total_volume")
    private Double totalVolume;

    @Column(name = "cost_per_volume", precision = 12, scale = 2)
    private java.math.BigDecimal costPerVolume;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
