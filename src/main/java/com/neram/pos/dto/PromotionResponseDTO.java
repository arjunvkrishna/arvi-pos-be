package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionResponseDTO {
    private Long id;
    private String name;
    private Double discountPercentage;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Boolean isActive;
    private String status; // "ACTIVE", "SCHEDULED", "EXPIRED", "INACTIVE"
    private String description;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean recurring;
    private List<Integer> daysOfWeek;
    private LocalTime dailyStart;
    private LocalTime dailyEnd;
    /** e.g. "Mon-Fri, 4:00 pm - 7:00 pm"; null for a one-off promotion */
    private String scheduleText;
}
