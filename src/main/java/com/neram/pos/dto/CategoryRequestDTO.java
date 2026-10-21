package com.neram.pos.dto;

import lombok.Data;

@Data
public class CategoryRequestDTO {
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
    private Boolean active;
}
