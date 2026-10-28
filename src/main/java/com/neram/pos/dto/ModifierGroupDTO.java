package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierGroupDTO {
    private Long id;
    private String name;
    private String description;
    private Integer minSelections;
    private Integer maxSelections;
    private Boolean isActive;
    private List<ModifierDTO> modifiers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModifierDTO {
        private Long id;
        private Long groupId;
        private String name;
        private BigDecimal priceAdjustment;
        private Boolean isDefault;
        private Boolean isActive;
        private Integer sortOrder;
        private Long inventoryItemId;
        private BigDecimal inventoryQuantity;
    }
}
