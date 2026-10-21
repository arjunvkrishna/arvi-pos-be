package com.neram.pos.service;

import com.neram.pos.entity.AppSetting;
import com.neram.pos.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingRepository appSettingRepository;
    private final AuditLogService auditLogService;

    public String getSetting(String key, String defaultValue) {
        return appSettingRepository.findByKey(key)
                .map(AppSetting::getValue)
                .orElse(defaultValue);
    }

    public boolean isPublicMenuEnabled() {
        return "true".equalsIgnoreCase(getSetting("PUBLIC_MENU_ENABLED", "true"));
    }

    public java.math.BigDecimal getDefaultParcelCharge() {
        String val = getSetting("PARCEL_CHARGE_DEFAULT", "10.00");
        try {
            return new java.math.BigDecimal(val).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new java.math.BigDecimal("10.00");
        }
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public void setSetting(String key, String value, String username) {
        AppSetting setting = appSettingRepository.findByKey(key)
                .orElse(AppSetting.builder().key(key).build());

        String oldValue = setting.getValue();
        setting.setValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(username != null ? username : "SYSTEM");

        appSettingRepository.saveAndFlush(setting);
        auditLogService.log("UPDATE_SETTING", "APP_SETTING", null, oldValue, key + "=" + value);
    }
}
