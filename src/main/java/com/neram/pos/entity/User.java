package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class User implements com.neram.pos.tenancy.TenantScoped , com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "can_take_orders", nullable = false)
    private Boolean canTakeOrders = true;

    @Column(name = "can_manage_inventory", nullable = false)
    private Boolean canManageInventory = false;

    /** May cancel or void an order that is not yet paid (paid bills go through the refund workflow). Owners and managers always may. */
    @Column(name = "can_cancel_order", nullable = false)
    private Boolean canCancelOrder = false;

    @Column(name = "can_print_bill", nullable = false)
    private Boolean canPrintBill = false;

    @Column(name = "can_manage_menu", nullable = false)
    private Boolean canManageMenu = false;

    @Column(name = "can_manage_settings", nullable = false)
    private Boolean canManageSettings = false;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Role {
        SUPER_ADMIN, ADMIN, CAFE_ADMIN, GENERAL_MANAGER, MANAGER, CASHIER, CAPTAIN, EMPLOYEE, KITCHEN
    }
}
