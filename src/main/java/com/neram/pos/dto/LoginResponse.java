package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private String username;
    private String role;
    private Long employeeId;
    private String employeeName;
    private Boolean canCancelOrder;
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
    private Boolean canPrintBill;
    private Boolean canManageMenu;
    private Boolean canManageSettings;
    private Boolean mustChangePassword;
    private Long tenantId;
    private String tenantName;
    private String tenantCode;
    private String logoUrl;
    private Long outletId;
    private String outletName;
    private String outletCode;
    private String message;
}
