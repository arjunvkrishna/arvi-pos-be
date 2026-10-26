package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettingsDTO {
    private Long id;
    private String name;
    private String code;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String gstNumber;
    private String logoUrl;
    private String tagline;
    private String subscriptionPlan;
    private Integer maxOutlets;
    private String currency;
    private String currencySymbol;
}
