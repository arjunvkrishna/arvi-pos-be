package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** The one row of loyalty rules the admin can change: how long points last and the least that can be redeemed. */
@Entity
@Table(name = "loyalty_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltySetting {

    @Id
    private Long id;

    @Column(name = "expiry_days", nullable = false)
    @Builder.Default
    private Integer expiryDays = 180;

    @Column(name = "min_redeem_points", nullable = false)
    @Builder.Default
    private Integer minRedeemPoints = 100;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_name", length = 100)
    private String updatedByName;
}
