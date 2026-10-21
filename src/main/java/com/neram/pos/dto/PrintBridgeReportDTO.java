package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintBridgeReportDTO {
    private String bridgeId;
    private Boolean ambiguous;
    private String message;
    private Integer bytesPrinted;
}
