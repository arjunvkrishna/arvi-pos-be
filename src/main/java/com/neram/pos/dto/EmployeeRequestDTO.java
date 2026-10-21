package com.neram.pos.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeRequestDTO {
    private String name;
    private String phone;
    private String email;
    private String role;
    private String department;
    private String designation;
    private Double basicSalary;
    private LocalDate joinedDate;
    private String address;
    private Integer paidLeavesQuota;
    private Integer casualLeavesQuota;
    private String bankName;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankBranch;
    private String bankAccountHolderName;
    private String upiId;
    private Boolean active;
    // For creating linked user account
    private String username;
    private String password;
}
