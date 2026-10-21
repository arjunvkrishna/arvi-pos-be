package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "printer_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrinterSetting {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String host = "192.168.1.37";

    @Column(nullable = false)
    @Builder.Default
    private Integer port = 9100;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    // Dedicated Bill Printer Configuration
    @Column(name = "bill_host", nullable = false, length = 100)
    @Builder.Default
    private String billHost = "192.168.1.37";

    @Column(name = "bill_port", nullable = false)
    @Builder.Default
    private Integer billPort = 9100;

    @Column(name = "bill_enabled", nullable = false)
    @Builder.Default
    private Boolean billEnabled = true;

    // Dedicated KOT Printer Configuration
    @Column(name = "kot_host", nullable = false, length = 100)
    @Builder.Default
    private String kotHost = "192.168.1.47";

    @Column(name = "kot_port", nullable = false)
    @Builder.Default
    private Integer kotPort = 9100;

    @Column(name = "kot_enabled", nullable = false)
    @Builder.Default
    private Boolean kotEnabled = true;

    // Dedicated Juice KOT Printer Configuration
    @Column(name = "juice_kot_host", nullable = false, length = 100)
    @Builder.Default
    private String juiceKotHost = "192.168.1.42";

    @Column(name = "juice_kot_port", nullable = false)
    @Builder.Default
    private Integer juiceKotPort = 9100;

    @Column(name = "juice_kot_enabled", nullable = false)
    @Builder.Default
    private Boolean juiceKotEnabled = true;

    @Column(name = "auto_cut", nullable = false)
    @Builder.Default
    private Boolean autoCut = true;

    @Column(name = "paper_width_mm", nullable = false)
    @Builder.Default
    private Integer paperWidthMm = 80;

    @Column(name = "header_title", nullable = false, length = 100)
    @Builder.Default
    private String headerTitle = "NERAM";

    @Column(name = "logo_enabled", nullable = false)
    @Builder.Default
    private Boolean logoEnabled = true;

    @Column(length = 500)
    @Builder.Default
    private String subheader = "Where time tastes better";

    @Column(name = "subheader2", length = 500)
    @Builder.Default
    private String subheader2 = "Infopark Expy, Rajagiri Valley, Chittethukara, Kakkanad, Kochi, Keralam 682037";

    @Column(name = "footer_message", length = 500)
    @Builder.Default
    private String footerMessage = "THANK YOU! VISIT AGAIN";

    @Column(name = "kot_header_title", length = 150)
    @Builder.Default
    private String kotHeaderTitle = "*** KITCHEN ORDER ***";

    @Column(name = "kot_subheader", length = 500)
    @Builder.Default
    private String kotSubheader = "NERAM - MAIN KITCHEN";

    @Column(name = "kot_footer_message", length = 150)
    @Builder.Default
    private String kotFooterMessage = "--- END OF KOT ---";

    // Dedicated Juice KOT Template Design Configuration
    @Column(name = "juice_kot_header_title", length = 150)
    @Builder.Default
    private String juiceKotHeaderTitle = "*** JUICE ORDER ***";

    @Column(name = "juice_kot_subheader", length = 500)
    @Builder.Default
    private String juiceKotSubheader = "NERAM - JUICE BAR";

    @Column(name = "juice_kot_footer_message", length = 150)
    @Builder.Default
    private String juiceKotFooterMessage = "--- END OF JUICE KOT ---";

    // Category routing configuration: comma-separated list of category IDs routed to Juice KOT
    @Column(name = "juice_kot_category_ids", length = 1000)
    @Builder.Default
    private String juiceKotCategoryIds = "";

    @Column(name = "logo_size")
    @Builder.Default
    private Integer logoSize = 130;

    @Column(name = "logo_gap")
    @Builder.Default
    private Integer logoGap = 2;

    @Column(name = "line_spacing")
    @Builder.Default
    private Integer lineSpacing = 24;

    @Column(name = "header_spacing")
    @Builder.Default
    private Integer headerSpacing = 1;

    @Column(name = "footer_spacing")
    @Builder.Default
    private Integer footerSpacing = 1;

    @Column(name = "font_size", length = 50)
    @Builder.Default
    private String fontSize = "DOUBLE_HEIGHT";

    @Column(name = "print_style", length = 50)
    @Builder.Default
    private String printStyle = "CRISP_DARK";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
        if (id == null) id = 1L;
        if (billHost == null || billHost.isBlank()) billHost = (host != null && !host.isBlank()) ? host : "192.168.1.37";
        if (billPort == null || billPort <= 0) billPort = (port != null && port > 0) ? port : 9100;
        if (billEnabled == null) billEnabled = (enabled != null) ? enabled : true;

        // Keep legacy host, port, enabled in parity with bill printer
        host = billHost;
        port = billPort;
        enabled = billEnabled;

        if (kotHost == null || kotHost.isBlank()) kotHost = "192.168.1.37";
        if (kotPort == null || kotPort <= 0) kotPort = 9100;
        if (kotEnabled == null) kotEnabled = true;

        if (juiceKotHost == null || juiceKotHost.isBlank()) juiceKotHost = "192.168.1.42";
        if (juiceKotPort == null || juiceKotPort <= 0) juiceKotPort = 9100;
        if (juiceKotEnabled == null) juiceKotEnabled = true;

        if (juiceKotHeaderTitle == null || juiceKotHeaderTitle.isBlank()) juiceKotHeaderTitle = "*** JUICE ORDER ***";
        if (juiceKotSubheader == null || juiceKotSubheader.isBlank()) juiceKotSubheader = "NERAM - JUICE BAR";
        if (juiceKotFooterMessage == null || juiceKotFooterMessage.isBlank()) juiceKotFooterMessage = "--- END OF JUICE KOT ---";
        if (juiceKotCategoryIds == null) juiceKotCategoryIds = "";

        if (autoCut == null) autoCut = true;
        if (paperWidthMm == null) paperWidthMm = 80;
        if (headerTitle == null) headerTitle = "NERAM";
        if (subheader == null) subheader = "Where time tastes better";
        if (footerMessage == null) footerMessage = "THANK YOU! VISIT AGAIN";

        if (logoSize == null || logoSize <= 0) logoSize = 130;
        if (logoGap == null || logoGap < 0) logoGap = 2;
        if (lineSpacing == null || lineSpacing <= 0) lineSpacing = 24;
        if (headerSpacing == null || headerSpacing < 0) headerSpacing = 1;
        if (footerSpacing == null || footerSpacing < 0) footerSpacing = 1;
        if (fontSize == null || fontSize.isBlank()) fontSize = "DOUBLE_HEIGHT";
        if (printStyle == null || printStyle.isBlank()) printStyle = "CRISP_DARK";
    }
}
