package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovingDishDTO {
    private Long menuItemId;
    private String dishName;
    private Long totalQuantity;
    private Double totalRevenue;
}
