package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A physical stock count: DRAFT while people count, APPLIED once the differences were booked, CANCELLED if abandoned. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_counts")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class StockCount implements com.neram.pos.access.OutletScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String status = "DRAFT";

    @Column(length = 60)
    private String scope;

    @Column(length = 300)
    private String note;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "applied_by", length = 100)
    private String appliedBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "outlet_id")
    private Long outletId;
}
