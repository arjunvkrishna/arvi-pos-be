package com.neram.pos.service;

import com.neram.pos.dto.LoyaltySettingsDTO;
import com.neram.pos.entity.LoyaltySetting;
import com.neram.pos.repository.LoyaltySettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoyaltySettingsService {

    public static final int MAX_EXPIRY_DAYS = 3650;
    public static final int MAX_MIN_REDEEM = 100_000;
    /** ₹100 of spend earns 1 point; 1 point pays ₹1. */
    public static final int RUPEES_PER_POINT_EARNED = 100;
    public static final int RUPEES_PER_POINT_REDEEMED = 1;

    /** What the points code needs, without the bookkeeping fields. */
    public record Rules(int expiryDays, int minRedeemPoints) {}

    private final LoyaltySettingRepository repository;
    private final AuditLogService auditLogService;

    public Rules rules() {
        LoyaltySetting s = repository.findById(1L).orElseGet(() -> LoyaltySetting.builder().id(1L).build());
        return new Rules(s.getExpiryDays(), s.getMinRedeemPoints());
    }

    public LoyaltySettingsDTO get() {
        return toDTO(repository.findById(1L).orElseGet(() -> LoyaltySetting.builder().id(1L).build()));
    }

    @Transactional
    public LoyaltySettingsDTO update(LoyaltySettingsDTO dto, String username) {
        LoyaltySetting s = repository.findById(1L).orElseGet(() -> LoyaltySetting.builder().id(1L).build());
        String before = "expiry " + s.getExpiryDays() + " days, minimum redeem " + s.getMinRedeemPoints() + " points";
        if (dto.getExpiryDays() != null) {
            if (dto.getExpiryDays() < 1 || dto.getExpiryDays() > MAX_EXPIRY_DAYS) {
                throw new IllegalArgumentException("Points can last between 1 and " + MAX_EXPIRY_DAYS + " days.");
            }
            s.setExpiryDays(dto.getExpiryDays());
        }
        if (dto.getMinRedeemPoints() != null) {
            if (dto.getMinRedeemPoints() < 1 || dto.getMinRedeemPoints() > MAX_MIN_REDEEM) {
                throw new IllegalArgumentException("The minimum to redeem must be between 1 and " + MAX_MIN_REDEEM + " points.");
            }
            s.setMinRedeemPoints(dto.getMinRedeemPoints());
        }
        s.setUpdatedAt(LocalDateTime.now());
        s.setUpdatedByName(username);
        LoyaltySetting saved = repository.save(s);
        auditLogService.log("LOYALTY_SETTINGS_CHANGED", "SETTING", 1L, before,
                "expiry " + saved.getExpiryDays() + " days, minimum redeem " + saved.getMinRedeemPoints() + " points, by " + username);
        return toDTO(saved);
    }

    private static LoyaltySettingsDTO toDTO(LoyaltySetting s) {
        return LoyaltySettingsDTO.builder()
                .expiryDays(s.getExpiryDays())
                .minRedeemPoints(s.getMinRedeemPoints())
                .rupeesPerPointEarned(RUPEES_PER_POINT_EARNED)
                .rupeesPerPointRedeemed(RUPEES_PER_POINT_REDEEMED)
                .updatedAt(s.getUpdatedAt())
                .updatedByName(s.getUpdatedByName())
                .build();
    }
}
