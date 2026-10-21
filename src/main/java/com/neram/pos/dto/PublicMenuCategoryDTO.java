package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicMenuCategoryDTO {
    private Long id;
    private String name;
    private String icon;
    private Integer sortOrder;
    private List<PublicMenuItemDTO> items;
}
