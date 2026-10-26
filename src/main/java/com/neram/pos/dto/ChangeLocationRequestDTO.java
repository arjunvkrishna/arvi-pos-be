package com.neram.pos.dto;

import lombok.Data;

@Data
public class ChangeLocationRequestDTO {
    private String orderType;
    private String tableNumber;
    private String customerName;
    private String takeawayToken;
    /** Optional free-text reason (e.g. "AC not working"), recorded in the audit log. */
    private String reason;
    /** When the target table already has an active order, merge this order into it instead of rejecting the move. */
    private Boolean mergeIfOccupied;
}
