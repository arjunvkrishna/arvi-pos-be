package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One item in a count: the book quantity when the count started and what was found on the shelf (null = not counted). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_count_lines")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(count_id IN (SELECT p.id FROM stock_counts p WHERE p.outlet_id = :outletId OR p.outlet_id IS NULL))")
public class StockCountLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "count_id", nullable = false)
    private Long countId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(length = 50)
    private String unit;

    @Column(name = "book_qty", nullable = false)
    private Double bookQty;

    @Column(name = "counted_qty")
    private Double countedQty;

    @Column(name = "cost_per_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPerUnit = BigDecimal.ZERO;
}
