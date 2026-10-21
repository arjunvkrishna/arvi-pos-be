package com.neram.pos.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramPreviewDTO {
    private int itemCount;
    private String reportDate;
    private String formattedReport;
    private List<LowStockItemDetail> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LowStockItemDetail {
        private String name;
        private Double currentQuantity;
        private Double minQuantity;
        private String unit;
        private String supplier;
    }
}
