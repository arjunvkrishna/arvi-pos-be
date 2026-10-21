package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCafeRequest {
    private String name;
    private String code;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String subscriptionPlan;
    private Integer maxOutlets;
    private String adminUsername;
    private String adminPassword;
    private String adminFullName;
}
