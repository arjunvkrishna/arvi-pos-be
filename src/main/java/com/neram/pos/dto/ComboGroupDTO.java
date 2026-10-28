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
public class ComboGroupDTO {
    private Long id;
    private Long comboMenuItemId;
    private String name;
    private Integer minChoices;
    private Integer maxChoices;
    private Integer sortOrder;
    private List<ComboItemDTO> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboItemDTO {
        private Long id;
        private Long menuItemId;
        private String menuItemName;
        private BigDecimal additionalPrice;
    }
}
