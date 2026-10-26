package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RecipeDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecipeRequest {
        private Long menuItemId;
        private Long inventoryItemId;
        private BigDecimal quantityPerServing;
        private String unit;
        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecipeResponse {
        private Long id;
        private Long menuItemId;
        private String menuItemName;
        private Long inventoryItemId;
        private String inventoryItemName;
        private String inventoryItemUnit;
        private BigDecimal inventoryCostPerUnit;
        /** false = the ingredient is issued by hand, so selling this dish does not reduce its stock */
        private Boolean inventoryAutoDeduct;
        private BigDecimal quantityPerServing;
        private String unit;
        private Boolean active;
        private BigDecimal costPerServing;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
