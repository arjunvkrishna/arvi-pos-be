package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    // User Account Info
    private Long userId;
    private String username;
    private String role;
    private Boolean active;
    private Boolean mustChangePassword;
    private Boolean canCancelOrder;
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
    private Long tenantId;
    private String tenantName;
    private Long outletId;
    private String outletName;
    private String cafeLogoUrl;
    private Boolean canPrintBill;
    private Boolean canManageMenu;
    private Boolean canManageSettings;
    private LocalDateTime accountCreatedAt;
    private com.neram.pos.plan.PlanInfoDTO plan;

    // Linked Employee Details (if any)
    private Long employeeId;
    private String employeeName;
    private String phone;
    private String email;
    private String department;
    private String designation;
    private Double basicSalary;
    private LocalDate joinedDate;
    private String address;
    private Integer paidLeavesQuota;
    private Integer casualLeavesQuota;

    // Employee Bank Account Details (Read-only view)
    private String bankName;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankBranch;
    private String bankAccountHolderName;
    private String upiId;
}
