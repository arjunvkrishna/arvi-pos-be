package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@EntityListeners(com.neram.pos.config.OrderChangeListener.class)
@Table(name = "dining_tables")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class DiningTable implements com.neram.pos.tenancy.TenantScoped , com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique inside its branch (see V87), not across the cafe: every branch has its own T1, T2 ... */
    @Column(nullable = false, length = 50)
    private String name;

    /** Floor or section the table is on (Ground, First floor, Terrace ...); null when the branch does not use floors. */
    @Column(length = 50)
    private String floor;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) active = true;
        if (displayOrder == null) displayOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
