package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tip_pool_distributions")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class TipPoolDistribution implements com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The branch this record belongs to; null means the whole cafe. */
    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "distribution_date", nullable = false, unique = true)
    private LocalDate distributionDate;

    @Column(name = "total_tips", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTips;

    @Column(name = "total_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "rate_per_hour", nullable = false, precision = 10, scale = 4)
    private BigDecimal ratePerHour;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "distribution", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TipPoolEmployeeShare> shares = new ArrayList<>();
}
