package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private LocalDate birthday;
    private LocalDate anniversary;
    private String address;
    private String allergies;
    private String notes;
    private Boolean marketingConsent;
    private Integer pointsBalance;
    private LocalDate pointsExpireAt;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
