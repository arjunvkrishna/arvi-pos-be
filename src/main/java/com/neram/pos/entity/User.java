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
public class User {

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

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Role {
        SUPER_ADMIN, ADMIN, CAFE_ADMIN, MANAGER, EMPLOYEE, KITCHEN
    }
}
