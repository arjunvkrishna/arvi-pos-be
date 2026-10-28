package com.neram.pos.controller;

import com.neram.pos.dto.CampaignDTO;
import com.neram.pos.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CampaignDTO>> getAllCampaigns() {
        return ResponseEntity.ok(campaignService.getAllCampaigns());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CampaignDTO> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaignById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<CampaignDTO> createCampaign(@RequestBody CampaignDTO dto) {
        return ResponseEntity.ok(campaignService.createCampaign(dto, null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<CampaignDTO> updateCampaign(@PathVariable Long id, @RequestBody CampaignDTO dto) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audience-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CampaignDTO.AudienceCountResponse> getAudienceCount(@RequestParam(defaultValue = "ALL") String audienceType) {
        return ResponseEntity.ok(campaignService.getAudienceCount(audienceType));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<CampaignDTO.SendResponse> sendCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.sendCampaign(id));
    }
}
