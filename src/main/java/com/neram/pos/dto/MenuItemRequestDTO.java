package com.neram.pos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MenuItemRequestDTO {
    private String name;
    private Long categoryId;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private Boolean isVeg;
    private Boolean isAvailable;
    private Boolean active;
}
