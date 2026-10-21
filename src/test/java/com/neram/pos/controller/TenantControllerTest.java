package com.neram.pos.controller;

import com.neram.pos.dto.TenantSettingsDTO;
import com.neram.pos.entity.Tenant;
import com.neram.pos.repository.TenantRepository;
import com.neram.pos.service.TenantSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantSecurityService tenantSecurityService;

    @InjectMocks
    private TenantController tenantController;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder()
                .id(1L)
                .name("Brew & Co.")
                .code("brew-co")
                .contactEmail("admin@brewco.internal")
                .contactPhone("+91 9876543210")
                .address("MG Road, Kochi")
                .gstNumber("32ABCDE1234F1Z5")
                .logoUrl("https://arvi.io/assets/logos/brewco.png")
                .subscriptionPlan("ENTERPRISE")
                .maxOutlets(5)
                .currency("INR")
                .currencySymbol("₹")
                .build();
    }

    @Test
    @DisplayName("getTenantSettings returns settings and logo for authenticated tenant")
    void testGetTenantSettings_Success() {
        when(tenantSecurityService.resolveTenantId(null)).thenReturn(1L);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        ResponseEntity<TenantSettingsDTO> res = tenantController.getTenantSettings();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("Brew & Co.", res.getBody().getName());
        assertEquals("https://arvi.io/assets/logos/brewco.png", res.getBody().getLogoUrl());
        assertEquals("₹", res.getBody().getCurrencySymbol());
        assertEquals("INR", res.getBody().getCurrency());
    }

    @Test
    @DisplayName("getTenantSettings throws NoSuchElementException when tenant does not exist")
    void testGetTenantSettings_NotFound() {
        when(tenantSecurityService.resolveTenantId(null)).thenReturn(999L);
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> tenantController.getTenantSettings());
    }

    @Test
    @DisplayName("updateTenantSettings successfully updates cafe logo, brand name, and contact details")
    void testUpdateTenantSettings_Success() {
        when(tenantSecurityService.resolveTenantId(null)).thenReturn(1L);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

        TenantSettingsDTO updateDto = TenantSettingsDTO.builder()
                .name("Brew & Co. Roastery")
                .logoUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
                .contactEmail("contact@brewco-roastery.com")
                .contactPhone("+91 9998887776")
                .address("Marine Drive, Kochi")
                .currency("USD")
                .currencySymbol("$")
                .build();

        ResponseEntity<?> response = tenantController.updateTenantSettings(updateDto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof TenantSettingsDTO);
        TenantSettingsDTO body = (TenantSettingsDTO) response.getBody();

        assertEquals("Brew & Co. Roastery", body.getName());
        assertTrue(body.getLogoUrl().startsWith("data:image/png;base64,"));
        assertEquals("contact@brewco-roastery.com", body.getContactEmail());
        assertEquals("+91 9998887776", body.getContactPhone());
        assertEquals("USD", body.getCurrency());
        assertEquals("$", body.getCurrencySymbol());

        verify(tenantRepository).save(tenant);
    }
}
