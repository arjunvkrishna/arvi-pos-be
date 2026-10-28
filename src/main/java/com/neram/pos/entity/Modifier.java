package com.neram.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "modifiers")
public class Modifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    @JsonIgnore
    private ModifierGroup group;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_adjustment", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "inventory_item_id")
    private Long inventoryItemId;

    @Column(name = "inventory_quantity", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal inventoryQuantity = BigDecimal.ZERO;
}
