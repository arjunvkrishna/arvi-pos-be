package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Result shapes for the menu price bulk import (preview and apply). */
public class MenuPriceImportDTO {

    public static final String CHANGED = "CHANGED";
    public static final String UNCHANGED = "UNCHANGED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID = "INVALID";
    public static final String DUPLICATE = "DUPLICATE";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        /** 1-based position of the entry in the uploaded file. */
        private int index;
        private Long id;
        private String name;
        private String categoryName;
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private String status;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private boolean applied;
        private int total;
        private int changed;
        private int unchanged;
        private int notFound;
        private int invalid;
        private List<Row> rows;
    }
}
