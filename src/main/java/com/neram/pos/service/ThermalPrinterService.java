package com.neram.pos.service;

import com.neram.pos.dto.PrintBridgeAckDTO;
import com.neram.pos.dto.PrintBridgeHeartbeatDTO;
import com.neram.pos.dto.PrintBridgeJobDTO;
import com.neram.pos.dto.PrintResultDTO;
import com.neram.pos.dto.PrinterSettingDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrintJobStatus;
import com.neram.pos.entity.PrinterSetting;
import com.neram.pos.entity.User;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.PrintJobRepository;
import com.neram.pos.repository.PrinterSettingRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.neram.pos.dto.BillItemResponseDTO;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalPrinterService {

    // Printer-ready (ESC/POS) versions of each cafe's/branch's uploaded logo and payment QR, keyed by cafe, branch,
    // upload time, paper width and size, so a re-upload takes effect at once and cafes never share a picture.
    private final java.util.Map<String, byte[]> imageRasterCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, byte[]> eldest) {
                    return size() > 200;
                }
            });

    private BrandingService brandingService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setBrandingService(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    /** Optional: tests build this service without one. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditLogService auditLogService;

    /** Optional: the loyalty block on the bill needs them. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.neram.pos.repository.CustomerRepository customerRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LoyaltySettingsService loyaltySettings;

    private final PrinterSettingRepository printerSettingRepository;
    private final BillRepository billRepository;
    private final PrintJobRepository printJobRepository;
    private final UserRepository userRepository;
    private final PrintBridgeService printBridgeService;
    private final GstSettingService gstSettingService;
    private final com.neram.pos.config.DatabaseSchemaEnsurer databaseSchemaEnsurer;
    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;

    @Value("${neram.printer.host:}")
    private String envHost;

    @Value("${neram.printer.port:0}")
    private int envPort;

    @Value("${neram.printer.enabled:#{null}}")
    private Boolean envEnabled;

    @Value("${neram.printer.bill-host:}")
    private String envBillHost;

    @Value("${neram.printer.bill-port:0}")
    private int envBillPort;

    @Value("${neram.printer.bill-enabled:#{null}}")
    private Boolean envBillEnabled;

    @Value("${neram.printer.kot-host:}")
    private String envKotHost;

    @Value("${neram.printer.kot-port:0}")
    private int envKotPort;

    @Value("${neram.printer.kot-enabled:#{null}}")
    private Boolean envKotEnabled;

    @Value("${neram.printer.juice-kot-host:}")
    private String envJuiceKotHost;

    @Value("${neram.printer.juice-kot-port:0}")
    private int envJuiceKotPort;

    @Value("${neram.printer.juice-kot-enabled:#{null}}")
    private Boolean envJuiceKotEnabled;

    @Value("${neram.printer.bridge-token:}")
    private String configuredBridgeToken;

    /**
     * Whether this server may open sockets to printer addresses itself. Off for the multi-cafe cloud service: a
     * cafe's printer address is user input, so connecting from the server would let a cafe admin probe the hosting
     * network. Cafes print through their own bridge on their own LAN. Only for a single-site, on-premise install.
     */
    @Value("${neram.printer.direct-enabled:false}")
    private boolean directPrintEnabled;

    private static final int LINE_WIDTH_80MM = 48; // standard 80mm ESC/POS characters per line (Font A)
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    // ============================================================
    // ESC/POS COMMAND CONSTANTS
    // ============================================================
    private static final byte[] CMD_INIT = new byte[]{0x1B, 0x40};               // ESC @
    private static final byte[] CMD_ALIGN_LEFT = new byte[]{0x1B, 0x61, 0x00};    // ESC a 0
    private static final byte[] CMD_ALIGN_CENTER = new byte[]{0x1B, 0x61, 0x01};  // ESC a 1
    private static final byte[] CMD_ALIGN_RIGHT = new byte[]{0x1B, 0x61, 0x02};   // ESC a 2
    private static final byte[] CMD_BOLD_ON = new byte[]{0x1B, 0x45, 0x01};       // ESC E 1
    private static final byte[] CMD_BOLD_OFF = new byte[]{0x1B, 0x45, 0x00};      // ESC E 0
    private static final byte[] CMD_TXT_NORMAL = new byte[]{0x1D, 0x21, 0x00};    // GS ! 0x00
    private static final byte[] CMD_TXT_DOUBLE_H = new byte[]{0x1D, 0x21, 0x01};  // GS ! 0x01 (Double Height)
    private static final byte[] CMD_TXT_DOUBLE_W_H = new byte[]{0x1D, 0x21, 0x11};// GS ! 0x11 (Double Width & Height)
    private static final byte[] CMD_CUT_PAPER = new byte[]{0x1D, 0x56, 0x42, 0x00}; // GS V 66 0 (Feed and cut)
    private static final byte[] CMD_INVERT_ON = new byte[]{0x1D, 0x42, 0x01};    // GS B 1 (White on Black Reverse ON)
    private static final byte[] CMD_INVERT_OFF = new byte[]{0x1D, 0x42, 0x00};   // GS B 0 (White on Black Reverse OFF)

    // Env printer overrides are a single-site feature. With one database per cafe they would be applied to whichever
    // cafe happened to call first, so they are disabled; each cafe configures its printers in its own settings.
    private volatile boolean envInitialized = true;

    private PrinterSetting buildDefaultSettings(Long id, Long outletId) {
        String initialBillHost = (envBillHost != null && !envBillHost.isBlank()) ? envBillHost.trim()
                : ((envHost != null && !envHost.isBlank()) ? envHost.trim() : "192.168.1.47");
        int initialBillPort = envBillPort > 0 ? envBillPort : (envPort > 0 ? envPort : 9100);
        boolean initialBillEnabled = envBillEnabled != null ? envBillEnabled : (envEnabled != null ? envEnabled : true);

        String initialKotHost = (envKotHost != null && !envKotHost.isBlank()) ? envKotHost.trim() : "192.168.1.41";
        int initialKotPort = envKotPort > 0 ? envKotPort : 9100;
        boolean initialKotEnabled = envKotEnabled != null ? envKotEnabled : true;

        PrinterSetting setting = PrinterSetting.builder()
                .id(id)
                .outletId(outletId)
                .host(initialBillHost)
                .port(initialBillPort)
                .enabled(initialBillEnabled)
                .billHost(initialBillHost)
                .billPort(initialBillPort)
                .billEnabled(initialBillEnabled)
                .kotHost(initialKotHost)
                .kotPort(initialKotPort)
                .kotEnabled(initialKotEnabled)
                .autoCut(true)
                .logoEnabled(true)
                .paymentQrEnabled(true)
                .paperWidthMm(80)
                .headerTitle("NERAM")
                .subheader("Where time tastes better")
                .subheader2("Infopark Expy, Rajagiri Valley, Chittethukara, Kakkanad, Kochi, Keralam 682037")
                .footerMessage("THANK YOU! VISIT AGAIN")
                .kotHeaderTitle("*** KITCHEN ORDER ***")
                .kotSubheader("NERAM - MAIN KITCHEN")
                .kotFooterMessage("--- END OF KOT ---")
                .juiceKotCategoryIds("")
                .logoSize(130)
                .logoGap(2)
                .lineSpacing(24)
                .headerSpacing(1)
                .footerSpacing(1)
                .fontSize("DOUBLE_HEIGHT")
                .printStyle("CRISP_DARK")
                .updatedAt(LocalDateTime.now())
                .build();
        try {
            return printerSettingRepository.save(setting);
        } catch (Exception e) {
            log.warn("Could not save initial default printer setting row: {}. Using in-memory fallback.", e.getMessage());
            return setting;
        }
    }

    /**
     * Get active printer settings, synchronizing with environment variables dynamically
     * so client configuration in .env always takes precedence on startup/change.
     * Contains automatic self-healing if columns or tables were missing in PostgreSQL.
     */
    /**
     * The printer settings row of the branch in context (row id = outlet id). A branch without one starts as a copy
     * of the first branch's settings. With no branch selected (whole-cafe view) the first branch's row is used.
     */
    private PrinterSetting loadBranchSettings() {
        Long outletId = com.neram.pos.access.OutletContext.current().filter(id -> id != com.neram.pos.access.OutletContext.NO_OUTLET).orElse(null);
        if (outletId == null) {
            return printerSettingRepository.findById(1L)
                    .or(() -> printerSettingRepository.findFirstByOrderByIdAsc())
                    .orElseGet(() -> buildDefaultSettings(1L, null));
        }
        return printerSettingRepository.findById(outletId).orElseGet(() -> {
            PrinterSetting template = com.neram.pos.access.OutletContext.callUnrestricted(
                    () -> printerSettingRepository.findFirstByOrderByIdAsc().orElse(null));
            if (template == null) {
                return buildDefaultSettings(outletId, outletId);
            }
            PrinterSetting copy = new PrinterSetting();
            org.springframework.beans.BeanUtils.copyProperties(template, copy);
            copy.setId(outletId);
            copy.setOutletId(outletId);
            copy.setUpdatedAt(LocalDateTime.now());
            return printerSettingRepository.save(copy);
        });
    }

    public PrinterSetting getSettings() {
        PrinterSetting s;
        try {
            s = loadBranchSettings();
        } catch (Exception ex) {
            log.error("Failed to query printer_settings (possible schema mismatch in DB): {}. Performing emergency self-healing...", ex.getMessage());
            databaseSchemaEnsurer.ensureSchema();
            try {
                s = loadBranchSettings();
            } catch (Exception ex2) {
                log.error("Emergency self-heal query retry failed: {}. Falling back to transient in-memory settings.", ex2.getMessage());
                return buildDefaultSettings(1L, null);
            }
        }

        // Ensure billHost and kotHost are initialized for legacy rows
        boolean changed = false;
        if (s.getBillHost() == null || s.getBillHost().isBlank()) {
            s.setBillHost(envBillHost != null && !envBillHost.isBlank() ? envBillHost.trim()
                    : (s.getHost() != null && !s.getHost().isBlank() ? s.getHost() : "192.168.1.47"));
            changed = true;
        }
        if (s.getBillPort() == null || s.getBillPort() <= 0) {
            s.setBillPort(envBillPort > 0 ? envBillPort : (s.getPort() != null && s.getPort() > 0 ? s.getPort() : 9100));
            changed = true;
        }
        if (s.getBillEnabled() == null) {
            s.setBillEnabled(envBillEnabled != null ? envBillEnabled : (s.getEnabled() != null ? s.getEnabled() : true));
            changed = true;
        }
        if (s.getKotHost() == null || s.getKotHost().isBlank()) {
            s.setKotHost(envKotHost != null && !envKotHost.isBlank() ? envKotHost.trim() : "192.168.1.41");
            changed = true;
        }
        if (s.getKotPort() == null || s.getKotPort() <= 0) {
            s.setKotPort(envKotPort > 0 ? envKotPort : 9100);
            changed = true;
        }
        if (s.getKotEnabled() == null) {
            s.setKotEnabled(envKotEnabled != null ? envKotEnabled : true);
            changed = true;
        }

        // Dynamic synchronization with Environment on initialization or when .env value changes
        if (!envInitialized) {
            if (envBillHost != null && !envBillHost.isBlank() && !envBillHost.trim().equals(s.getBillHost())) {
                log.info("Overriding Bill Printer Host from env: {} -> {}", s.getBillHost(), envBillHost.trim());
                s.setBillHost(envBillHost.trim());
                s.setHost(envBillHost.trim());
                changed = true;
            } else if (envHost != null && !envHost.isBlank() && !envHost.trim().equals(s.getHost())) {
                s.setHost(envHost.trim());
                s.setBillHost(envHost.trim());
                changed = true;
            }

            if (envBillPort > 0 && !Integer.valueOf(envBillPort).equals(s.getBillPort())) {
                s.setBillPort(envBillPort);
                s.setPort(envBillPort);
                changed = true;
            } else if (envPort > 0 && !Integer.valueOf(envPort).equals(s.getPort())) {
                s.setPort(envPort);
                s.setBillPort(envPort);
                changed = true;
            }

            if (envBillEnabled != null && !envBillEnabled.equals(s.getBillEnabled())) {
                s.setBillEnabled(envBillEnabled);
                s.setEnabled(envBillEnabled);
                changed = true;
            } else if (envEnabled != null && !envEnabled.equals(s.getEnabled())) {
                s.setEnabled(envEnabled);
                s.setBillEnabled(envEnabled);
                changed = true;
            }

            if (envKotHost != null && !envKotHost.isBlank() && !envKotHost.trim().equals(s.getKotHost())) {
                log.info("Overriding KOT Printer Host from env: {} -> {}", s.getKotHost(), envKotHost.trim());
                s.setKotHost(envKotHost.trim());
                changed = true;
            }
            if (envKotPort > 0 && !Integer.valueOf(envKotPort).equals(s.getKotPort())) {
                s.setKotPort(envKotPort);
                changed = true;
            }
            if (envKotEnabled != null && !envKotEnabled.equals(s.getKotEnabled())) {
                s.setKotEnabled(envKotEnabled);
                changed = true;
            }

            if (envJuiceKotHost != null && !envJuiceKotHost.isBlank() && !envJuiceKotHost.trim().equals(s.getJuiceKotHost())) {
                log.info("Overriding Juice KOT Printer Host from env: {} -> {}", s.getJuiceKotHost(), envJuiceKotHost.trim());
                s.setJuiceKotHost(envJuiceKotHost.trim());
                changed = true;
            }
            if (envJuiceKotPort > 0 && !Integer.valueOf(envJuiceKotPort).equals(s.getJuiceKotPort())) {
                s.setJuiceKotPort(envJuiceKotPort);
                changed = true;
            }
            if (envJuiceKotEnabled != null && !envJuiceKotEnabled.equals(s.getJuiceKotEnabled())) {
                s.setJuiceKotEnabled(envJuiceKotEnabled);
                changed = true;
            }

            envInitialized = true;
        }

        if (changed) {
            s.setUpdatedAt(LocalDateTime.now());
            try {
                s = printerSettingRepository.save(s);
                log.info("Printer settings dynamically synchronized: bill={}:{} (enabled={}), kot={}:{} (enabled={}), juiceKot={}:{} (enabled={})",
                        s.getBillHost(), s.getBillPort(), s.getBillEnabled(),
                        s.getKotHost(), s.getKotPort(), s.getKotEnabled(),
                        s.getJuiceKotHost(), s.getJuiceKotPort(), s.getJuiceKotEnabled());
            } catch (Exception e) {
                log.warn("Could not save dynamically synchronized printer settings: {}", e.getMessage());
            }
        }

        return s;
    }

    public PrinterSettingDTO getSettingsDTO() {
        PrinterSetting s = getSettings();
        String effectiveBillHost = (s.getBillHost() != null && !s.getBillHost().isBlank()) ? s.getBillHost()
                : (s.getHost() != null && !s.getHost().isBlank() ? s.getHost()
                : ((envBillHost != null && !envBillHost.isBlank()) ? envBillHost.trim() : "192.168.1.47"));
        String effectiveKotHost = (s.getKotHost() != null && !s.getKotHost().isBlank()) ? s.getKotHost()
                : ((envKotHost != null && !envKotHost.isBlank()) ? envKotHost.trim() : "192.168.1.41");
        String effectiveJuiceKotHost = (s.getJuiceKotHost() != null && !s.getJuiceKotHost().isBlank()) ? s.getJuiceKotHost()
                : ((envJuiceKotHost != null && !envJuiceKotHost.isBlank()) ? envJuiceKotHost.trim() : "192.168.1.42");

        return PrinterSettingDTO.builder()
                .host(effectiveBillHost)
                .port(s.getPort() != null && s.getPort() > 0 ? s.getPort() : 9100)
                .enabled(s.getEnabled() != null ? s.getEnabled() : true)
                .billHost(effectiveBillHost)
                .billPort(s.getBillPort() != null && s.getBillPort() > 0 ? s.getBillPort() : 9100)
                .billEnabled(s.getBillEnabled() != null ? s.getBillEnabled() : true)
                .kotHost(effectiveKotHost)
                .kotPort(s.getKotPort() != null && s.getKotPort() > 0 ? s.getKotPort() : 9100)
                .kotEnabled(s.getKotEnabled() != null ? s.getKotEnabled() : true)
                .juiceKotHost(effectiveJuiceKotHost)
                .juiceKotPort(s.getJuiceKotPort() != null && s.getJuiceKotPort() > 0 ? s.getJuiceKotPort() : 9100)
                .juiceKotEnabled(s.getJuiceKotEnabled() != null ? s.getJuiceKotEnabled() : true)
                .autoCut(s.getAutoCut())
                .logoEnabled(s.getLogoEnabled() != null ? s.getLogoEnabled() : true)
                .paymentQrEnabled(s.getPaymentQrEnabled() != null ? s.getPaymentQrEnabled() : true)
                .paperWidthMm(s.getPaperWidthMm())
                .headerTitle(s.getHeaderTitle())
                .subheader(s.getSubheader())
                .subheader2(s.getSubheader2())
                .footerMessage(s.getFooterMessage())
                .kotHeaderTitle(s.getKotHeaderTitle())
                .kotSubheader(s.getKotSubheader())
                .kotFooterMessage(s.getKotFooterMessage())
                .juiceKotHeaderTitle(s.getJuiceKotHeaderTitle())
                .juiceKotSubheader(s.getJuiceKotSubheader())
                .juiceKotFooterMessage(s.getJuiceKotFooterMessage())
                .juiceKotCategoryIds(s.getJuiceKotCategoryIds() != null ? s.getJuiceKotCategoryIds() : "")
                .directPrintSsids(s.getDirectPrintSsids() != null ? s.getDirectPrintSsids() : "")
                .logoSize(s.getLogoSize() != null && s.getLogoSize() > 0 ? s.getLogoSize() : 130)
                .logoGap(s.getLogoGap() != null && s.getLogoGap() >= 0 ? s.getLogoGap() : 2)
                .lineSpacing(s.getLineSpacing() != null && s.getLineSpacing() > 0 ? s.getLineSpacing() : 24)
                .headerSpacing(s.getHeaderSpacing() != null && s.getHeaderSpacing() >= 0 ? s.getHeaderSpacing() : 1)
                .footerSpacing(s.getFooterSpacing() != null && s.getFooterSpacing() >= 0 ? s.getFooterSpacing() : 1)
                .fontSize(s.getFontSize() != null && !s.getFontSize().isBlank() ? s.getFontSize() : "DOUBLE_HEIGHT")
                .printStyle(s.getPrintStyle() != null && !s.getPrintStyle().isBlank() ? s.getPrintStyle() : "CRISP_DARK")
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    /** Saves printer settings. The cafe Wi-Fi for direct printing is an admin decision: anyone else may resend the current value, not change it. */
    public PrinterSettingDTO updateSettings(PrinterSettingDTO dto, boolean isAdmin) {
        String before = DevicePrintScope.cleanSsidList(getSettings().getDirectPrintSsids());
        boolean sent = dto.getDirectPrintSsids() != null;
        String after = sent ? DevicePrintScope.cleanSsidList(dto.getDirectPrintSsids()) : before;
        boolean changing = sent && !after.equals(before);
        if (changing && !isAdmin) {
            audit("PRINTER_WIFI_CHANGE_DENIED", before, after);
            throw new org.springframework.security.access.AccessDeniedException("Only an admin can change the cafe Wi-Fi used for direct printing.");
        }
        PrinterSettingDTO saved = updateSettings(dto);
        if (changing) audit("PRINTER_WIFI_CHANGED", before, after);
        return saved;
    }

    /** The audit log is a record, never a reason for a setting not to save. */
    private void audit(String action, String oldValue, String newValue) {
        if (auditLogService == null) return;
        try { auditLogService.log(action, "PRINTER_SETTING", 1L, oldValue, newValue); }
        catch (Exception e) { log.warn("Could not write audit entry {}: {}", action, e.getMessage()); }
    }

    @Transactional
    public PrinterSettingDTO updateSettings(PrinterSettingDTO dto) {
        PrinterSetting s = getSettings();
        // Bill printer
        if (dto.getBillHost() != null && !dto.getBillHost().isBlank()) {
            s.setBillHost(dto.getBillHost().trim());
            s.setHost(dto.getBillHost().trim());
        } else if (dto.getHost() != null && !dto.getHost().isBlank()) {
            s.setHost(dto.getHost().trim());
            s.setBillHost(dto.getHost().trim());
        }

        if (dto.getBillPort() != null && dto.getBillPort() > 0) {
            s.setBillPort(dto.getBillPort());
            s.setPort(dto.getBillPort());
        } else if (dto.getPort() != null && dto.getPort() > 0) {
            s.setPort(dto.getPort());
            s.setBillPort(dto.getPort());
        }

        if (dto.getBillEnabled() != null) {
            s.setBillEnabled(dto.getBillEnabled());
            s.setEnabled(dto.getBillEnabled());
        } else if (dto.getEnabled() != null) {
            s.setEnabled(dto.getEnabled());
            s.setBillEnabled(dto.getEnabled());
        }

        // KOT printer
        if (dto.getKotHost() != null && !dto.getKotHost().isBlank()) {
            s.setKotHost(dto.getKotHost().trim());
        }
        if (dto.getKotPort() != null && dto.getKotPort() > 0) {
            s.setKotPort(dto.getKotPort());
        }
        if (dto.getKotEnabled() != null) {
            s.setKotEnabled(dto.getKotEnabled());
        }

        // Juice KOT printer
        if (dto.getJuiceKotHost() != null && !dto.getJuiceKotHost().isBlank()) {
            s.setJuiceKotHost(dto.getJuiceKotHost().trim());
        }
        if (dto.getJuiceKotPort() != null && dto.getJuiceKotPort() > 0) {
            s.setJuiceKotPort(dto.getJuiceKotPort());
        }
        if (dto.getJuiceKotEnabled() != null) {
            s.setJuiceKotEnabled(dto.getJuiceKotEnabled());
        }

        if (dto.getAutoCut() != null) s.setAutoCut(dto.getAutoCut());
        if (dto.getLogoEnabled() != null) s.setLogoEnabled(dto.getLogoEnabled());
        if (dto.getPaymentQrEnabled() != null) s.setPaymentQrEnabled(dto.getPaymentQrEnabled());
        if (dto.getPaperWidthMm() != null && dto.getPaperWidthMm() > 0) s.setPaperWidthMm(dto.getPaperWidthMm());
        if (dto.getHeaderTitle() != null) s.setHeaderTitle(dto.getHeaderTitle().trim());
        if (dto.getSubheader() != null) s.setSubheader(dto.getSubheader().trim());
        if (dto.getSubheader2() != null) s.setSubheader2(dto.getSubheader2().trim());
        if (dto.getFooterMessage() != null) s.setFooterMessage(dto.getFooterMessage().trim());
        if (dto.getKotHeaderTitle() != null) s.setKotHeaderTitle(dto.getKotHeaderTitle().trim());
        if (dto.getKotSubheader() != null) s.setKotSubheader(dto.getKotSubheader().trim());
        if (dto.getKotFooterMessage() != null) s.setKotFooterMessage(dto.getKotFooterMessage().trim());
        if (dto.getJuiceKotHeaderTitle() != null) s.setJuiceKotHeaderTitle(dto.getJuiceKotHeaderTitle().trim());
        if (dto.getJuiceKotSubheader() != null) s.setJuiceKotSubheader(dto.getJuiceKotSubheader().trim());
        if (dto.getJuiceKotFooterMessage() != null) s.setJuiceKotFooterMessage(dto.getJuiceKotFooterMessage().trim());
        if (dto.getJuiceKotCategoryIds() != null) s.setJuiceKotCategoryIds(dto.getJuiceKotCategoryIds().trim());
        if (dto.getDirectPrintSsids() != null) s.setDirectPrintSsids(DevicePrintScope.cleanSsidList(dto.getDirectPrintSsids()));

        boolean logoSettingsChanged = false;
        if (dto.getLogoSize() != null && dto.getLogoSize() > 0 && !dto.getLogoSize().equals(s.getLogoSize())) {
            s.setLogoSize(dto.getLogoSize());
            logoSettingsChanged = true;
        }
        if (dto.getLogoGap() != null && dto.getLogoGap() >= 0) s.setLogoGap(dto.getLogoGap());
        if (dto.getLineSpacing() != null && dto.getLineSpacing() > 0) s.setLineSpacing(dto.getLineSpacing());
        if (dto.getHeaderSpacing() != null && dto.getHeaderSpacing() >= 0) s.setHeaderSpacing(dto.getHeaderSpacing());
        if (dto.getFooterSpacing() != null && dto.getFooterSpacing() >= 0) s.setFooterSpacing(dto.getFooterSpacing());
        if (dto.getFontSize() != null && !dto.getFontSize().isBlank()) s.setFontSize(dto.getFontSize().trim());
        if (dto.getPrintStyle() != null && !dto.getPrintStyle().isBlank()) s.setPrintStyle(dto.getPrintStyle().trim());

        if (logoSettingsChanged) {
            imageRasterCache.clear();
        }

        s.setUpdatedAt(LocalDateTime.now());
        try {
            printerSettingRepository.save(s);
        } catch (Exception ex) {
            log.error("Failed to save updated printer settings: {}. Retrying after emergency self-heal...", ex.getMessage());
            databaseSchemaEnsurer.ensureSchema();
            printerSettingRepository.save(s);
        }
        return getSettingsDTO();
    }

    /**
     * Test connection to printer without printing.
     * Supports testing specific printer ("BILL", "KOT", "JUICE_KOT" or null for default).
     */
    public PrintResultDTO testConnection() {
        return testConnection(null);
    }

    /** Direct-socket probe results are reused briefly so automatic status checks don't re-block on a dead printer. */
    private static final long DIRECT_PROBE_CACHE_MS = 15_000L;
    private record CachedProbe(long at, PrintResultDTO result) {}
    private final java.util.concurrent.ConcurrentHashMap<String, CachedProbe> directProbeCache = new java.util.concurrent.ConcurrentHashMap<>();

    public PrintResultDTO testConnection(String type) {
        return testConnection(type, false);
    }

    /** @param allowCached true for background/automatic checks; manual pings and settings changes pass false. */
    public PrintResultDTO testConnection(String type, boolean allowCached) {
        PrinterSetting s = getSettings();
        boolean isJuice = "JUICE_KOT".equalsIgnoreCase(type) || "JUICE".equalsIgnoreCase(type);
        boolean isKot = "KOT".equalsIgnoreCase(type);

        String targetHost = isJuice
                ? ((s.getJuiceKotHost() != null && !s.getJuiceKotHost().isBlank()) ? s.getJuiceKotHost() : "192.168.1.42")
                : (isKot
                ? ((s.getKotHost() != null && !s.getKotHost().isBlank()) ? s.getKotHost() : "192.168.1.41")
                : ((s.getBillHost() != null && !s.getBillHost().isBlank()) ? s.getBillHost() : (s.getHost() != null && !s.getHost().isBlank() ? s.getHost() : "192.168.1.47")));
        int targetPort = isJuice
                ? ((s.getJuiceKotPort() != null && s.getJuiceKotPort() > 0) ? s.getJuiceKotPort() : 9100)
                : (isKot
                ? ((s.getKotPort() != null && s.getKotPort() > 0) ? s.getKotPort() : 9100)
                : ((s.getBillPort() != null && s.getBillPort() > 0) ? s.getBillPort() : s.getPort()));
        String targetName = isJuice ? "Juice KOT Printer" : (isKot ? "KOT Printer" : "Bill Printer");

        // 1. If Cloud Bridge is connected from store WiFi, use bridge telemetry
        if (printBridgeService.isBridgeOnline()) {
            PrintBridgeHeartbeatDTO hb = printBridgeService.getLatestHeartbeat();
            String bridgeIp = (hb != null && hb.getBridgeIp() != null) ? hb.getBridgeIp() : "LAN";

            boolean reachable;
            long latency;
            if (isJuice) {
                reachable = hb != null && Boolean.TRUE.equals(hb.getJuiceKotPrinterReachable());
                latency = (hb != null && hb.getJuiceKotPrinterLatencyMs() != null) ? hb.getJuiceKotPrinterLatencyMs() : 0L;
            } else if (isKot) {
                reachable = hb != null && Boolean.TRUE.equals(hb.getKotPrinterReachable());
                latency = (hb != null && hb.getKotPrinterLatencyMs() != null) ? hb.getKotPrinterLatencyMs() : 0L;
            } else {
                reachable = hb != null && (Boolean.TRUE.equals(hb.getBillPrinterReachable()) || Boolean.TRUE.equals(hb.getPrinterReachable()));
                latency = (hb != null && hb.getBillPrinterLatencyMs() != null) ? hb.getBillPrinterLatencyMs()
                        : (hb != null && hb.getPrinterLatencyMs() != null ? hb.getPrinterLatencyMs() : 0L);
            }

            if (reachable) {
                return PrintResultDTO.builder()
                        .success(true)
                        .message(String.format("%s is ONLINE via ESP32 Bridge (%s -> %s:%d, Latency: %dms)",
                                targetName, bridgeIp, targetHost, targetPort, latency))
                        .host(targetHost)
                        .port(targetPort)
                        .printType("CONNECTION_TEST")
                        .printedAt(LocalDateTime.now())
                        .build();
            } else {
                return PrintResultDTO.builder()
                        .success(false)
                        .message(String.format("ESP32 Bridge is online (%s), but %s is OFFLINE or unreachable at %s:%d",
                                bridgeIp, targetName, targetHost, targetPort))
                        .host(targetHost)
                        .port(targetPort)
                        .printType("CONNECTION_TEST")
                        .printedAt(LocalDateTime.now())
                        .build();
            }
        }

        // 2. Direct Socket connection test (Local network mode)
        String cacheKey = com.neram.pos.tenancy.TenantContext.currentId().orElse(0L) + "|" + targetName + "|" + targetHost + ":" + targetPort;
        if (allowCached) {
            CachedProbe cached = directProbeCache.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.at() < DIRECT_PROBE_CACHE_MS) {
                return cached.result();
            }
        }
        if (!directPrintEnabled) {
            return PrintResultDTO.builder()
                    .success(false)
                    .message(targetName + " status is only known while your print bridge is connected.")
                    .host(targetHost)
                    .port(targetPort)
                    .printType("STATUS_CHECK")
                    .status("UNKNOWN")
                    .build();
        }
        PrintResultDTO probed = probeDirect(targetName, targetHost, targetPort);
        directProbeCache.put(cacheKey, new CachedProbe(System.currentTimeMillis(), probed));
        return probed;
    }

    private PrintResultDTO probeDirect(String targetName, String targetHost, int targetPort) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), 3000);
            long latency = System.currentTimeMillis() - start;
            return PrintResultDTO.builder()
                    .success(true)
                    .message(String.format("%s is ONLINE at %s:%d (Response time: %dms)", targetName, targetHost, targetPort, latency))
                    .host(targetHost)
                    .port(targetPort)
                    .printType("CONNECTION_TEST")
                    .printedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.warn("{} direct connection test failed: {}", targetName, e.getMessage());
            return PrintResultDTO.builder()
                    .success(false)
                    .message(String.format("%s is OFFLINE or unreachable at %s:%d: %s", targetName, targetHost, targetPort, e.getMessage()))
                    .host(targetHost)
                    .port(targetPort)
                    .printType("CONNECTION_TEST")
                    .printedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Print test page slip to thermal printer
     */
    public PrintResultDTO printTestSlip() {
        return printTestSlip((String) null, null);
    }

    public PrintResultDTO printTestSlip(String idempotencyKey) {
        return printTestSlip(null, idempotencyKey);
    }

    public PrintResultDTO printTestSlip(String type, String idempotencyKey) {
        PrinterSetting s = getSettings();
        boolean isJuice = "JUICE_KOT".equalsIgnoreCase(type) || "JUICE".equalsIgnoreCase(type);
        boolean isKot = "KOT".equalsIgnoreCase(type);

        String targetHost = isJuice
                ? ((s.getJuiceKotHost() != null && !s.getJuiceKotHost().isBlank()) ? s.getJuiceKotHost() : "192.168.1.42")
                : (isKot
                ? ((s.getKotHost() != null && !s.getKotHost().isBlank()) ? s.getKotHost() : "192.168.1.37")
                : ((s.getBillHost() != null && !s.getBillHost().isBlank()) ? s.getBillHost() : s.getHost()));
        int targetPort = isJuice
                ? ((s.getJuiceKotPort() != null && s.getJuiceKotPort() > 0) ? s.getJuiceKotPort() : 9100)
                : (isKot
                ? ((s.getKotPort() != null && s.getKotPort() > 0) ? s.getKotPort() : 9100)
                : ((s.getBillPort() != null && s.getBillPort() > 0) ? s.getBillPort() : s.getPort()));
        String targetName = isJuice ? "Juice KOT Printer" : (isKot ? "KOT Kitchen Printer" : "Bill Cashier Printer");

        boolean isEnabled = isJuice
                ? Boolean.TRUE.equals(s.getJuiceKotEnabled()) && Boolean.TRUE.equals(s.getEnabled())
                : (isKot
                ? Boolean.TRUE.equals(s.getKotEnabled()) && Boolean.TRUE.equals(s.getEnabled())
                : Boolean.TRUE.equals(s.getBillEnabled()) && Boolean.TRUE.equals(s.getEnabled()));

        if (!isEnabled) {
            return PrintResultDTO.builder()
                    .success(false)
                    .message(targetName + " is currently disabled in settings.")
                    .host(targetHost)
                    .port(targetPort)
                    .printType(isJuice ? "JUICE_KOT" : (isKot ? "KOT" : "TEST_SLIP"))
                    .status("DISABLED")
                    .build();
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            // Initialize
            buffer.write(CMD_INIT);

            // Centered Header with optional Logo
            if (s.getLogoEnabled() == null || Boolean.TRUE.equals(s.getLogoEnabled())) {
                byte[] logoBytes = getLogoRasterBytes(s.getPaperWidthMm() != null ? s.getPaperWidthMm() : 80);
                if (logoBytes != null && logoBytes.length > 0) {
                    buffer.write(CMD_ALIGN_CENTER);
                    buffer.write(logoBytes);
                    buffer.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            buffer.write(CMD_ALIGN_CENTER);
            buffer.write(CMD_TXT_DOUBLE_W_H);
            buffer.write(CMD_BOLD_ON);
            buffer.write((s.getHeaderTitle() + "\n").getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_TXT_NORMAL);
            buffer.write(CMD_BOLD_OFF);
            if (s.getSubheader() != null && !s.getSubheader().isBlank()) {
                for (String line : s.getSubheader().split("\r?\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        buffer.write(formatWordWrapCentered(trimmed, LINE_WIDTH_80MM).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            if (s.getSubheader2() != null && !s.getSubheader2().isBlank()) {
                for (String line : s.getSubheader2().split("\r?\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        buffer.write(formatWordWrapCentered(trimmed, LINE_WIDTH_80MM).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            buffer.write(CMD_BOLD_ON);
            buffer.write(String.format("*** %s TEST ***\n", targetName.toUpperCase()).getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
            buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

            // Left Body
            buffer.write(CMD_ALIGN_LEFT);
            String nowStr = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).format(DATE_TIME_FORMATTER);
            buffer.write(formatTwoColumns("Date & Time:", nowStr).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Printer:", targetName).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Printer IP:", targetHost).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Printer Port:", String.valueOf(targetPort)).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Paper Width:", s.getPaperWidthMm() + "mm (48 Cols)").getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Protocol:", "Raw ESC/POS TCP Socket").getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Bridge Target:", "ESP32-S3-N16R8").getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Status:", "READY & OPERATIONAL").getBytes(StandardCharsets.UTF_8));
            buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

            // Centered Footer
            buffer.write(CMD_ALIGN_CENTER);
            buffer.write(CMD_BOLD_ON);
            buffer.write((s.getFooterMessage() + "\n").getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
            buffer.write("Have a Wonderful Day!\n\n\n\n".getBytes(StandardCharsets.UTF_8));

            if (Boolean.TRUE.equals(s.getAutoCut())) {
                buffer.write(CMD_CUT_PAPER);
            }

            return dispatchPrintJob(targetHost, targetPort, buffer.toByteArray(), "TEST_SLIP", null, null, idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to generate test slip to {}:{}: {}", targetHost, targetPort, e.getMessage());
            return PrintResultDTO.builder()
                    .success(false)
                    .message(String.format("Print failed on %s:%d: %s", targetHost, targetPort, e.getMessage()))
                    .host(targetHost)
                    .port(targetPort)
                    .printType("TEST_SLIP")
                    .status("FAILED")
                    .build();
        }
    }

    private Long branchOfBill(Long billId) {
        return billId == null ? null : billRepository.findById(billId).map(Bill::getOutletId).orElse(null);
    }

    public PrintResultDTO printBill(Long billId) {
        return printBill(billId, null);
    }

    /**
     * Print customer bill receipt directly to thermal printer
     */
    @Transactional
    public PrintResultDTO printBill(Long billId, String idempotencyKey) {
        return printBill(billId, idempotencyKey, null);
    }

    /**
     * The customer bill. {@code redeemPoints} prints the bill as it will be after that many loyalty points are used: the points
     * come off the total, the balance to pay is shown, and so are the points the rest will earn. Nothing is redeemed by printing.
     */
    @Transactional
    public PrintResultDTO printBill(Long billId, String idempotencyKey, Integer redeemPoints) {
        // An owner/main manager looking at the whole cafe prints with the settings and printers of the bill's branch.
        if (com.neram.pos.access.OutletContext.current().isEmpty()) {
            Long branch = branchOfBill(billId);
            if (branch != null) {
                return com.neram.pos.access.OutletContext.callAs(branch, () -> printBill(billId, idempotencyKey, redeemPoints));
            }
        }
        PrinterSetting s = getSettings();
        String billHost = (s.getBillHost() != null && !s.getBillHost().isBlank()) ? s.getBillHost() : s.getHost();
        int billPort = (s.getBillPort() != null && s.getBillPort() > 0) ? s.getBillPort() : s.getPort();
        boolean billEnabled = Boolean.TRUE.equals(s.getBillEnabled()) && Boolean.TRUE.equals(s.getEnabled());

        if (!billEnabled) {
            return PrintResultDTO.builder()
                    .success(false)
                    .message("Bill thermal printing is currently disabled.")
                    .host(billHost)
                    .port(billPort)
                    .billId(billId)
                    .printType("BILL")
                    .status("DISABLED")
                    .build();
        }

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found with id: " + billId));

        int redeem = validatedRedeem(bill, redeemPoints);
        try {
            byte[] receipt = buildBillReceipt(bill, s, new ReceiptExtras(null, null, redeem));
            return dispatchPrintJob(billHost, billPort, receipt, "BILL", bill.getId(), bill.getBillNumber(), idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to print Bill #{} to {}:{}: {}", bill.getBillNumber(), billHost, billPort, e.getMessage());
            return PrintResultDTO.builder()
                    .success(false)
                    .message(String.format("Print failed on %s:%d: %s", billHost, billPort, e.getMessage()))
                    .host(billHost)
                    .port(billPort)
                    .billId(bill.getId())
                    .billNumber(bill.getBillNumber())
                    .printType("BILL")
                    .status("FAILED")
                    .build();
        }
    }

    /**
     * Prints one bill per group of items (see {@link BillSplit}). Each printed bill carries "SPLIT BILL n OF m", its own items and
     * amounts, and the same bill number; the bill itself is not changed. All the bills are sent, or the first problem is reported.
     */
    @Transactional
    public PrintResultDTO printBillSplit(Long billId, com.neram.pos.dto.SplitBillRequestDTO request, String idempotencyKey) {
        // An owner/main manager looking at the whole cafe prints with the settings and printers of the bill's branch.
        if (com.neram.pos.access.OutletContext.current().isEmpty()) {
            Long branch = branchOfBill(billId);
            if (branch != null) {
                return com.neram.pos.access.OutletContext.callAs(branch, () -> printBillSplit(billId, request, idempotencyKey));
            }
        }
        PrinterSetting s = getSettings();
        String billHost = (s.getBillHost() != null && !s.getBillHost().isBlank()) ? s.getBillHost() : s.getHost();
        int billPort = (s.getBillPort() != null && s.getBillPort() > 0) ? s.getBillPort() : s.getPort();
        if (!(Boolean.TRUE.equals(s.getBillEnabled()) && Boolean.TRUE.equals(s.getEnabled()))) {
            return PrintResultDTO.builder().success(false).message("Bill thermal printing is currently disabled.").host(billHost).port(billPort)
                    .billId(billId).printType("BILL").status("DISABLED").build();
        }
        Bill bill = billRepository.findById(billId).orElseThrow(() -> new IllegalArgumentException("Bill not found with id: " + billId));
        List<BillSplit.Portion> portions = BillSplit.divide(bill, request != null ? request.shares() : null);
        boolean paid = bill.getStatus() == Bill.Status.PAID;
        PrintResultDTO last = null;
        for (BillSplit.Portion portion : portions) {
            PrintResultDTO result;
            try {
                String marker = "SPLIT BILL " + portion.number() + " OF " + portions.size();
                byte[] bytes = buildBillReceipt(BillSplit.asBill(bill, portion), s, new ReceiptExtras(marker, paid ? "PAID" : "PENDING", 0));
                result = dispatchPrintJob(billHost, billPort, bytes, "BILL", bill.getId(), bill.getBillNumber(),
                        idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey + ":split" + portion.number() + "of" + portions.size());
            } catch (Exception e) {
                log.error("Failed to print split bill {} of {} for Bill #{}: {}", portion.number(), portions.size(), bill.getBillNumber(), e.getMessage());
                return PrintResultDTO.builder().success(false).message("Bill " + portion.number() + " of " + portions.size() + " could not be printed: " + e.getMessage())
                        .host(billHost).port(billPort).billId(bill.getId()).billNumber(bill.getBillNumber()).printType("BILL").status("FAILED").build();
            }
            if (!result.isSuccess()) {
                result.setMessage("Bill " + portion.number() + " of " + portions.size() + ": " + result.getMessage());
                return result;
            }
            last = result;
        }
        if (last != null) last.setMessage(portions.size() + " split bills sent to the printer for Bill #" + bill.getBillNumber());
        recordAudit("PRINT_SPLIT_BILL", bill, portions);
        return last;
    }

    private void recordAudit(String action, Bill bill, List<BillSplit.Portion> portions) {
        if (auditLogService == null) return;
        try {
            StringBuilder parts = new StringBuilder();
            for (BillSplit.Portion p : portions) parts.append(parts.length() == 0 ? "" : " + ").append(p.netAmount().toPlainString());
            auditLogService.log(action, "BILL", bill.getId(), null, "Bill " + bill.getBillNumber() + " printed as " + portions.size() + " bills: " + parts);
        } catch (Exception e) { log.warn("Audit entry for split bill not written: {}", e.getMessage()); }
    }

    private com.neram.pos.entity.Customer memberOf(Bill bill) {
        if (bill == null || bill.getCustomerId() == null || customerRepository == null) return null;
        return customerRepository.findById(bill.getCustomerId()).orElse(null);
    }

    /** The points to print as used on this bill: none, or a number the member can really use now and the bill can take. */
    private int validatedRedeem(Bill bill, Integer wanted) {
        if (wanted == null || wanted <= 0) return 0;
        com.neram.pos.entity.Customer member = memberOf(bill);
        if (member == null) throw new IllegalArgumentException("Add the customer's phone number first: only a loyalty member can use points.");
        int min = loyaltySettings != null ? loyaltySettings.rules().minRedeemPoints() : 100;
        int usable = CustomerService.spendablePoints(member, com.neram.pos.util.BusinessDate.now());
        if (wanted < min) throw new IllegalArgumentException("At least " + min + " points are needed to use points.");
        if (wanted > usable) throw new IllegalArgumentException("The customer can use " + usable + " points now.");
        BigDecimal net = bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO;
        if (BigDecimal.valueOf(wanted).compareTo(net) > 0) throw new IllegalArgumentException("The points used cannot be more than the bill total.");
        return wanted;
    }

    /**
     * What a member needs to see on the bill: the points they hold and until when, so they can ask for them to be used; and,
     * once points are applied, what is left to pay and what the rest will earn. Bills without a member print nothing here.
     */
    private void writeLoyaltyBlock(ByteArrayOutputStream buffer, Bill bill, int redeem, BigDecimal net) throws java.io.IOException {
        com.neram.pos.entity.Customer member = memberOf(bill);
        if (member == null) return;
        int usable = CustomerService.spendablePoints(member, com.neram.pos.util.BusinessDate.now());
        int min = loyaltySettings != null ? loyaltySettings.rules().minRedeemPoints() : 100;
        buffer.write(CMD_ALIGN_LEFT);
        buffer.write(CMD_BOLD_ON);
        buffer.write(("LOYALTY: " + member.getName() + "\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_BOLD_OFF);
        buffer.write(formatTwoColumns("Points balance:", usable + " pts (Rs. " + usable + ")").getBytes(StandardCharsets.UTF_8));
        if (usable > 0 && member.getPointsExpireAt() != null) {
            buffer.write(formatTwoColumns("Valid till:", member.getPointsExpireAt().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))).getBytes(StandardCharsets.UTF_8));
        }
        if (redeem > 0) {
            BigDecimal toPay = net.subtract(BigDecimal.valueOf(redeem)).max(BigDecimal.ZERO);
            int earned = toPay.divide(BigDecimal.valueOf(LoyaltySettingsService.RUPEES_PER_POINT_EARNED), 0, RoundingMode.DOWN).intValue();
            buffer.write(formatTwoColumns("Points after this bill:", (usable - redeem + earned) + " pts").getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Points earned on Rs. " + toPay.setScale(0, RoundingMode.DOWN).toPlainString() + ":", "+" + earned + " pts").getBytes(StandardCharsets.UTF_8));
        } else if (bill.getStatus() == Bill.Status.BILL_PRINTED && usable >= min) {
            buffer.write(formatWordWrapCentered("Ask the cashier to use your points on this bill (1 point = Rs. 1).", LINE_WIDTH_80MM).getBytes(StandardCharsets.UTF_8));
        }
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** What is different about a bill that is not the plain customer bill: a split marker, its own payment line, points about to be used. */
    record ReceiptExtras(String splitMarker, String paymentText, int redeemPoints) {}

    private byte[] buildBillReceipt(Bill bill, PrinterSetting s, ReceiptExtras extras) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(CMD_INIT);

        // Configure Line Spacing: ESC 3 n (dots)
        int lineSpacing = (s.getLineSpacing() != null && s.getLineSpacing() > 0) ? s.getLineSpacing() : 24;
        buffer.write(new byte[]{0x1B, 0x33, (byte) Math.min(255, lineSpacing)});

        // Configure High Contrast / Crisp Thermal Print: ESC G 1 (Double strike)
        if ("CRISP_DARK".equalsIgnoreCase(s.getPrintStyle()) || s.getPrintStyle() == null) {
            buffer.write(new byte[]{0x1B, 0x47, 0x01});
        }

        // Centered Brand Logo at Top (Auto-cropped, pixel-tight)
        if (s.getLogoEnabled() == null || Boolean.TRUE.equals(s.getLogoEnabled())) {
            int targetLogoSize = (s.getLogoSize() != null && s.getLogoSize() > 0) ? s.getLogoSize() : 130;
            byte[] logoBytes = getLogoRasterBytes(s.getPaperWidthMm() != null ? s.getPaperWidthMm() : 80, targetLogoSize);
            if (logoBytes != null && logoBytes.length > 0) {
                buffer.write(CMD_ALIGN_CENTER);
                buffer.write(logoBytes);
                int gap = (s.getLogoGap() != null && s.getLogoGap() >= 0) ? s.getLogoGap() : 2;
                if (gap > 0) {
                    // ESC J n: Micro-feed n dots without full line feed
                    buffer.write(new byte[]{0x1B, 0x4A, (byte) Math.min(255, gap * 4)});
                }
            }
        }

        // Centered Brand Header (Crisp tall typography)
        buffer.write(CMD_ALIGN_CENTER);
        if ("DOUBLE_W_H".equalsIgnoreCase(s.getFontSize())) {
            buffer.write(CMD_TXT_DOUBLE_W_H);
        } else {
            buffer.write(CMD_TXT_DOUBLE_H);
        }
        buffer.write(CMD_BOLD_ON);
        buffer.write((s.getHeaderTitle() + "\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_TXT_NORMAL);
        buffer.write(CMD_BOLD_OFF);
        if (s.getSubheader() != null && !s.getSubheader().isBlank()) {
            for (String line : s.getSubheader().split("\r?\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    buffer.write(formatWordWrapCentered(trimmed, LINE_WIDTH_80MM).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        if (s.getSubheader2() != null && !s.getSubheader2().isBlank()) {
            for (String line : s.getSubheader2().split("\r?\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    buffer.write(formatWordWrapCentered(trimmed, LINE_WIDTH_80MM).getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        boolean gstActive = Boolean.TRUE.equals(bill.getGstEnabled());
        String gstin = bill.getGstin();
        if (gstin == null || gstin.isBlank()) {
            try {
                gstin = gstSettingService.getSettings().getGstin();
            } catch (Exception ignored) {}
        }

        if (gstActive) {
            if (gstin != null && !gstin.isBlank()) {
                buffer.write(("GSTIN: " + gstin + "\n").getBytes(StandardCharsets.UTF_8));
            }
            buffer.write(CMD_BOLD_ON);
            buffer.write("TAX INVOICE\n".getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
        } else {
            buffer.write(CMD_BOLD_ON);
            buffer.write("CASH BILL / RECEIPT\n".getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
        }
        if (extras.splitMarker() != null) {
            buffer.write(CMD_BOLD_ON);
            buffer.write((extras.splitMarker() + "\n").getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
        }
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // Order Metadata
        buffer.write(CMD_ALIGN_LEFT);
        LocalDateTime dt = bill.getCreatedAt() != null ? bill.getCreatedAt() : LocalDateTime.now();
        String dateStr = dt.format(DATE_TIME_FORMATTER);

        boolean isOutdoor = bill.getOrderType() == Bill.OrderType.OUTDOOR || (bill.getOutdoorToken() != null && !bill.getOutdoorToken().isBlank());
        boolean isTakeaway = bill.getOrderType() == Bill.OrderType.TAKEAWAY || (bill.getTakeawayToken() != null && !bill.getTakeawayToken().isBlank());
        if (isOutdoor) {
            String cust = bill.getCustomerName() != null ? bill.getCustomerName() : "Guest";
            String tok = bill.getOutdoorToken() != null ? bill.getOutdoorToken() : "OUT";
            buffer.write(formatTwoColumns("Bill: " + bill.getBillNumber(), "Token: " + tok).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Date: " + dateStr, "Location: OUTDOOR").getBytes(StandardCharsets.UTF_8));
            buffer.write(("Customer: " + cust + "\n").getBytes(StandardCharsets.UTF_8));
        } else if (isTakeaway) {
            String cust = bill.getCustomerName() != null ? bill.getCustomerName() : "Guest";
            String tok = bill.getTakeawayToken() != null ? bill.getTakeawayToken() : "TAK";
            buffer.write(formatTwoColumns("Bill: " + bill.getBillNumber(), "Token: " + tok).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Date: " + dateStr, "Type: TAKEAWAY").getBytes(StandardCharsets.UTF_8));
            buffer.write(("Customer: " + cust + "\n").getBytes(StandardCharsets.UTF_8));
        } else {
            String tbl = bill.getTableNumber() != null ? bill.getTableNumber() : "Takeaway";
            String cust = bill.getCustomerName() != null && !bill.getCustomerName().isBlank() ? " (" + bill.getCustomerName() + ")" : "";
            buffer.write(formatTwoColumns("Bill: " + bill.getBillNumber(), "Table: " + tbl + cust).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns("Date: " + dateStr, "Cashier: " + (getUserName(bill.getPrintedBy()) != null ? getUserName(bill.getPrintedBy()) : "Counter")).getBytes(StandardCharsets.UTF_8));
        }

        String staffName = getUserName(bill.getCreatedBy());
        buffer.write(("Staff Attended: " + (staffName != null ? staffName : "Staff") + "\n").getBytes(StandardCharsets.UTF_8));

        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // Items Table Header (48 chars width)
        // ITEM: 25 chars, QTY: 4 chars, PRICE: 8 chars, TOTAL: 11 chars
        buffer.write(CMD_BOLD_ON);
        buffer.write(formatItemHeader().getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_BOLD_OFF);
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // Items List
        int totalQty = 0;
        if (bill.getItems() != null) {
            for (BillItem item : bill.getItems()) {
                int q = item.getQuantity() != null ? item.getQuantity() : 0;
                totalQty += q;
                String itemName = item.getItemName();
                if (Boolean.TRUE.equals(item.getIsTakeaway()) && !isTakeaway) {
                    itemName = itemName + " (Parcel)";
                }
                buffer.write(formatItemRow(itemName, q, item.getPrice(), item.getSubtotal()).getBytes(StandardCharsets.UTF_8));
                printItemSnapshots(buffer, item.getModifiersSnapshot(), item.getComboSnapshot());
            }
        }

        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // Summary / Totals
        buffer.write(formatTwoColumns("Total Qty: " + totalQty, "").getBytes(StandardCharsets.UTF_8));
        BigDecimal subtotal = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;
        buffer.write(formatTwoColumns("Sub Total:", String.format("Rs. %.2f", subtotal.doubleValue())).getBytes(StandardCharsets.UTF_8));

        if (bill.getDiscountAmount() != null && bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            String discLabel = String.format("Discount (%.0f%%):", bill.getDiscountPercent() != null ? bill.getDiscountPercent() : 0.0);
            buffer.write(formatTwoColumns(discLabel, String.format("-Rs. %.2f", bill.getDiscountAmount().doubleValue())).getBytes(StandardCharsets.UTF_8));
        }

        if (bill.getParcelCharge() != null && bill.getParcelCharge().compareTo(BigDecimal.ZERO) > 0) {
            buffer.write(formatTwoColumns("Parcel Charge:", String.format("Rs. %.2f", bill.getParcelCharge().doubleValue())).getBytes(StandardCharsets.UTF_8));
        }

        if (gstActive) {
            double cgstRate = bill.getCgstPercent() != null ? bill.getCgstPercent() : 2.5;
            double sgstRate = bill.getSgstPercent() != null ? bill.getSgstPercent() : 2.5;
            BigDecimal cgstAmt = bill.getCgstAmount() != null ? bill.getCgstAmount() : (bill.getTaxAmount() != null ? bill.getTaxAmount().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            BigDecimal sgstAmt = bill.getSgstAmount() != null ? bill.getSgstAmount() : (bill.getTaxAmount() != null ? bill.getTaxAmount().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

            buffer.write(formatTwoColumns(String.format("CGST (%.1f%%):", cgstRate), String.format("Rs. %.2f", cgstAmt.doubleValue())).getBytes(StandardCharsets.UTF_8));
            buffer.write(formatTwoColumns(String.format("SGST (%.1f%%):", sgstRate), String.format("Rs. %.2f", sgstAmt.doubleValue())).getBytes(StandardCharsets.UTF_8));

            if (bill.getRoundOff() != null && bill.getRoundOff().abs().compareTo(new BigDecimal("0.001")) > 0) {
                String sign = bill.getRoundOff().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                buffer.write(formatTwoColumns("Round off:", String.format("%s%.2f", sign, bill.getRoundOff().doubleValue())).getBytes(StandardCharsets.UTF_8));
            }
        }

        if (bill.getPromotionDiscountAmount() != null && bill.getPromotionDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            String promoLabel = String.format("PROMO: %s (%.0f%%):",
                    bill.getPromotionName() != null ? bill.getPromotionName() : "Promotion",
                    bill.getPromotionPercentage() != null ? bill.getPromotionPercentage() : 0.0);
            buffer.write(formatTwoColumns(promoLabel, String.format("-Rs. %.2f", bill.getPromotionDiscountAmount().doubleValue())).getBytes(StandardCharsets.UTF_8));
        }

        buffer.write((divider('=') + "\n").getBytes(StandardCharsets.UTF_8));

        // Grand Total in Bold Double Height
        BigDecimal netTotal = bill.getNetAmount() != null ? bill.getNetAmount() : subtotal;
        buffer.write(CMD_BOLD_ON);
        buffer.write(CMD_TXT_DOUBLE_H);
        buffer.write(formatTwoColumns("GRAND TOTAL:", String.format("Rs. %.2f", netTotal.doubleValue())).getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_TXT_NORMAL);
        buffer.write(CMD_BOLD_OFF);

        int redeem = extras.redeemPoints();
        if (redeem > 0) {
            BigDecimal toPay = netTotal.subtract(BigDecimal.valueOf(redeem)).max(BigDecimal.ZERO);
            buffer.write(formatTwoColumns("Loyalty points used (" + redeem + " pts):", String.format("-Rs. %.2f", (double) redeem)).getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_ON);
            buffer.write(CMD_TXT_DOUBLE_H);
            buffer.write(formatTwoColumns("BALANCE TO PAY:", String.format("Rs. %.2f", toPay.doubleValue())).getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_TXT_NORMAL);
            buffer.write(CMD_BOLD_OFF);
        }

        buffer.write((divider('=') + "\n").getBytes(StandardCharsets.UTF_8));
        String payMode = (bill.getStatus() == Bill.Status.BILL_PRINTED) ? "PENDING" : (bill.getPaymentMode() != null ? bill.getPaymentMode().name() : "CASH");
        if (extras.paymentText() == null && bill.getStatus() != Bill.Status.BILL_PRINTED && bill.getPaymentSummary() != null) {
            buffer.write(formatTwoColumns("Payment Mode:", "SPLIT").getBytes(StandardCharsets.UTF_8));
            for (String part : bill.getPaymentSummary().split(" \\+ ")) buffer.write(formatTwoColumns("  " + part.substring(0, part.indexOf(' ')), part.substring(part.indexOf(' ') + 1).replace("₹", "Rs. ")).getBytes(StandardCharsets.UTF_8));
        } else
        buffer.write(formatTwoColumns("Payment Mode:", extras.paymentText() != null ? extras.paymentText() : payMode).getBytes(StandardCharsets.UTF_8));
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));
        writeLoyaltyBlock(buffer, bill, redeem, netTotal);

        // Payment QR (neram-qr.png): scan to pay by UPI, between the payment mode and the thank-you footer (can be switched off in the printer settings)
        byte[] payQr = Boolean.FALSE.equals(s.getPaymentQrEnabled()) ? null : getPaymentQrRasterBytes(s.getPaperWidthMm() != null ? s.getPaperWidthMm() : 80);
        if (payQr != null) {
            buffer.write(CMD_ALIGN_CENTER);
            buffer.write(CMD_BOLD_ON);
            buffer.write("SCAN TO PAY (UPI)\n".getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
            buffer.write(payQr);
            buffer.write("\n".getBytes(StandardCharsets.UTF_8));
        }

        // Footer
        buffer.write(CMD_ALIGN_CENTER);
        buffer.write(CMD_BOLD_ON);
        buffer.write((s.getFooterMessage() + "\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_BOLD_OFF);
        buffer.write("Have a Wonderful Day!\n".getBytes(StandardCharsets.UTF_8));

        int footSpacing = (s.getFooterSpacing() != null && s.getFooterSpacing() >= 0) ? s.getFooterSpacing() : 1;
        for (int i = 0; i < footSpacing; i++) {
            buffer.write("\n".getBytes(StandardCharsets.UTF_8));
        }

        if (Boolean.TRUE.equals(s.getAutoCut())) {
            buffer.write(CMD_CUT_PAPER);
        }


        return buffer.toByteArray();
    }

    public PrintResultDTO printKot(Long billId) {
        return printKot(billId, null);
    }

    /**
     * Check if a category name represents Juice (legacy fallback)
     */
    public boolean isJuiceCategory(String name) {
        if (name == null || name.isBlank()) return false;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("juice") || normalized.equals("juices")
                || normalized.matches(".*\\bjuices?\\b.*");
    }

    /**
     * Parse configured comma-separated category IDs into a Set of Longs
     */
    public Set<Long> parseJuiceKotCategoryIds(String categoryIdsStr) {
        if (categoryIdsStr == null || categoryIdsStr.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> set = new HashSet<>();
        for (String part : categoryIdsStr.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    set.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {}
            }
        }
        return set;
    }

    /**
     * Check if a menu item belongs to the configured Juice / Beverage KOT categories
     */
    public boolean isJuiceItem(Long menuItemId, String itemName) {
        return isJuiceItem(menuItemId, itemName, getSettings());
    }

    /**
     * Check if a menu item belongs to the configured Juice / Beverage KOT categories using active settings
     */
    public boolean isJuiceItem(Long menuItemId, String itemName, PrinterSetting s) {
        Set<Long> configuredCatIds = parseJuiceKotCategoryIds(s != null ? s.getJuiceKotCategoryIds() : null);
        boolean hasCustomConfig = !configuredCatIds.isEmpty();

        if (menuItemId != null && menuItemRepository != null) {
            Optional<MenuItem> mOpt = menuItemRepository.findById(menuItemId);
            if (mOpt.isPresent()) {
                Long catId = mOpt.get().getCategoryId();
                if (catId != null) {
                    if (hasCustomConfig) {
                        return configuredCatIds.contains(catId);
                    }
                    if (categoryRepository != null) {
                        Optional<Category> catOpt = categoryRepository.findById(catId);
                        if (catOpt.isPresent() && isJuiceCategory(catOpt.get().getName())) {
                            return true;
                        }
                    }
                }
            }
        }
        if (itemName != null && !itemName.isBlank() && menuItemRepository != null) {
            List<MenuItem> matches = menuItemRepository.findByNameIgnoreCase(itemName.trim());
            for (MenuItem m : matches) {
                if (m.getCategoryId() != null) {
                    if (hasCustomConfig) {
                        if (configuredCatIds.contains(m.getCategoryId())) {
                            return true;
                        }
                    } else if (categoryRepository != null) {
                        Optional<Category> catOpt = categoryRepository.findById(m.getCategoryId());
                        if (catOpt.isPresent() && isJuiceCategory(catOpt.get().getName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Build ESC/POS raster payload for a KOT slip (Kitchen or Juice)
     */
    /**
     * The time to print on a kitchen ticket: the most recent of "KOT printed" and "order updated",
     * i.e. the moment of the latest kitchen dispatch. Falls back to the order creation time, then now.
     */
    public static LocalDateTime kotTicketTime(Bill bill) {
        LocalDateTime printed = bill.getKotPrintedAt();
        LocalDateTime updated = bill.getKotUpdatedAt();
        LocalDateTime latest = (printed != null && updated != null) ? (printed.isAfter(updated) ? printed : updated)
                : (printed != null ? printed : updated);
        if (latest != null) return latest;
        return bill.getCreatedAt() != null ? bill.getCreatedAt() : LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    public byte[] buildKotPayload(Bill bill, List<BillItemResponseDTO> itemsToPrint, PrinterSetting s, boolean isJuiceKot, boolean isUpdatedKot) throws IOException {
        return buildKotPayload(bill, itemsToPrint, s, isJuiceKot, isUpdatedKot, false);
    }

    /** {@code reprint}: a copy of a ticket the kitchen already received (or lost): same body, marked REPRINT at the top. */
    public byte[] buildKotPayload(Bill bill, List<BillItemResponseDTO> itemsToPrint, PrinterSetting s, boolean isJuiceKot, boolean isUpdatedKot, boolean reprint) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(CMD_INIT);

        // Configure Line Spacing: ESC 3 n (dots)
        int lineSpacing = (s.getLineSpacing() != null && s.getLineSpacing() > 0) ? s.getLineSpacing() : 24;
        buffer.write(new byte[]{0x1B, 0x33, (byte) Math.min(255, lineSpacing)});

        // Configure High Contrast / Crisp Thermal Print
        if ("CRISP_DARK".equalsIgnoreCase(s.getPrintStyle()) || s.getPrintStyle() == null) {
            buffer.write(new byte[]{0x1B, 0x47, 0x01});
        }

        // 1. Centered Brand Logo at Top (Auto-cropped, pixel-tight) matching UI
        if (s.getLogoEnabled() == null || Boolean.TRUE.equals(s.getLogoEnabled())) {
            int targetLogoSize = (s.getLogoSize() != null && s.getLogoSize() > 0) ? s.getLogoSize() : 130;
            byte[] logoBytes = getLogoRasterBytes(s.getPaperWidthMm() != null ? s.getPaperWidthMm() : 80, targetLogoSize);
            if (logoBytes != null && logoBytes.length > 0) {
                buffer.write(CMD_ALIGN_CENTER);
                buffer.write(logoBytes);
                int gap = (s.getLogoGap() != null && s.getLogoGap() >= 0) ? s.getLogoGap() : 2;
                if (gap > 0) {
                    buffer.write(new byte[]{0x1B, 0x4A, (byte) Math.min(255, gap * 4)});
                }
                buffer.write("\n".getBytes(StandardCharsets.UTF_8));
            }
        }

        // 2. Centered KOT Header (Clean bold typography, normal height)
        buffer.write(CMD_ALIGN_CENTER);
        buffer.write(CMD_TXT_NORMAL);
        buffer.write(CMD_BOLD_ON);

        String baseKotTitle;
        if (isJuiceKot) {
            String cfgJuiceTitle = (s.getJuiceKotHeaderTitle() != null && !s.getJuiceKotHeaderTitle().isBlank()) ? s.getJuiceKotHeaderTitle().trim() : "*** JUICE ORDER ***";
            baseKotTitle = isUpdatedKot ? String.format("*** JUICE ORDER (UPDATE #%d) ***", bill.getKotUpdateCount()) : cfgJuiceTitle;
        } else {
            String cfgTitle = (s.getKotHeaderTitle() != null && !s.getKotHeaderTitle().isBlank()) ? s.getKotHeaderTitle().trim() : "*** KITCHEN ORDER ***";
            baseKotTitle = isUpdatedKot ? String.format("*** KITCHEN ORDER (UPDATE #%d) ***", bill.getKotUpdateCount()) : cfgTitle;
        }
        buffer.write((baseKotTitle + "\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_BOLD_OFF);

        // Subheader
        String kotSub;
        if (isJuiceKot) {
            kotSub = (s.getJuiceKotSubheader() != null && !s.getJuiceKotSubheader().isBlank()) ? s.getJuiceKotSubheader().trim() : "NERAM - JUICE BAR";
        } else {
            kotSub = (s.getKotSubheader() != null && !s.getKotSubheader().isBlank()) ? s.getKotSubheader().trim() : "NERAM - MAIN KITCHEN";
        }
        for (String line : kotSub.split("\r?\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                buffer.write((trimmed + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        if (reprint) {
            buffer.write(CMD_BOLD_ON);
            buffer.write("*** REPRINT ***\n".getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
            buffer.write(("Copy printed " + LocalDateTime.now(ZoneId.of("Asia/Kolkata")).format(KOT_REPRINT_TIME) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        if (isUpdatedKot) {
            buffer.write(CMD_BOLD_ON);
            buffer.write("** ATTENTION: ORDER MODIFIED **\n".getBytes(StandardCharsets.UTF_8));
            buffer.write(CMD_BOLD_OFF);
        }

        // Dashed Divider Line
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // 3. Location Header: Clean Black Text on White Background (no inversion)
        boolean isOutdoor = bill.getOrderType() == Bill.OrderType.OUTDOOR || (bill.getOutdoorToken() != null && !bill.getOutdoorToken().isBlank());
        boolean isTakeaway = bill.getOrderType() == Bill.OrderType.TAKEAWAY || (bill.getTakeawayToken() != null && !bill.getTakeawayToken().isBlank());
        buffer.write(CMD_ALIGN_CENTER);
        buffer.write(CMD_TXT_NORMAL);
        buffer.write(CMD_BOLD_ON);

        int width = (s.getPaperWidthMm() != null && s.getPaperWidthMm() == 58) ? 32 : LINE_WIDTH_80MM;
        String bannerContent;
        if (isOutdoor) {
            String tok = bill.getOutdoorToken() != null ? bill.getOutdoorToken() : "OUT";
            bannerContent = "OUTDOOR • TOKEN: " + tok;
        } else if (isTakeaway) {
            String tok = bill.getTakeawayToken() != null ? bill.getTakeawayToken() : "TAK";
            bannerContent = "TAKEAWAY • TOKEN: " + tok;
        } else {
            String tbl = bill.getTableNumber() != null ? bill.getTableNumber() : "Takeaway";
            bannerContent = "TABLE: " + tbl.toUpperCase();
        }
        int padTotal = Math.max(2, width - bannerContent.length());
        int padLeft = padTotal / 2;
        int padRight = padTotal - padLeft;
        buffer.write((" ".repeat(padLeft) + bannerContent + " ".repeat(padRight) + "\n").getBytes(StandardCharsets.UTF_8));
        if (isOutdoor && bill.getCustomerName() != null && !bill.getCustomerName().isBlank()) {
            String custLine = "Guest: " + bill.getCustomerName();
            int cPadTotal = Math.max(2, width - custLine.length());
            int cPadLeft = cPadTotal / 2;
            int cPadRight = cPadTotal - cPadLeft;
            buffer.write((" ".repeat(cPadLeft) + custLine + " ".repeat(cPadRight) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        buffer.write(CMD_BOLD_OFF);

        // 4. Order Metadata (REF, Date/Time, Server, Order #)
        buffer.write(CMD_ALIGN_LEFT);
        String kotRef = bill.getKotNumber() != null ? bill.getKotNumber() : (bill.getBillNumber() != null ? bill.getBillNumber() : "NRM-0000");

        // The ticket shows when THIS kitchen ticket was printed, not when the order was first created,
        // so a second round for the same table printed at 1:30 reads 1:30 even though the order started at 1:00.
        LocalDateTime ticketDt = kotTicketTime(bill);
        DateTimeFormatter istFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, h:mm:ss a", Locale.ENGLISH);
        String formattedDt = ticketDt.format(istFormatter).toLowerCase(Locale.ENGLISH);

        buffer.write(formatTwoColumns("REF: " + kotRef, formattedDt).getBytes(StandardCharsets.UTF_8));

        String serverName = getUserName(bill.getCreatedBy());
        if (serverName == null || serverName.isBlank()) serverName = "Staff";

        String orderRef = "";
        if (kotRef.contains("-")) {
            String[] parts = kotRef.split("-");
            orderRef = "Order #" + parts[parts.length - 1];
        } else {
            orderRef = "Order #" + kotRef;
        }

        buffer.write(formatTwoColumns("Server: " + serverName, orderRef).getBytes(StandardCharsets.UTF_8));
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // 5. KOT Items Table Header (Normal font, bold)
        buffer.write(CMD_ALIGN_LEFT);
        buffer.write(CMD_BOLD_ON);
        if (isUpdatedKot) {
            buffer.write(String.format("%-36s %11s\n", "UPDATED ITEM", "CHANGE").getBytes(StandardCharsets.UTF_8));
        } else {
            buffer.write(formatKotHeader().getBytes(StandardCharsets.UTF_8));
        }
        buffer.write(CMD_BOLD_OFF);
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // 6. KOT Items List
        int totalKotQty = 0;
        int totalKotItems = 0;
        for (BillItemResponseDTO item : itemsToPrint) {
            totalKotItems++;
            int qty = item.getQuantity() != null ? item.getQuantity() : 1;
            String badgeStr;
            if ("REMOVED".equalsIgnoreCase(item.getUpdateType())) {
                badgeStr = "-CAN";
            } else if ("NOTE_UPDATED".equalsIgnoreCase(item.getUpdateType())) {
                badgeStr = "*NOTE";
            } else if (isUpdatedKot && item.getDeltaQuantity() != null && item.getDeltaQuantity() > 0) {
                badgeStr = "+" + item.getDeltaQuantity();
                totalKotQty += item.getDeltaQuantity();
            } else if (isUpdatedKot && item.getDeltaQuantity() != null && item.getDeltaQuantity() < 0) {
                badgeStr = String.valueOf(item.getDeltaQuantity());
            } else {
                badgeStr = String.valueOf(qty);
                totalKotQty += qty;
            }

            buffer.write(CMD_ALIGN_LEFT);
            buffer.write(CMD_BOLD_ON);
            buffer.write(formatKotRowBadgeBytes(item.getItemName(), badgeStr, width));
            buffer.write(CMD_BOLD_OFF);

            // Print Update message if present
            if (item.getUpdateMessage() != null && !item.getUpdateMessage().isBlank()) {
                buffer.write(("  * " + item.getUpdateMessage() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            // Print Customization Note if present
            if (item.getCustomization() != null && !item.getCustomization().trim().isBlank()) {
                buffer.write(("  * NOTE: " + item.getCustomization().trim() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            printItemSnapshots(buffer, item.getModifiersSnapshot(), item.getComboSnapshot());
            if (Boolean.TRUE.equals(item.getIsTakeaway()) || isTakeaway) {
                buffer.write(("  *** [PARCEL / TAKEAWAY PACK] ***\n").getBytes(StandardCharsets.UTF_8));
            }
        }

        // 7. Total Items / Qty (Enhanced +1 font height for clear kitchen visibility)
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_TXT_DOUBLE_H);
        buffer.write(CMD_BOLD_ON);
        if (isUpdatedKot) {
            buffer.write(formatTwoColumns("TOTAL UPDATED ITEMS:", totalKotItems + " Item" + (totalKotItems == 1 ? "" : "s")).getBytes(StandardCharsets.UTF_8));
        } else {
            buffer.write(formatTwoColumns("TOTAL ITEMS / QTY:", totalKotItems + " Item" + (totalKotItems == 1 ? "" : "s") + " (Qty: " + totalKotQty + ")").getBytes(StandardCharsets.UTF_8));
        }
        buffer.write(CMD_TXT_NORMAL);
        buffer.write(CMD_BOLD_OFF);
        buffer.write((divider('-') + "\n").getBytes(StandardCharsets.UTF_8));

        // 8. Notes
        String notesText = (bill.getNotes() != null && !bill.getNotes().isBlank())
                ? bill.getNotes().trim() : (isOutdoor ? (isJuiceKot ? "Outdoor Juice Order" : "Outdoor Kitchen Order") : (isJuiceKot ? "Juice Order Ticket" : "Kitchen Order Ticket"));
        buffer.write(CMD_BOLD_ON);
        buffer.write(("Notes: " + notesText + "\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(CMD_BOLD_OFF);

        // 9. Centered Footer
        buffer.write(CMD_ALIGN_CENTER);
        String kotFooter;
        if (isJuiceKot) {
            kotFooter = (s.getJuiceKotFooterMessage() != null && !s.getJuiceKotFooterMessage().isBlank())
                    ? s.getJuiceKotFooterMessage().trim() : "--- END OF JUICE KOT ---";
        } else {
            kotFooter = (s.getKotFooterMessage() != null && !s.getKotFooterMessage().isBlank())
                    ? s.getKotFooterMessage().trim() : "--- END OF KOT ---";
        }
        buffer.write((kotFooter + "\n").getBytes(StandardCharsets.UTF_8));

        int footSpacing = (s.getFooterSpacing() != null && s.getFooterSpacing() >= 0) ? s.getFooterSpacing() : 1;
        for (int i = 0; i < footSpacing; i++) {
            buffer.write("\n".getBytes(StandardCharsets.UTF_8));
        }

        if (Boolean.TRUE.equals(s.getAutoCut())) {
            buffer.write(CMD_CUT_PAPER);
        }

        return buffer.toByteArray();
    }

    /**
     * Print Kitchen Order Ticket (KOT) directly to thermal printer(s).
     * Automatically splits non-Juice items to Kitchen KOT and Juice items to Juice KOT.
     */
    @Transactional
    public PrintResultDTO printKot(Long billId, String idempotencyKey) {
        // An owner/main manager looking at the whole cafe prints with the settings and printers of the bill's branch.
        if (com.neram.pos.access.OutletContext.current().isEmpty()) {
            Long branch = branchOfBill(billId);
            if (branch != null) {
                return com.neram.pos.access.OutletContext.callAs(branch, () -> printKot(billId, idempotencyKey));
            }
        }
        return printKotInternal(billId, idempotencyKey, false);
    }

    private static final java.time.format.DateTimeFormatter KOT_REPRINT_TIME = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a", Locale.ENGLISH);
    /** A reprint of the same order cannot be repeated within this time (a double tap must not print two copies). */
    static final long KOT_REPRINT_COOLDOWN_MS = 15_000L;
    /** Bill ids repeat across cafes (each has its own database), so cooldowns are keyed by cafe + bill. */
    private static String reprintKey(Long billId) {
        return com.neram.pos.tenancy.TenantContext.currentId().orElse(0L) + ":" + billId;
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastKotReprint = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Prints the last kitchen ticket of an order that is being prepared once more (the printer restarted and the ticket never came
     * out). The ticket has the same body as the one sent (the latest round) and a REPRINT marker. It changes nothing on the order:
     * not its status, nor what counts as already sent to the kitchen, so the normal flow carries on exactly as before.
     */
    public PrintResultDTO reprintKot(Long billId) {
        // An owner/main manager looking at the whole cafe prints with the settings and printers of the bill's branch.
        if (com.neram.pos.access.OutletContext.current().isEmpty()) {
            Long branch = branchOfBill(billId);
            if (branch != null) {
                return com.neram.pos.access.OutletContext.callAs(branch, () -> reprintKot(billId));
            }
        }
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + billId));
        if (bill.getStatus() == Bill.Status.KOT_REQUESTED) {
            throw new IllegalStateException("The kitchen ticket for this order is already waiting to print.");
        }
        if (bill.getStatus() != Bill.Status.KOT_PRINTED || bill.getKotPrintedAt() == null) {
            throw new IllegalStateException(bill.getStatus() == Bill.Status.ACTIVE
                    ? "This order has not been sent to the kitchen yet. Use Send to Kitchen."
                    : "A kitchen ticket can only be reprinted while the order is being prepared.");
        }
        long now = System.currentTimeMillis();
        Long last = lastKotReprint.get(reprintKey(billId));
        if (last != null && now - last < KOT_REPRINT_COOLDOWN_MS) {
            throw new IllegalStateException("A reprint was just sent for this order. Please wait a few seconds.");
        }
        lastKotReprint.put(reprintKey(billId), now);
        if (lastKotReprint.size() > 500) lastKotReprint.entrySet().removeIf(e -> now - e.getValue() > KOT_REPRINT_COOLDOWN_MS);

        String key = "KOT_" + bill.getId() + "_v" + (bill.getKotUpdateCount() != null ? bill.getKotUpdateCount() : 0) + "_RP" + now;
        log.info("Reprinting the kitchen ticket of order #{} (key {})", bill.getBillNumber(), key);
        return printKotInternal(billId, key, true);
    }

    private PrintResultDTO printKotInternal(Long billId, String idempotencyKey, boolean reprint) {
        PrinterSetting s = getSettings();
        String kotHost = (s.getKotHost() != null && !s.getKotHost().isBlank()) ? s.getKotHost() : "192.168.1.37";
        int kotPort = (s.getKotPort() != null && s.getKotPort() > 0) ? s.getKotPort() : 9100;
        boolean masterEnabled = s.getEnabled() == null || Boolean.TRUE.equals(s.getEnabled());
        boolean kotEnabled = masterEnabled && (s.getKotEnabled() == null || Boolean.TRUE.equals(s.getKotEnabled()));

        String juiceKotHost = (s.getJuiceKotHost() != null && !s.getJuiceKotHost().isBlank()) ? s.getJuiceKotHost() : "192.168.1.42";
        int juiceKotPort = (s.getJuiceKotPort() != null && s.getJuiceKotPort() > 0) ? s.getJuiceKotPort() : 9100;
        boolean juiceKotEnabled = masterEnabled && (s.getJuiceKotEnabled() == null || Boolean.TRUE.equals(s.getJuiceKotEnabled()));

        if (!kotEnabled && !juiceKotEnabled) {
            return PrintResultDTO.builder()
                    .success(false)
                    .message("KOT and Juice KOT thermal printing are currently disabled.")
                    .host(kotHost)
                    .port(kotPort)
                    .billId(billId)
                    .printType("KOT")
                    .status("DISABLED")
                    .build();
        }

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + billId));

        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : "KOT_" + bill.getId() + "_v" + (bill.getKotUpdateCount() != null ? bill.getKotUpdateCount() : 0);

        // If the order has been cancelled or voided, route to cancellation notice
        if (bill.getStatus() == Bill.Status.CANCELLED || bill.getStatus() == Bill.Status.VOIDED || bill.getKotCancelledAt() != null) {
            return printCancelledKot(billId, bill.getVoidReason(), effectiveKey);
        }

        // Initial KOT print: record kotPrintedAt (a reprint never touches the order)
        if (!reprint && bill.getKotPrintedAt() == null) {
            bill.setKotPrintedAt(LocalDateTime.now());
            if (bill.getStatus() == Bill.Status.ACTIVE || bill.getStatus() == Bill.Status.KOT_REQUESTED) {
                bill.setStatus(Bill.Status.KOT_PRINTED);
            }
            billRepository.save(bill);
        }

        try {
            boolean isUpdatedKot = (bill.getKotUpdateCount() != null && bill.getKotUpdateCount() > 0)
                    && (bill.getKotLastChanges() != null && !bill.getKotLastChanges().isBlank());

            List<BillItemResponseDTO> allKotItems = new ArrayList<>();
            if (isUpdatedKot) {
                allKotItems = BillService.parseKotChanges(bill.getKotLastChanges(), bill.getItems());
            }
            if (allKotItems.isEmpty() && bill.getItems() != null) {
                for (BillItem item : bill.getItems()) {
                    allKotItems.add(BillItemResponseDTO.builder()
                            .id(item.getId())
                            .menuItemId(item.getMenuItemId())
                            .itemName(item.getItemName())
                            .quantity(item.getQuantity() != null ? item.getQuantity() : 1)
                            .customization(item.getCustomization())
                            .isTakeaway(Boolean.TRUE.equals(item.getIsTakeaway()))
                            .build());
                }
            }

            if (allKotItems.isEmpty()) {
                return PrintResultDTO.builder()
                        .success(false)
                        .message("No items found to print KOT.")
                        .host(kotHost)
                        .port(kotPort)
                        .billId(bill.getId())
                        .billNumber(bill.getBillNumber())
                        .printType("KOT")
                        .status("FAILED")
                        .build();
            }

            List<BillItemResponseDTO> kitchenItems = new ArrayList<>();
            List<BillItemResponseDTO> juiceItems = new ArrayList<>();

            for (BillItemResponseDTO it : allKotItems) {
                if (isJuiceItem(it.getMenuItemId(), it.getItemName(), s)) {
                    juiceItems.add(it);
                } else {
                    kitchenItems.add(it);
                }
            }

            String kotRef = bill.getKotNumber() != null ? bill.getKotNumber() : (bill.getBillNumber() != null ? bill.getBillNumber() : "NRM-0000");

            // Case 1: Mixed order (both non-Juice and Juice items present)
            if (!kitchenItems.isEmpty() && !juiceItems.isEmpty()) {
                PrintResultDTO kitchenResult = null;
                PrintResultDTO juiceResult = null;

                if (kotEnabled) {
                    byte[] kitchenBytes = buildKotPayload(bill, kitchenItems, s, false, isUpdatedKot, reprint);
                    String kitchenKey = effectiveKey + "_KITCHEN";
                    kitchenResult = dispatchPrintJob(kotHost, kotPort, kitchenBytes, "KOT", bill.getId(), kotRef, kitchenKey);
                }
                if (juiceKotEnabled) {
                    byte[] juiceBytes = buildKotPayload(bill, juiceItems, s, true, isUpdatedKot, reprint);
                    String juiceKey = effectiveKey + "_JUICE";
                    juiceResult = dispatchPrintJob(juiceKotHost, juiceKotPort, juiceBytes, "JUICE_KOT", bill.getId(), kotRef, juiceKey);
                }

                boolean success = (kitchenResult == null || kitchenResult.isSuccess()) && (juiceResult == null || juiceResult.isSuccess());
                String jId = (kitchenResult != null ? kitchenResult.getJobId() : "")
                        + (kitchenResult != null && juiceResult != null ? "," : "")
                        + (juiceResult != null ? juiceResult.getJobId() : "");

                return PrintResultDTO.builder()
                        .success(success)
                        .message(String.format("Split KOT dispatched (Kitchen: %d items to %s:%d, Juice KOT: %d items to %s:%d)",
                                kitchenItems.size(), kotHost, kotPort, juiceItems.size(), juiceKotHost, juiceKotPort))
                        .host(kotHost + " / " + juiceKotHost)
                        .port(kotPort)
                        .billId(bill.getId())
                        .billNumber(bill.getBillNumber())
                        .printType("KOT_SPLIT")
                        .jobId(jId)
                        .status(success ? "QUEUED" : "FAILED")
                        .printedAt(LocalDateTime.now())
                        .build();
            }

            // Case 2: Juice-only order
            if (!juiceItems.isEmpty()) {
                if (!juiceKotEnabled) {
                    return PrintResultDTO.builder()
                            .success(false)
                            .message("Juice KOT thermal printing is currently disabled.")
                            .host(juiceKotHost)
                            .port(juiceKotPort)
                            .billId(bill.getId())
                            .billNumber(bill.getBillNumber())
                            .printType("JUICE_KOT")
                            .status("DISABLED")
                            .build();
                }
                byte[] juiceBytes = buildKotPayload(bill, juiceItems, s, true, isUpdatedKot, reprint);
                return dispatchPrintJob(juiceKotHost, juiceKotPort, juiceBytes, "JUICE_KOT", bill.getId(), kotRef, effectiveKey);
            }

            // Case 3: Food-only (non-Juice) order
            if (!kotEnabled) {
                return PrintResultDTO.builder()
                        .success(false)
                        .message("KOT thermal printing is currently disabled.")
                        .host(kotHost)
                        .port(kotPort)
                        .billId(bill.getId())
                        .billNumber(bill.getBillNumber())
                        .printType("KOT")
                        .status("DISABLED")
                        .build();
            }
            byte[] kitchenBytes = buildKotPayload(bill, kitchenItems, s, false, isUpdatedKot, reprint);
            return dispatchPrintJob(kotHost, kotPort, kitchenBytes, "KOT", bill.getId(), kotRef, effectiveKey);

        } catch (Exception e) {
            log.error("Failed to print KOT for Bill #{} to {}:{}: {}", bill.getBillNumber(), kotHost, kotPort, e.getMessage());
            return PrintResultDTO.builder()
                    .success(false)
                    .message(String.format("KOT print failed: %s", e.getMessage()))
                    .host(kotHost)
                    .port(kotPort)
                    .billId(bill.getId())
                    .billNumber(bill.getBillNumber())
                    .printType("KOT")
                    .status("FAILED")
                    .build();
        }
    }

    /**
     * Print an Updated KOT notice with prominent order number, change summary, and background watermark
     */
    @Transactional
    public PrintResultDTO printUpdatedKot(Long billId, List<String> changeLines, String idempotencyKey) {
        // An owner/main manager looking at the whole cafe prints with the settings and printers of the bill's branch.
        if (com.neram.pos.access.OutletContext.current().isEmpty()) {
            Long branch = branchOfBill(billId);
            if (branch != null) {
                return com.neram.pos.access.OutletContext.callAs(branch, () -> printUpdatedKot(billId, changeLines, idempotencyKey));
            }
        }
        PrinterSetting s = getSettings();
        String kotHost = (s.getKotHost() != null && !s.getKotHost().isBlank()) ? s.getKotHost() : "192.168.1.37";
        int kotPort = (s.getKotPort() != null && s.getKotPort() > 0) ? s.getKotPort() : 9100;
        boolean kotEnabled = Boolean.TRUE.equals(s.getKotEnabled()) && Boolean.TRUE.equals(s.getEnabled());

        if (!kotEnabled) {
            return PrintResultDTO.builder()
                    .success(false)
                    .message("KOT thermal printing is currently disabled.")
                    .host(kotHost)
                    .port(kotPort)
                    .billId(billId)
                    .printType("KOT_UPDATE")
                    .status("DISABLED")
                    .build();
        }

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + billId));

        int paperWidth = (s.getPaperWidthMm() != null) ? s.getPaperWidthMm() : 80;
        List<String> effectiveChanges = (changeLines != null && !changeLines.isEmpty()) ? changeLines :
                (bill.getKotLastChanges() != null ? java.util.Arrays.asList(bill.getKotLastChanges().split("\r?\n")) : List.of());
        LocalDateTime updateTime = kotTicketTime(bill);

        byte[] rasterData = buildWatermarkedKotRaster(bill, "UPDATED", effectiveChanges, updateTime, null, paperWidth);

        String kotRef = bill.getKotNumber() != null ? bill.getKotNumber() : bill.getBillNumber();
        String key = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey
                : ("KOT_UPDATE_" + bill.getId() + "_v" + (bill.getKotUpdateCount() != null ? bill.getKotUpdateCount() : 1));

        return dispatchPrintJob(kotHost, kotPort, rasterData, "KOT_UPDATE", bill.getId(), kotRef, key);
    }

    /**
     * Print a Cancelled KOT notice with prominent order number, reason, cancelled items, and background watermark
     */
    @Transactional
    public PrintResultDTO printCancelledKot(Long billId, String cancellationReason, String idempotencyKey) {
        // An owner/main manager looking at the whole cafe prints with the settings and printers of the bill's branch.
        if (com.neram.pos.access.OutletContext.current().isEmpty()) {
            Long branch = branchOfBill(billId);
            if (branch != null) {
                return com.neram.pos.access.OutletContext.callAs(branch, () -> printCancelledKot(billId, cancellationReason, idempotencyKey));
            }
        }
        PrinterSetting s = getSettings();
        String kotHost = (s.getKotHost() != null && !s.getKotHost().isBlank()) ? s.getKotHost() : "192.168.1.37";
        int kotPort = (s.getKotPort() != null && s.getKotPort() > 0) ? s.getKotPort() : 9100;
        boolean kotEnabled = Boolean.TRUE.equals(s.getKotEnabled()) && Boolean.TRUE.equals(s.getEnabled());

        if (!kotEnabled) {
            return PrintResultDTO.builder()
                    .success(false)
                    .message("KOT thermal printing is currently disabled.")
                    .host(kotHost)
                    .port(kotPort)
                    .billId(billId)
                    .printType("KOT_CANCEL")
                    .status("DISABLED")
                    .build();
        }

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + billId));

        int paperWidth = (s.getPaperWidthMm() != null) ? s.getPaperWidthMm() : 80;
        LocalDateTime cancelTime = bill.getKotCancelledAt() != null ? bill.getKotCancelledAt() : (bill.getVoidedAt() != null ? bill.getVoidedAt() : LocalDateTime.now());
        String reason = (cancellationReason != null && !cancellationReason.isBlank()) ? cancellationReason : bill.getVoidReason();

        byte[] rasterData = buildWatermarkedKotRaster(bill, "CANCELLED", List.of(), cancelTime, reason, paperWidth);

        String kotRef = bill.getKotNumber() != null ? bill.getKotNumber() : bill.getBillNumber();
        String key = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : ("KOT_CANCEL_" + bill.getId());

        return dispatchPrintJob(kotHost, kotPort, rasterData, "KOT_CANCEL", bill.getId(), kotRef, key);
    }

    /**
     * Generate ESC/POS raster bitmap for KOT Update or Cancellation notices with low-opacity watermark.
     * The foreground text is rendered in solid black, while the watermark is stippled in the background
     * using a Bayer matrix so item names, quantities, and notes remain 100% readable.
     */
    public byte[] buildWatermarkedKotRaster(
            Bill bill,
            String noticeType, // "UPDATED" or "CANCELLED"
            List<String> changeLines,
            LocalDateTime noticeTime,
            String reason,
            int paperWidthMm) {

        int paperDots = (paperWidthMm == 58) ? 384 : 576;
        int margin = (paperWidthMm == 58) ? 14 : 24;

        Font fontTitle = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 20 : 26);
        Font fontOrderRef = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 24 : 32);
        Font fontLocation = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 18 : 24);
        Font fontSection = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 16 : 20);
        Font fontChange = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 15 : 18);
        Font fontItem = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 16 : 20);
        Font fontText = new Font(Font.SANS_SERIF, Font.PLAIN, (paperDots == 384) ? 14 : 17);
        Font fontNote = new Font(Font.SANS_SERIF, Font.ITALIC, (paperDots == 384) ? 13 : 15);
        Font fontFooter = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 15 : 18);
        Font fontWatermark = new Font(Font.SANS_SERIF, Font.BOLD, (paperDots == 384) ? 60 : 76);

        // Pre-calculate target canvas height
        int estimatedHeight = 320;
        if ("UPDATED".equalsIgnoreCase(noticeType)) {
            if (changeLines != null && !changeLines.isEmpty()) {
                estimatedHeight += 60 + (changeLines.size() * 32);
            }
            if (bill.getItems() != null && !bill.getItems().isEmpty()) {
                estimatedHeight += 60;
                for (BillItem it : bill.getItems()) {
                    estimatedHeight += 32;
                    if (Boolean.TRUE.equals(it.getIsTakeaway()) || (bill.getOrderType() == Bill.OrderType.TAKEAWAY)) {
                        estimatedHeight += 24;
                    }
                    if (it.getCustomization() != null && !it.getCustomization().isBlank()) {
                        estimatedHeight += 24;
                    }
                }
            }
        } else {
            if (reason != null && !reason.isBlank()) {
                estimatedHeight += 40;
            }
            if (bill.getItems() != null && !bill.getItems().isEmpty()) {
                estimatedHeight += 60;
                for (BillItem it : bill.getItems()) {
                    estimatedHeight += 32;
                    if (Boolean.TRUE.equals(it.getIsTakeaway()) || (bill.getOrderType() == Bill.OrderType.TAKEAWAY)) {
                        estimatedHeight += 24;
                    }
                    if (it.getCustomization() != null && !it.getCustomization().isBlank()) {
                        estimatedHeight += 24;
                    }
                }
            }
        }
        if (bill.getNotes() != null && !bill.getNotes().isBlank()) {
            estimatedHeight += 60;
        }
        estimatedHeight += 120;
        int targetH = Math.max(estimatedHeight, 520);

        BufferedImage img = new BufferedImage(paperDots, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();

        // 1. Fill background pure white
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, paperDots, targetH);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 2. Draw low-opacity background watermark ("UPDATED" or "CANCELLED")
        String watermarkWord = "UPDATED".equalsIgnoreCase(noticeType) ? "UPDATED" : "CANCELLED";
        g2d.setFont(fontWatermark);
        g2d.setColor(new Color(222, 222, 222));
        AffineTransform origTx = g2d.getTransform();
        g2d.rotate(Math.toRadians(-28), paperDots / 2.0, targetH / 2.0);

        FontMetrics wmMetrics = g2d.getFontMetrics(fontWatermark);
        int wmWidth = wmMetrics.stringWidth(watermarkWord);
        for (int wY = -targetH; wY <= targetH * 2; wY += 190) {
            int wX = (paperDots - wmWidth) / 2;
            g2d.drawString(watermarkWord, wX, wY);
        }
        g2d.setTransform(origTx);

        // 3. Draw foreground solid black text
        g2d.setColor(Color.BLACK);
        int curY = 38;

        // Banner box
        String bannerText = "UPDATED".equalsIgnoreCase(noticeType)
                ? "*** KOT UPDATE NOTICE ***"
                : "*** KOT CANCELLATION NOTICE ***";
        g2d.setFont(fontTitle);
        FontMetrics fmTitle = g2d.getFontMetrics();
        g2d.drawString(bannerText, (paperDots - fmTitle.stringWidth(bannerText)) / 2, curY);
        curY += 34;

        // Prominent Order Number
        String kotRef = bill.getKotNumber() != null ? bill.getKotNumber() : bill.getBillNumber();
        String orderLine = "ORDER #" + kotRef;
        g2d.setFont(fontOrderRef);
        FontMetrics fmOrder = g2d.getFontMetrics();
        g2d.drawString(orderLine, (paperDots - fmOrder.stringWidth(orderLine)) / 2, curY);
        curY += 32;

        // Location / Table / Token
        boolean isOutdoor = bill.getOrderType() == Bill.OrderType.OUTDOOR || (bill.getOutdoorToken() != null && !bill.getOutdoorToken().isBlank());
        boolean isTakeaway = bill.getOrderType() == Bill.OrderType.TAKEAWAY || (bill.getTakeawayToken() != null && !bill.getTakeawayToken().isBlank());
        String locationText;
        if (isOutdoor) {
            String cust = bill.getCustomerName() != null ? bill.getCustomerName() : "Guest";
            String tok = bill.getOutdoorToken() != null ? bill.getOutdoorToken() : "OUT";
            locationText = "[OUTDOOR] " + cust + " (Token: " + tok + ")";
        } else if (isTakeaway) {
            String cust = bill.getCustomerName() != null ? bill.getCustomerName() : "Guest";
            String tok = bill.getTakeawayToken() != null ? bill.getTakeawayToken() : "TAK";
            locationText = "[TAKEAWAY] " + cust + " (Token: " + tok + ")";
        } else {
            String tbl = bill.getTableNumber() != null ? bill.getTableNumber() : "Takeaway";
            locationText = "TABLE: " + tbl.toUpperCase();
        }
        g2d.setFont(fontLocation);
        FontMetrics fmLoc = g2d.getFontMetrics();
        g2d.drawString(locationText, (paperDots - fmLoc.stringWidth(locationText)) / 2, curY);
        curY += 24;

        // Divider
        g2d.setStroke(new java.awt.BasicStroke(2));
        g2d.drawLine(margin, curY, paperDots - margin, curY);
        curY += 24;

        // Timestamp & details
        g2d.setFont(fontText);
        LocalDateTime effectiveTime = noticeTime != null ? noticeTime : LocalDateTime.now();
        String timeStr = effectiveTime.format(DATE_TIME_FORMATTER);

        if ("UPDATED".equalsIgnoreCase(noticeType)) {
            int updateVer = (bill.getKotUpdateCount() != null && bill.getKotUpdateCount() > 0) ? bill.getKotUpdateCount() : 1;
            g2d.drawString("Update Version: #" + updateVer, margin, curY);
            curY += 22;
            g2d.drawString("Update Time   : " + timeStr, margin, curY);
            curY += 22;
        } else {
            g2d.drawString("Cancellation Time: " + timeStr, margin, curY);
            curY += 22;
            String cancelReason = (reason != null && !reason.isBlank()) ? reason : (bill.getVoidReason() != null ? bill.getVoidReason() : "Cancelled by staff");
            g2d.drawString("Reason           : " + cancelReason, margin, curY);
            curY += 22;
        }

        String serverName = getUserName(bill.getCreatedBy());
        if (serverName != null && !serverName.isBlank()) {
            g2d.drawString("Server           : " + serverName, margin, curY);
            curY += 22;
        }

        // Divider
        g2d.drawLine(margin, curY, paperDots - margin, curY);
        curY += 26;

        // Section Content
        if ("UPDATED".equalsIgnoreCase(noticeType)) {
            // Changes Section
            g2d.setFont(fontSection);
            g2d.drawString(">>> WHAT CHANGED <<<", margin, curY);
            curY += 24;

            g2d.setFont(fontChange);
            if (changeLines != null && !changeLines.isEmpty()) {
                for (String cl : changeLines) {
                    if (cl != null && !cl.isBlank()) {
                        g2d.drawString(cl, margin, curY);
                        curY += 24;
                    }
                }
            } else {
                g2d.drawString("(Order updated - see current order below)", margin, curY);
                curY += 24;
            }

            curY += 8;
            g2d.drawLine(margin, curY, paperDots - margin, curY);
            curY += 24;

            // Current Items
            g2d.setFont(fontSection);
            g2d.drawString("CURRENT TOTAL ORDER ITEMS:", margin, curY);
            curY += 24;

            if (bill.getItems() != null && !bill.getItems().isEmpty()) {
                for (BillItem it : bill.getItems()) {
                    int qty = it.getQuantity() != null ? it.getQuantity() : 1;
                    g2d.setFont(fontItem);
                    String itemLine = String.format("%dx  %s", qty, it.getItemName());
                    g2d.drawString(itemLine, margin, curY);
                    curY += 24;

                    if (Boolean.TRUE.equals(it.getIsTakeaway()) || isTakeaway) {
                        g2d.setFont(fontChange);
                        g2d.drawString("  *** [PARCEL / TAKEAWAY PACK] ***", margin + 12, curY);
                        curY += 20;
                    }

                    if (it.getCustomization() != null && !it.getCustomization().isBlank()) {
                        g2d.setFont(fontNote);
                        g2d.drawString("  * NOTE: " + it.getCustomization().trim(), margin + 12, curY);
                        curY += 20;
                    }
                }
            }
        } else {
            // Cancelled Section
            g2d.setFont(fontSection);
            g2d.drawString("CANCELLED ITEMS (DO NOT PREPARE):", margin, curY);
            curY += 24;

            if (bill.getItems() != null && !bill.getItems().isEmpty()) {
                for (BillItem it : bill.getItems()) {
                    int qty = it.getQuantity() != null ? it.getQuantity() : 1;
                    g2d.setFont(fontItem);
                    String itemLine = String.format("%dx  %s", qty, it.getItemName());
                    g2d.drawString(itemLine, margin, curY);
                    curY += 24;

                    if (Boolean.TRUE.equals(it.getIsTakeaway()) || isTakeaway) {
                        g2d.setFont(fontChange);
                        g2d.drawString("  *** [PARCEL / TAKEAWAY PACK] ***", margin + 12, curY);
                        curY += 20;
                    }

                    if (it.getCustomization() != null && !it.getCustomization().isBlank()) {
                        g2d.setFont(fontNote);
                        g2d.drawString("  * NOTE: " + it.getCustomization().trim(), margin + 12, curY);
                        curY += 20;
                    }
                }
            }
        }

        // Special notes if present
        if (bill.getNotes() != null && !bill.getNotes().isBlank()) {
            curY += 8;
            g2d.drawLine(margin, curY, paperDots - margin, curY);
            curY += 22;
            g2d.setFont(fontSection);
            g2d.drawString("ORDER NOTE: " + bill.getNotes().trim(), margin, curY);
            curY += 22;
        }

        // Divider
        curY += 8;
        g2d.drawLine(margin, curY, paperDots - margin, curY);
        curY += 28;

        // Footer
        g2d.setFont(fontFooter);
        FontMetrics fmFooter = g2d.getFontMetrics();
        String endMsg = "UPDATED".equalsIgnoreCase(noticeType)
                ? "--- END OF UPDATE NOTICE ---"
                : "--- ORDER CANCELLED ---";
        g2d.drawString(endMsg, (paperDots - fmFooter.stringWidth(endMsg)) / 2, curY);
        curY += 35;

        g2d.dispose();

        // Crop to actual rendered height
        int actualHeight = Math.min(curY, targetH);
        BufferedImage cropped = img.getSubimage(0, 0, paperDots, actualHeight);

        return convertImageToEscPosRaster(cropped, paperDots);
    }

    private byte[] convertImageToEscPosRaster(BufferedImage img, int paperDots) {
        int targetW = paperDots;
        int targetH = img.getHeight();
        int totalBytesPerRow = paperDots / 8;

        int[][] bayer4x4 = {
            {  15, 195,  60, 240 },
            { 135,  75, 180, 120 },
            {  45, 225,  30, 210 },
            { 165, 105, 150,  90 }
        };

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // ESC @ (Initialize)
            baos.write(0x1B);
            baos.write(0x40);

            // GS v 0 0 xL xH yL yH
            baos.write(0x1D);
            baos.write(0x76);
            baos.write(0x30);
            baos.write(0x00);
            baos.write(totalBytesPerRow % 256);
            baos.write(totalBytesPerRow / 256);
            baos.write(targetH % 256);
            baos.write(targetH / 256);

            for (int y = 0; y < targetH; y++) {
                int currentByte = 0;
                int bitCount = 0;
                for (int x = 0; x < targetW; x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int gVal = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int luminance = (int) (0.299 * r + 0.587 * gVal + 0.114 * b);

                    int bit;
                    if (luminance < 120) {
                        bit = 1; // Solid black (text, lines)
                    } else if (luminance > 245) {
                        bit = 0; // Pure white
                    } else {
                        int threshold = bayer4x4[y % 4][x % 4];
                        bit = (luminance < threshold) ? 1 : 0; // Dithered watermark
                    }

                    currentByte = (currentByte << 1) | bit;
                    bitCount++;
                    if (bitCount == 8) {
                        baos.write(currentByte);
                        currentByte = 0;
                        bitCount = 0;
                    }
                }
                if (bitCount > 0) {
                    currentByte <<= (8 - bitCount);
                    baos.write(currentByte);
                }
            }

            // Feed paper
            baos.write(0x0A);
            baos.write(0x0A);
            baos.write(0x0A);
            baos.write(0x0A);
            // Cut paper
            baos.write(CMD_CUT_PAPER);

            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate ESC/POS raster for watermarked KOT: {}", e.getMessage());
            return new byte[0];
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /** Heads-up shown to the operator when the job was queued but is unlikely to print right now. */
    private String offlineWarning(String host, int port, String printType) {
        if (!printBridgeService.isBridgeOnline()) {
            return "The print bridge is offline. The ticket is saved and will print as soon as it reconnects.";
        }
        java.util.Optional<Boolean> reachable = printBridgeService.isPrinterReachable(host, port);
        if (reachable != null && reachable.isPresent() && Boolean.FALSE.equals(reachable.get())) {
            String name = "BILL".equalsIgnoreCase(printType) ? "The bill printer"
                    : "JUICE_KOT".equalsIgnoreCase(printType) ? "The juice KOT printer"
                    : "The kitchen printer";
            return name + " (" + host + ") is not reachable. The ticket is saved and will print when it is back online.";
        }
        return null;
    }

    private PrintResultDTO dispatchPrintJob(String host, int port, byte[] data, String printType, Long billId, String billNumber, String idempotencyKey) {
        String jobId = UUID.randomUUID().toString();
        DevicePrintScope device = DevicePrintScope.current();
        LocalDateTime now = LocalDateTime.now();
        PrintJob job = PrintJob.builder()
                .jobId(jobId)
                .billId(billId)
                .billNumber(billNumber)
                .jobType(printType != null ? printType : "RAW")
                .targetPrinterHost(host)
                .targetPrinterPort(port)
                .payload(data)
                .status(device != null ? PrintJobStatus.CLAIMED : PrintJobStatus.PENDING)
                .attemptCount(device != null ? 1 : 0)
                .claimedByBridgeId(device != null ? device.deviceId() : null)
                .claimedAt(device != null ? now : null)
                .leaseExpiresAt(device != null ? now.plusSeconds(DevicePrintScope.LEASE_SECONDS) : null)
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .updatedAt(now)
                .nextEligibleAt(now)
                .build();

        PrintJob persistedJob = printBridgeService.enqueuePrintJob(job);

        // The phone prints this one itself. If the job already existed (a repeated tap) it is not ours to hand out, so it
        // falls through to the normal queued answer below.
        if (device != null && persistedJob.getStatus() == PrintJobStatus.CLAIMED
                && device.deviceId().equals(persistedJob.getClaimedByBridgeId()) && persistedJob.getPayload() != null) {
            device.add(com.neram.pos.dto.DevicePrintJobDTO.builder()
                    .jobId(persistedJob.getJobId()).printType(printType).host(host).port(port)
                    .dataBase64(java.util.Base64.getEncoder().encodeToString(persistedJob.getPayload())).build());
            return PrintResultDTO.builder()
                    .success(true)
                    .message(String.format("Ready to print from this device to %s:%d", host, port))
                    .host(host).port(port).billId(billId).billNumber(billNumber).printType(printType)
                    .jobId(persistedJob.getJobId())
                    .status(persistedJob.getStatus().name())
                    .attemptCount(persistedJob.getAttemptCount())
                    .printedAt(LocalDateTime.now())
                    .build();
        }

        // All print jobs must go through the ESP32 hardware bridge (no direct socket from backend)
        if (!directPrintEnabled || printBridgeService.isBridgeOnline() || (configuredBridgeToken != null && !configuredBridgeToken.isBlank())) {
            log.info("Print job {} ({}) queued in database for ESP32 print bridge (Online: {})",
                    persistedJob.getJobId(), printType, printBridgeService.isBridgeOnline());
            return PrintResultDTO.builder()
                    .success(true)
                    .message(String.format("Print job queued for ESP32 bridge (%s:%d)", host, port))
                    .host(host)
                    .port(port)
                    .billId(billId)
                    .billNumber(billNumber)
                    .printType(printType)
                    .jobId(persistedJob.getJobId())
                    .status(persistedJob.getStatus().name())
                    .attemptCount(persistedJob.getAttemptCount())
                    .printedAt(LocalDateTime.now())
                    .warning(offlineWarning(host, port, printType))
                    .build();
        }

        // Direct local socket connection (fallback ONLY when no bridge token is configured)
        log.info("No bridge configured. Attempting direct socket print for job {} to {}:{}",
                persistedJob.getJobId(), host, port);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3500); // 3.5s connect timeout
            socket.setSoTimeout(5000); // 5s read/write timeout
            try (OutputStream out = socket.getOutputStream()) {
                out.write(data);
                out.flush();
            }

            persistedJob.setStatus(PrintJobStatus.COMPLETED);
            persistedJob.setSentAt(LocalDateTime.now());
            persistedJob.setCompletedAt(LocalDateTime.now());
            persistedJob.setBytesPrinted(data.length);
            persistedJob.setLastErrorMessage("Direct socket print successful");
            printJobRepository.save(persistedJob);

            if (billId != null) {
                billRepository.findById(billId).ifPresent(b -> {
                    if ("KOT".equalsIgnoreCase(printType)) {
                        b.setKotPrintedAt(LocalDateTime.now());
                    } else if ("BILL".equalsIgnoreCase(printType)) {
                        b.setPrintedAt(LocalDateTime.now());
                    }
                    billRepository.save(b);
                });
            }

            return PrintResultDTO.builder()
                    .success(true)
                    .message(String.format("Printed directly to printer at %s:%d", host, port))
                    .host(host)
                    .port(port)
                    .billId(billId)
                    .billNumber(billNumber)
                    .printType(printType)
                    .jobId(persistedJob.getJobId())
                    .status(PrintJobStatus.COMPLETED.name())
                    .attemptCount(persistedJob.getAttemptCount())
                    .printedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.warn("Direct socket print failed for job {}: {}", persistedJob.getJobId(), e.getMessage());
            persistedJob.setStatus(PrintJobStatus.FAILED);
            persistedJob.setLastErrorMessage("Direct socket failure: " + e.getMessage());
            printJobRepository.save(persistedJob);

            return PrintResultDTO.builder()
                    .success(false)
                    .message(String.format("Print failed on %s:%d: %s", host, port, e.getMessage()))
                    .host(host)
                    .port(port)
                    .billId(billId)
                    .billNumber(billNumber)
                    .printType(printType)
                    .jobId(persistedJob.getJobId())
                    .status(PrintJobStatus.FAILED.name())
                    .attemptCount(persistedJob.getAttemptCount())
                    .retryable(true)
                    .build();
        }
    }

    private String divider(char ch) {
        return String.valueOf(ch).repeat(LINE_WIDTH_80MM);
    }

    private String formatTwoColumns(String left, String right) {
        if (left == null) left = "";
        if (right == null) right = "";
        int spaces = LINE_WIDTH_80MM - left.length() - right.length();
        if (spaces < 1) {
            return left + "\n" + " ".repeat(Math.max(0, LINE_WIDTH_80MM - right.length())) + right + "\n";
        }
        return left + " ".repeat(spaces) + right + "\n";
    }

    private String formatItemHeader() {
        // Total width 48: Name(24), Qty(5), Price(9), Total(10)
        return String.format("%-24s %4s %8s %9s\n", "ITEM", "QTY", "RATE", "TOTAL");
    }

    private String formatItemRow(String name, Integer quantity, BigDecimal price, BigDecimal subtotal) {
        if (name == null) name = "Item";
        int qty = quantity != null ? quantity : 1;
        double p = price != null ? price.doubleValue() : 0.0;
        double t = subtotal != null ? subtotal.doubleValue() : (qty * p);

        String rateStr = String.format("%.2f", p);
        String totalStr = String.format("%.2f", t);

        if (name.length() <= 24) {
            return String.format("%-24s %4d %8s %9s\n", name, qty, rateStr, totalStr);
        } else {
            // First 24 chars on main line, remainder on subsequent lines
            String firstLine = name.substring(0, 24);
            String remainder = name.substring(24);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-24s %4d %8s %9s\n", firstLine, qty, rateStr, totalStr));
            while (!remainder.isEmpty()) {
                int take = Math.min(remainder.length(), 24);
                sb.append(remainder, 0, take).append("\n");
                remainder = remainder.substring(take);
            }
            return sb.toString();
        }
    }

    private String formatKotHeader() {
        return String.format("%-36s %11s\n", "ITEM DESCRIPTION", "QTY");
    }

    private String formatKotRow(String name, int qty) {
        if (name == null) name = "Item";
        return formatTwoColumns(name, String.valueOf(qty));
    }

    private byte[] formatKotRowBytes(String name, int qty, int width) throws IOException {
        return formatKotRowBadgeBytes(name, String.valueOf(qty), width);
    }

    private byte[] formatKotRowBadgeBytes(String name, String badgeText, int width) throws IOException {
        if (name == null) name = "Item";
        if (badgeText == null) badgeText = "1";
        ByteArrayOutputStream rowBuf = new ByteArrayOutputStream();
        String qtyBadge = String.format(" %s ", badgeText);
        int badgeLen = qtyBadge.length();
        int maxNameWidth = width - badgeLen;

        if (name.length() <= maxNameWidth) {
            String paddedName = String.format("%-" + maxNameWidth + "s", name);
            rowBuf.write(paddedName.getBytes(StandardCharsets.UTF_8));
            rowBuf.write(qtyBadge.getBytes(StandardCharsets.UTF_8));
            rowBuf.write("\n".getBytes(StandardCharsets.UTF_8));
        } else {
            String firstLine = name.substring(0, maxNameWidth);
            String remainder = name.substring(maxNameWidth);
            String paddedFirst = String.format("%-" + maxNameWidth + "s", firstLine);
            rowBuf.write(paddedFirst.getBytes(StandardCharsets.UTF_8));
            rowBuf.write(qtyBadge.getBytes(StandardCharsets.UTF_8));
            rowBuf.write("\n".getBytes(StandardCharsets.UTF_8));
            while (!remainder.isEmpty()) {
                int take = Math.min(remainder.length(), width);
                rowBuf.write((remainder.substring(0, take) + "\n").getBytes(StandardCharsets.UTF_8));
                remainder = remainder.substring(take);
            }
        }
        return rowBuf.toByteArray();
    }

    private String getUserName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse(null);
    }

    /** The branch's (or cafe's) uploaded logo as an ESC/POS image; null when the cafe has not uploaded one. */
    private byte[] getLogoRasterBytes(int paperWidthMm, int logoSize) {
        if (brandingService == null) {
            return null;
        }
        return brandingService.effectiveForContext(com.neram.pos.entity.BrandingAsset.Kind.LOGO)
                .map(img -> rasterOf("logo", img, paperWidthMm, logoSize, bytes -> {
                    BufferedImage cropped = autoCrop(ImageIO.read(new java.io.ByteArrayInputStream(bytes)));
                    int size80 = logoSize > 0 ? logoSize : 130;
                    return paperWidthMm == 58
                            ? buildEscPosRaster(cropped, Math.min(size80, 110), 384)
                            : buildEscPosRaster(cropped, size80, 576);
                }))
                .orElse(null);
    }

    /** The branch's (or cafe's) payment QR as an ESC/POS image; null when none was uploaded or it is unreadable. */
    private byte[] getPaymentQrRasterBytes(int paperWidthMm) {
        if (brandingService == null) {
            return null;
        }
        return brandingService.effectiveForContext(com.neram.pos.entity.BrandingAsset.Kind.PAYMENT_QR)
                .map(img -> rasterOf("qr", img, paperWidthMm, 0, bytes ->
                        PaymentQrRaster.build(ImageIO.read(new java.io.ByteArrayInputStream(bytes)), paperWidthMm == 58 ? 384 : 576)))
                .orElse(null);
    }

    private interface RasterBuilder {
        byte[] build(byte[] imageBytes) throws Exception;
    }

    private byte[] rasterOf(String what, BrandingService.Image img, int paperWidthMm, int size, RasterBuilder builder) {
        String key = what + "|" + com.neram.pos.tenancy.TenantContext.currentId().orElse(0L) + "|" + img.outletId() + "|"
                + img.updatedAt() + "|" + paperWidthMm + "|" + size;
        byte[] cached = imageRasterCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] raster = builder.build(img.data());
            if (raster != null) {
                imageRasterCache.put(key, raster);
            }
            return raster;
        } catch (Exception e) {
            log.warn("Could not prepare the {} image for printing: {}", what, e.getMessage());
            return null;
        }
    }

    private byte[] getLogoRasterBytes(int paperWidthMm) {
        return getLogoRasterBytes(paperWidthMm, 130);
    }

    /**
     * Crop away whitespace and transparent borders from brand logo image
     */
    private BufferedImage autoCrop(BufferedImage img) {
        if (img == null) return null;
        int w = img.getWidth();
        int h = img.getHeight();
        int minX = w, minY = h, maxX = 0, maxY = 0;
        boolean hasVisiblePixel = false;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                boolean isNearWhite = (r > 240 && g > 240 && b > 240);
                if (alpha > 20 && !isNearWhite) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    hasVisiblePixel = true;
                }
            }
        }

        if (!hasVisiblePixel || minX >= maxX || minY >= maxY) {
            return img;
        }

        int cropX = Math.max(0, minX - 2);
        int cropY = Math.max(0, minY - 2);
        int cropW = Math.min(w - cropX, (maxX - minX + 1) + 4);
        int cropH = Math.min(h - cropY, (maxY - minY + 1) + 4);

        return img.getSubimage(cropX, cropY, cropW, cropH);
    }

    private byte[] buildEscPosRaster(BufferedImage src, int logoSize, int paperDots) {
        int origW = src.getWidth();
        int origH = src.getHeight();
        int targetW = logoSize;
        int targetH = (int) Math.round((double) origH * targetW / origW);

        BufferedImage rgbImage = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetW, targetH);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();

        int totalBytesPerRow = paperDots / 8;
        int logoBytesPerRow = (targetW + 7) / 8;
        int leftPadBytes = Math.max(0, (totalBytesPerRow - logoBytesPerRow) / 2);
        int rightPadBytes = Math.max(0, totalBytesPerRow - logoBytesPerRow - leftPadBytes);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // GS v 0 m xL xH yL yH
        baos.write(0x1D);
        baos.write(0x76);
        baos.write(0x30);
        baos.write(0x00);
        baos.write(totalBytesPerRow % 256);
        baos.write(totalBytesPerRow / 256);
        baos.write(targetH % 256);
        baos.write(targetH / 256);

        int threshold = 200;

        for (int y = 0; y < targetH; y++) {
            for (int p = 0; p < leftPadBytes; p++) {
                baos.write(0x00);
            }

            int currentByte = 0;
            int bitCount = 0;
            for (int x = 0; x < targetW; x++) {
                int rgb = rgbImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gVal = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luminance = (int) (0.299 * r + 0.587 * gVal + 0.114 * b);

                int bit = (luminance < threshold) ? 1 : 0;
                currentByte = (currentByte << 1) | bit;
                bitCount++;

                if (bitCount == 8) {
                    baos.write(currentByte);
                    currentByte = 0;
                    bitCount = 0;
                }
            }
            if (bitCount > 0) {
                currentByte <<= (8 - bitCount);
                baos.write(currentByte);
            }

            for (int p = 0; p < rightPadBytes; p++) {
                baos.write(0x00);
            }
        }

        return baos.toByteArray();
    }

    private String formatWordWrapCentered(String text, int maxLineLen) {
        if (text == null || text.isBlank()) return "";
        if (text.length() <= maxLineLen) {
            return text + "\n";
        }
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();
        for (String w : words) {
            if (currentLine.length() + (currentLine.length() > 0 ? 1 : 0) + w.length() <= maxLineLen) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(w);
            } else {
                if (currentLine.length() > 0) {
                    result.append(currentLine).append("\n");
                    currentLine.setLength(0);
                }
                if (w.length() > maxLineLen) {
                    String remainder = w;
                    while (remainder.length() > maxLineLen) {
                        result.append(remainder, 0, maxLineLen).append("\n");
                        remainder = remainder.substring(maxLineLen);
                    }
                    currentLine.append(remainder);
                } else {
                    currentLine.append(w);
                }
            }
        }
        if (currentLine.length() > 0) {
            result.append(currentLine).append("\n");
        }
        return result.toString();
    }

    private void printItemSnapshots(ByteArrayOutputStream buffer, String modifiersSnapshot, String comboSnapshot) {
        if (modifiersSnapshot != null && !modifiersSnapshot.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(modifiersSnapshot);
                if (root.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : root) {
                        String name = node.has("name") ? node.get("name").asText() : "";
                        double price = node.has("priceAdjustment") ? node.get("priceAdjustment").asDouble() :
                                (node.has("price") ? node.get("price").asDouble() : 0.0);
                        if (!name.isBlank()) {
                            String priceStr = price > 0.001 ? String.format(" (+Rs. %.2f)", price) : "";
                            buffer.write(("  + " + name + priceStr + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (comboSnapshot != null && !comboSnapshot.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(comboSnapshot);
                if (root.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : root) {
                        String dishName = node.has("dishName") ? node.get("dishName").asText() :
                                (node.has("dish_name") ? node.get("dish_name").asText() : "");
                        double extra = node.has("extraPrice") ? node.get("extraPrice").asDouble() :
                                (node.has("extra_price") ? node.get("extra_price").asDouble() : 0.0);
                        if (!dishName.isBlank()) {
                            String extraStr = extra > 0.001 ? String.format(" (+Rs. %.2f)", extra) : "";
                            buffer.write(("  + " + dishName + extraStr + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
