package com.neram.pos.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the kitchen display needs about an open order, and nothing else: no prices, totals, payments or customer details.
 */
@Data
@Builder
public class KitchenTicketDTO {
    private Long id;
    private String billNumber;
    private String orderType;
    private String tableNumber;
    private String outdoorToken;
    private String takeawayToken;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime kotPrintedAt;
    private List<Line> items;

    @Data
    @Builder
    public static class Line {
        private String name;
        private Integer quantity;
        /** How many of them an earlier kitchen ticket already covered; the rest are new in this round. */
        private Integer printedQuantity;
        private String note;
        /** Names of chosen add-ons and combo choices, e.g. "Extra cheese". */
        private List<String> extras;
        private Boolean takeaway;
    }
}
