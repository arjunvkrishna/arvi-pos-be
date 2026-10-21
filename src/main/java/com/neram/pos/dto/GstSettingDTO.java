package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GstSettingDTO {
    private Boolean enabled;
    private String gstin;
    private Double cgstPercent;
    private Double sgstPercent;
    private Double totalGstPercent;
    private Boolean roundOffEnabled;
    private LocalDateTime updatedAt;
    private String updatedByName;
}
