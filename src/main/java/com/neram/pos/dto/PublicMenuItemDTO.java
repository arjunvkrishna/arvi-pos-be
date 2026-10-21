package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicMenuItemDTO {
    private Long id;
    private String name;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private Boolean isVeg;
    private Boolean isAvailable;
}
