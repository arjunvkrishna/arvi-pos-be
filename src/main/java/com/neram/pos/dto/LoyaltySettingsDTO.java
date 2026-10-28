package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltySettingsDTO {
    /** Days without a purchase after which a customer's points lapse. */
    private Integer expiryDays;
    /** The least a customer can redeem in one go (and the least balance needed to redeem at all). */
    private Integer minRedeemPoints;
    /** Fixed rules, sent so the screens can say them: ₹100 spent earns 1 point, and 1 point is worth ₹1. */
    private Integer rupeesPerPointEarned;
    private Integer rupeesPerPointRedeemed;
    private LocalDateTime updatedAt;
    private String updatedByName;
}
