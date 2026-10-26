package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDecreaseRequestDTO {
    private Double quantity;
    private String reason;
    /** true = quantity is a number of packs (e.g. cases); default is the stock unit. */
    private Boolean inPacks;
    /** USAGE (default), EXPIRED, SPOILED, BREAKAGE, STAFF_MEAL or OTHER */
    private String reasonType;
    /** true = allowed to take the stock below zero (the purchase was not entered yet); it shows as a negative number until it is added */
    private Boolean allowNegative;
}
