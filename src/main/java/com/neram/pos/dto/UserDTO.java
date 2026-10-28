package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String role;
    private Long employeeId;
    private String employeeName;
    private Boolean active;
    private Boolean canCancelOrder;
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
    private Long outletId;
    private String outletName;
    private Long tenantId;
    private Boolean canPrintBill;
    private Boolean canManageMenu;
    private Boolean canManageSettings;
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
    /** True while the account is locked after repeated failed logins. */
    private Boolean locked;
    private LocalDateTime lockedUntil;
}
