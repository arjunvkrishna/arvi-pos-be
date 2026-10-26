package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicMenuResponseDTO {
    private boolean enabled;
    private String message;
    private List<PublicMenuCategoryDTO> categories;
    /** The cafe's own identity, so the menu page shows its name, tagline and logo. */
    private String cafeName;
    private String tagline;
    private String logoUrl;
    private String address;
    private String phone;
    private String currencySymbol;
}
