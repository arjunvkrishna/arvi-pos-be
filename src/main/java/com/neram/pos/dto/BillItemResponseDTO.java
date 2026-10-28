package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillItemResponseDTO {
    private Long id;
    private Long menuItemId;
    private String itemName;
    private Integer quantity;
    private Integer printedQuantity;
    private Integer deltaQuantity;
    private java.math.BigDecimal price;
    private java.math.BigDecimal subtotal;
    private String customization;
    private String updateType;
    private String updateMessage;
    private Integer previousQuantity;
    private Integer currentTotalQuantity;
    private Boolean isTakeaway;
    private String modifiersSnapshot;
    private String comboSnapshot;
}
