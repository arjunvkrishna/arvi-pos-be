package com.neram.pos.service;

import com.neram.pos.dto.LoyaltySettingsDTO;
import com.neram.pos.entity.LoyaltySetting;
import com.neram.pos.repository.LoyaltySettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltySettingsServiceTest {

    @Mock private LoyaltySettingRepository repository;
    @Mock private AuditLogService audit;
    private LoyaltySettingsService svc;
    private LoyaltySetting row;

    @BeforeEach
    void setUp() {
        row = LoyaltySetting.builder().id(1L).build();
        when(repository.findById(1L)).thenReturn(Optional.of(row));
        when(repository.save(any(LoyaltySetting.class))).thenAnswer(i -> i.getArgument(0));
        svc = new LoyaltySettingsService(repository, audit);
    }

    private static LoyaltySettingsDTO change(Integer days, Integer min) {
        return LoyaltySettingsDTO.builder().expiryDays(days).minRedeemPoints(min).build();
    }

    @Test
    @DisplayName("out of the box: points last 180 days and 100 points are needed to redeem")
    void defaults() {
        assertEquals(new LoyaltySettingsService.Rules(180, 100), svc.rules());
        LoyaltySettingsDTO dto = svc.get();
        assertEquals(100, dto.getRupeesPerPointEarned());
        assertEquals(1, dto.getRupeesPerPointRedeemed());
    }

    @Test
    @DisplayName("an admin can change the validity period and the minimum, and the change is audited")
    void changeIsSavedAndAudited() {
        LoyaltySettingsDTO saved = svc.update(change(90, 200), "owner");
        assertEquals(90, saved.getExpiryDays());
        assertEquals(200, saved.getMinRedeemPoints());
        assertEquals(new LoyaltySettingsService.Rules(90, 200), svc.rules());
        verify(audit).log(eq("LOYALTY_SETTINGS_CHANGED"), eq("SETTING"), eq(1L), anyString(), contains("90 days"));
    }

    @Test
    @DisplayName("senseless values are refused and nothing is saved")
    void badValuesAreRefused() {
        for (LoyaltySettingsDTO bad : new LoyaltySettingsDTO[]{change(0, null), change(-5, null), change(3651, null), change(null, 0), change(null, 100_001)}) {
            assertThrows(IllegalArgumentException.class, () -> svc.update(bad, "owner"));
        }
        verify(repository, never()).save(any());
        assertEquals(new LoyaltySettingsService.Rules(180, 100), svc.rules());
    }

    @Test
    @DisplayName("changing one setting leaves the other alone")
    void partialChange() {
        svc.update(change(365, null), "owner");
        assertEquals(new LoyaltySettingsService.Rules(365, 100), svc.rules());
    }
}
