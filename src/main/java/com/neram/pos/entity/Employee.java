package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "employees")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class Employee implements com.neram.pos.tenancy.TenantScoped , com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String email;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    private String department;
    private String designation;

    @Column(name = "basic_salary")
    private Double basicSalary = 0.0;

    @Column(name = "joined_date")
    private LocalDate joinedDate;

    private String address;

    @Column(name = "paid_leaves_quota")
    private Integer paidLeavesQuota = 12;

    @Column(name = "casual_leaves_quota")
    private Integer casualLeavesQuota = 12;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_ifsc")
    private String bankIfsc;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "bank_account_holder_name")
    private String bankAccountHolderName;

    @Column(name = "upi_id")
    private String upiId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Role {
        ADMIN, GENERAL_MANAGER, MANAGER, CASHIER, CAPTAIN, EMPLOYEE, KITCHEN
    }
}
