package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shift_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftSetting {

    @Id
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_by_name", length = 100)
    private String updatedByName;

    @Column(name = "variance_threshold", precision = 12, scale = 2)
    @Builder.Default
    private java.math.BigDecimal varianceThreshold = new java.math.BigDecimal("500.00");

    @Column(name = "large_movement_threshold", precision = 12, scale = 2)
    @Builder.Default
    private java.math.BigDecimal largeMovementThreshold = new java.math.BigDecimal("5000.00");

    @Column(name = "flag_missing_count")
    @Builder.Default
    private Boolean flagMissingCount = true;

    @Column(name = "flag_transfers_without_ref")
    @Builder.Default
    private Boolean flagTransfersWithoutRef = true;
}
