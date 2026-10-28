package com.neram.pos.service;

import com.neram.pos.dto.CampaignDTO;
import com.neram.pos.entity.Campaign;
import com.neram.pos.entity.Customer;
import com.neram.pos.repository.CampaignRepository;
import com.neram.pos.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CustomerRepository customerRepository;

    public List<CampaignDTO> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CampaignDTO getCampaignById(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with ID: " + id));
        return toDTO(campaign);
    }

    @Transactional
    public CampaignDTO createCampaign(CampaignDTO dto, Long userId) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }
        if (dto.getMessage() == null || dto.getMessage().isBlank()) {
            throw new IllegalArgumentException("Campaign message is required");
        }
        String audienceType = dto.getAudienceType() != null ? dto.getAudienceType().toUpperCase() : "ALL";
        String channel = dto.getChannel() != null ? dto.getChannel().toUpperCase() : "EMAIL";

        Campaign campaign = Campaign.builder()
                .name(dto.getName().trim())
                .message(dto.getMessage().trim())
                .channel(channel)
                .audienceType(audienceType)
                .status("DRAFT")
                .sentCount(0)
                .scheduledAt(dto.getScheduledAt())
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        return toDTO(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDTO updateCampaign(Long id, CampaignDTO dto) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with ID: " + id));

        if ("SENT".equals(campaign.getStatus())) {
            throw new IllegalStateException("Cannot edit an already sent campaign");
        }

        if (dto.getName() != null && !dto.getName().isBlank()) campaign.setName(dto.getName().trim());
        if (dto.getMessage() != null && !dto.getMessage().isBlank()) campaign.setMessage(dto.getMessage().trim());
        if (dto.getChannel() != null) campaign.setChannel(dto.getChannel().toUpperCase());
        if (dto.getAudienceType() != null) campaign.setAudienceType(dto.getAudienceType().toUpperCase());
        if (dto.getScheduledAt() != null) campaign.setScheduledAt(dto.getScheduledAt());

        return toDTO(campaignRepository.save(campaign));
    }

    @Transactional
    public void deleteCampaign(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with ID: " + id));
        campaignRepository.delete(campaign);
    }

    public List<Customer> resolveAudience(String audienceType) {
        if (audienceType == null) audienceType = "ALL";
        switch (audienceType.toUpperCase()) {
            case "LOYALTY_MEMBERS":
                return customerRepository.findLoyaltyAudience();
            case "INACTIVE_30_DAYS":
                LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
                return customerRepository.findInactiveAudience(thirtyDaysAgo);
            case "ALL":
            default:
                return customerRepository.findByMarketingConsentTrueAndIsActiveTrue();
        }
    }

    public CampaignDTO.AudienceCountResponse getAudienceCount(String audienceType) {
        List<Customer> customers = resolveAudience(audienceType);
        return CampaignDTO.AudienceCountResponse.builder()
                .audienceType(audienceType)
                .eligibleCount(customers.size())
                .build();
    }

    @Transactional
    public CampaignDTO.SendResponse sendCampaign(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with ID: " + id));

        if ("SENT".equals(campaign.getStatus())) {
            throw new IllegalStateException("Campaign has already been sent");
        }

        List<Customer> targetAudience = resolveAudience(campaign.getAudienceType());
        int count = targetAudience.size();

        campaign.setStatus("SENT");
        campaign.setSentCount(count);
        campaign.setSentAt(Instant.now());
        campaignRepository.save(campaign);

        log.info("Campaign ID {} ('{}') recorded as sent for {} recipient(s) via {} (no delivery provider is connected)", campaign.getId(), campaign.getName(), count, campaign.getChannel());

        return CampaignDTO.SendResponse.builder()
                .campaignId(campaign.getId())
                .status("SENT")
                .sentCount(count)
                .sentAt(campaign.getSentAt())
                .build();
    }

    @Transactional
    public boolean unsubscribe(String token) {
        if (token == null || token.isBlank()) return false;
        Customer customer = customerRepository.findByUnsubscribeToken(token.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid unsubscribe token"));
        customer.setMarketingConsent(false);
        customerRepository.save(customer);
        log.info("Customer {} (ID {}) unsubscribed from marketing communications", customer.getName(), customer.getId());
        return true;
    }

    private CampaignDTO toDTO(Campaign c) {
        return CampaignDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .message(c.getMessage())
                .channel(c.getChannel())
                .audienceType(c.getAudienceType())
                .status(c.getStatus())
                .sentCount(c.getSentCount())
                .scheduledAt(c.getScheduledAt())
                .sentAt(c.getSentAt())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
