package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPermissionsRequestDTO {
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
}
