package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrinterSettingDTO {
    private String host;
    private Integer port;
    private Boolean enabled;

    // Dedicated Bill Printer Fields
    private String billHost;
    private Integer billPort;
    private Boolean billEnabled;

    // Dedicated KOT Printer Fields
    private String kotHost;
    private Integer kotPort;
    private Boolean kotEnabled;

    // Dedicated Juice KOT Printer Fields
    private String juiceKotHost;
    private Integer juiceKotPort;
    private Boolean juiceKotEnabled;

    private Boolean autoCut;
    private Boolean logoEnabled;
    private Boolean paymentQrEnabled;
    private Integer paperWidthMm;
    private String headerTitle;
    private String subheader;
    private String subheader2;
    private String footerMessage;
    private String kotHeaderTitle;
    private String kotSubheader;
    private String kotFooterMessage;
    private String juiceKotHeaderTitle;
    private String juiceKotSubheader;
    private String juiceKotFooterMessage;
    private String juiceKotCategoryIds;
    /** Wi-Fi names (comma-separated) on which the staff app prints directly to the LAN printers; empty = off. */
    private String directPrintSsids;
    private Integer logoSize;
    private Integer logoGap;
    private Integer lineSpacing;
    private Integer headerSpacing;
    private Integer footerSpacing;
    private String fontSize;
    private String printStyle;
    private LocalDateTime updatedAt;
}
