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
public class EmployeeDTO {
    private Long id;
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
    private LocalDateTime createdAt;
}
