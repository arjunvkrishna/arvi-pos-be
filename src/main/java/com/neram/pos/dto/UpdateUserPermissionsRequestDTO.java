package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPermissionsRequestDTO {
    private Boolean canBill;
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
    private Boolean canManageExpenses;
    private Boolean canViewReports;
    private Long outletId;
    private Boolean canPrintBill;
    private Boolean canManageMenu;
    private Boolean canManageSettings;
}
