package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(com.neram.pos.config.OrderChangeListener.class)
@Table(name = "bill_items")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(bill_id IN (SELECT p.id FROM bills p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    @JsonIgnore
    private Bill bill;

    @Column(name = "menu_item_id")
    private Long menuItemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "printed_quantity", nullable = false)
    private Integer printedQuantity = 0;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal subtotal;

    @Column(name = "customization", length = 500)
    private String customization;

    @Column(name = "is_takeaway", nullable = false)
    private Boolean isTakeaway = false;
}
