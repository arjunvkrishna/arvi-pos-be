package com.neram.pos.service;

import com.neram.pos.dto.GstSettingDTO;
import com.neram.pos.entity.GstSetting;
import com.neram.pos.entity.User;
import com.neram.pos.repository.GstSettingRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GstSettingService {

    private final GstSettingRepository gstSettingRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    private static final java.util.regex.Pattern GSTIN_PATTERN =
            java.util.regex.Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final double MAX_RATE_PERCENT = 30.0;

    private static void validateRate(String label, Double rate) {
        if (rate != null && (rate.isNaN() || rate < 0 || rate > MAX_RATE_PERCENT)) {
            throw new IllegalArgumentException(label + " rate must be between 0 and " + (int) MAX_RATE_PERCENT + "%");
        }
    }

    public GstSetting getSettings() {
        return gstSettingRepository.findById(1L).orElseGet(() -> {
            GstSetting s = GstSetting.builder()
                    .id(1L)
                    .enabled(true)
                    .gstin("32CQRPS5214H3ZK")
                    .cgstPercent(2.5)
                    .sgstPercent(2.5)
                    .roundOffEnabled(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
            return gstSettingRepository.save(s);
        });
    }

    public GstSettingDTO getSettingsDTO() {
        GstSetting s = getSettings();
        String updatedBy = "System";
        if (s.getUpdatedBy() != null) {
            updatedBy = userRepository.findById(s.getUpdatedBy()).map(User::getUsername).orElse("System");
        }
        double cgst = s.getCgstPercent() != null ? s.getCgstPercent() : 0.0;
        double sgst = s.getSgstPercent() != null ? s.getSgstPercent() : 0.0;
        return GstSettingDTO.builder()
                .enabled(s.getEnabled())
                .gstin(s.getGstin())
                .cgstPercent(cgst)
                .sgstPercent(sgst)
                .totalGstPercent(Math.round((cgst + sgst) * 100.0) / 100.0)
                .roundOffEnabled(s.getRoundOffEnabled())
                .updatedAt(s.getUpdatedAt())
                .updatedByName(updatedBy)
                .build();
    }

    @Transactional
    public GstSettingDTO updateSettings(GstSettingDTO dto) {
        GstSetting s = getSettings();
        String oldVal = String.format("Enabled: %s, GSTIN: %s, CGST: %.2f%%, SGST: %.2f%%, RoundOff: %s",
                s.getEnabled(), s.getGstin(), s.getCgstPercent(), s.getSgstPercent(), s.getRoundOffEnabled());

        if (dto.getGstin() != null && !dto.getGstin().isBlank()) {
            String gstin = dto.getGstin().trim().toUpperCase();
            if (!GSTIN_PATTERN.matcher(gstin).matches()) {
                throw new IllegalArgumentException("Invalid GSTIN. Expected 15 characters, e.g. 32ABCDE1234F1Z5.");
            }
            dto.setGstin(gstin);
        }
        validateRate("CGST", dto.getCgstPercent());
        validateRate("SGST", dto.getSgstPercent());

        if (dto.getEnabled() != null) s.setEnabled(dto.getEnabled());
        if (dto.getGstin() != null && !dto.getGstin().isBlank()) s.setGstin(dto.getGstin());
        if (dto.getCgstPercent() != null) s.setCgstPercent(dto.getCgstPercent());
        if (dto.getSgstPercent() != null) s.setSgstPercent(dto.getSgstPercent());
        if (dto.getRoundOffEnabled() != null) s.setRoundOffEnabled(dto.getRoundOffEnabled());

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> s.setUpdatedBy(u.getId()));
            }
        } catch (Exception ignored) {}

        s.setUpdatedAt(LocalDateTime.now());
        gstSettingRepository.save(s);

        String newVal = String.format("Enabled: %s, GSTIN: %s, CGST: %.2f%%, SGST: %.2f%%, RoundOff: %s",
                s.getEnabled(), s.getGstin(), s.getCgstPercent(), s.getSgstPercent(), s.getRoundOffEnabled());

        auditLogService.log("UPDATE_GST_SETTINGS", "SETTINGS", 1L, oldVal, newVal);
        return getSettingsDTO();
    }
}
