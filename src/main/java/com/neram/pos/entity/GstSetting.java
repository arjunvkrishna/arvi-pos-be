package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gst_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GstSetting {

    @Id
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "gstin", length = 50)
    @Builder.Default
    private String gstin = "32CQRPS5214H3ZK";

    @Column(name = "cgst_percent", nullable = false)
    @Builder.Default
    private Double cgstPercent = 2.5;

    @Column(name = "sgst_percent", nullable = false)
    @Builder.Default
    private Double sgstPercent = 2.5;

    @Column(name = "round_off_enabled", nullable = false)
    @Builder.Default
    private Boolean roundOffEnabled = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
        if (id == null) id = 1L;
        if (enabled == null) enabled = true;
        if (gstin == null || gstin.isBlank()) gstin = "32CQRPS5214H3ZK";
        if (cgstPercent == null || cgstPercent < 0) cgstPercent = 2.5;
        if (sgstPercent == null || sgstPercent < 0) sgstPercent = 2.5;
        if (roundOffEnabled == null) roundOffEnabled = false;
    }
}
