package com.neram.pos.controller;

import com.neram.pos.dto.TenantSettingsDTO;
import com.neram.pos.entity.Tenant;
import com.neram.pos.repository.TenantRepository;
import com.neram.pos.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CAFE_ADMIN', 'GENERAL_MANAGER', 'MANAGER')")
    public ResponseEntity<TenantSettingsDTO> getTenantSettings() {
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found with id: " + tenantId));

        return ResponseEntity.ok(toDTO(tenant));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CAFE_ADMIN', 'GENERAL_MANAGER', 'MANAGER')")
    public ResponseEntity<?> updateTenantSettings(@RequestBody TenantSettingsDTO dto) {
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found with id: " + tenantId));

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            tenant.setName(dto.getName().trim());
        }
        if (dto.getContactEmail() != null) {
            tenant.setContactEmail(dto.getContactEmail().trim());
        }
        if (dto.getContactPhone() != null) {
            tenant.setContactPhone(dto.getContactPhone().trim());
        }
        if (dto.getAddress() != null) {
            tenant.setAddress(dto.getAddress().trim());
        }
        if (dto.getGstNumber() != null) {
            tenant.setGstNumber(dto.getGstNumber().trim());
        }
        if (dto.getLogoUrl() != null) {
            tenant.setLogoUrl(dto.getLogoUrl().trim());
        }
        if (dto.getCurrency() != null && !dto.getCurrency().trim().isEmpty()) {
            tenant.setCurrency(dto.getCurrency().trim());
        }
        if (dto.getCurrencySymbol() != null && !dto.getCurrencySymbol().trim().isEmpty()) {
            tenant.setCurrencySymbol(dto.getCurrencySymbol().trim());
        }

        tenant.setUpdatedAt(LocalDateTime.now());
        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant {} ({}) updated settings and logo", saved.getId(), saved.getName());

        return ResponseEntity.ok(toDTO(saved));
    }

    private TenantSettingsDTO toDTO(Tenant t) {
        return TenantSettingsDTO.builder()
                .id(t.getId())
                .name(t.getName())
                .code(t.getCode())
                .contactEmail(t.getContactEmail())
                .contactPhone(t.getContactPhone())
                .address(t.getAddress())
                .gstNumber(t.getGstNumber())
                .logoUrl(t.getLogoUrl())
                .subscriptionPlan(t.getSubscriptionPlan())
                .maxOutlets(t.getMaxOutlets())
                .currency(t.getCurrency())
                .currencySymbol(t.getCurrencySymbol())
                .build();
    }
}
