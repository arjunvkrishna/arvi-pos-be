package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemDTO {
    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private Boolean isVeg;
    private Boolean isAvailable;
    private Boolean active;
    private LocalDateTime createdAt;
    /** Cafe-wide price/availability; {@code price}/{@code isAvailable} above are what the caller's branch sees. */
    private BigDecimal basePrice;
    private Boolean baseAvailable;
    /** True when the caller's branch has its own price and/or availability for this item. */
    private Boolean branchOverride;
}
