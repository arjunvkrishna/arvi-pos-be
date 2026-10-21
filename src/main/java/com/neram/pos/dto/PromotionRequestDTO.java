package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionRequestDTO {
    private String name;
    private Double discountPercentage;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Boolean isActive;
    private String description;
}
