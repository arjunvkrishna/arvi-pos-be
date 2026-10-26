package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequestDTO {
    private String username;
    private String password;
    private String role; // ADMIN, GENERAL_MANAGER, MANAGER, CASHIER, CAPTAIN, EMPLOYEE, KITCHEN
    private Long employeeId; // Optional: link to an existing employee
    private Long outletId; // Specific branch outlet assignment
    private Boolean canBill;
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
    private Boolean canManageExpenses;
    private Boolean canViewReports;
    private Boolean canPrintBill;
    private Boolean canManageMenu;
    private Boolean canManageSettings;
}
