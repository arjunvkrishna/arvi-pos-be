package com.neram.pos.dto;

import lombok.Data;

@Data
public class ChangeLocationRequestDTO {
    private String orderType;
    private String tableNumber;
    private String customerName;
    private String takeawayToken;
}
