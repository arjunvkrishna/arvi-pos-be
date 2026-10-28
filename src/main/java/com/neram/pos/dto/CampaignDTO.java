package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignDTO {
    private Long id;
    private String name;
    private String message;
    private String channel;
    private String audienceType;
    private String status;
    private Integer sentCount;
    private Instant scheduledAt;
    private Instant sentAt;
    private Long createdBy;
    private Instant createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AudienceCountResponse {
        private String audienceType;
        private int eligibleCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SendResponse {
        private Long campaignId;
        private String status;
        private int sentCount;
        private Instant sentAt;
    }
}
