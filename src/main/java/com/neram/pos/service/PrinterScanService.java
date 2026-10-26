package com.neram.pos.service;

import com.neram.pos.dto.PrinterScanDTO;
import com.neram.pos.dto.PrinterScanReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * "Scan for printers": the POS asks, the print bridge (which sits on the shop network) looks for devices listening on the
 * thermal-printer port and reports the addresses. The bridge calls the cloud, never the other way round, so a scan is a small
 * request that waits here until the bridge's next poll picks it up. One scan at a time; asking again while one is open returns it.
 */
@Slf4j
@Service
public class PrinterScanService {

    public static final int PORT_MIN = 9100;
    public static final int PORT_MAX = 9109;
    /** the bridge polls every second or so; if nobody takes the request in this time the bridge is offline or too old */
    static final long PENDING_TIMEOUT_MS = 45_000L;
    /** a /24 scan takes seconds; this only stops a stuck scan from blocking the next one */
    static final long RUNNING_TIMEOUT_MS = 150_000L;
    static final int MAX_RESULTS = 40;

    private static final Pattern PRIVATE_IPV4 = Pattern.compile(
            "^(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})$");

    private static final class Scan {
        String id;
        String status = "PENDING";
        String message;
        int port;
        String subnet;
        int scanned;
        int total;
        long createdAt;
        long updatedAt;
        String bridgeId;
        List<PrinterScanDTO.Found> printers = new ArrayList<>();
    }

    private final LongSupplier clock;
    private Scan current;

    public PrinterScanService() {
        this(System::currentTimeMillis);
    }

    PrinterScanService(LongSupplier clock) {
        this.clock = clock;
    }

    /** Starts a scan, or returns the one that is already open. */
    public synchronized PrinterScanDTO start(int port) {
        if (port < PORT_MIN || port > PORT_MAX) {
            throw new IllegalArgumentException("Printer port must be between " + PORT_MIN + " and " + PORT_MAX + ".");
        }
        expire();
        if (current != null && isOpen(current)) {
            return view(current);
        }
        Scan s = new Scan();
        s.id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        s.port = port;
        s.createdAt = s.updatedAt = clock.getAsLong();
        s.message = "Waiting for the print bridge...";
        current = s;
        log.info("Printer scan {} requested (port {})", s.id, port);
        return view(s);
    }

    public synchronized Optional<PrinterScanDTO> get(String id) {
        expire();
        return current != null && current.id.equals(id) ? Optional.of(view(current)) : Optional.empty();
    }

    public synchronized Optional<PrinterScanDTO> latest() {
        expire();
        return Optional.ofNullable(current).map(this::view);
    }

    /** The bridge's poll asks for work: hands out the waiting scan once. */
    public synchronized Optional<PrinterScanDTO> claimPending(String bridgeId) {
        expire();
        if (current == null || !"PENDING".equals(current.status)) return Optional.empty();
        current.status = "RUNNING";
        current.bridgeId = bridgeId;
        current.updatedAt = clock.getAsLong();
        current.message = "Scanning the network...";
        log.info("Printer scan {} taken by bridge {}", current.id, bridgeId);
        return Optional.of(view(current));
    }

    /** Progress and final results from the bridge. */
    public synchronized boolean report(String id, PrinterScanReportDTO r) {
        expire();
        if (current == null || !current.id.equals(id) || r == null) return false;
        if (!"RUNNING".equals(current.status) && !"PENDING".equals(current.status)) return false;   // already finished or expired
        if (r.getBridgeId() != null && current.bridgeId != null && !current.bridgeId.equals(r.getBridgeId())) return false;

        current.updatedAt = clock.getAsLong();
        if (r.getSubnet() != null && r.getSubnet().length() <= 40) current.subnet = r.getSubnet();
        if (r.getTotal() != null) current.total = Math.max(0, Math.min(r.getTotal(), 65_536));
        if (r.getScanned() != null) current.scanned = Math.max(0, Math.min(r.getScanned(), Math.max(current.total, r.getScanned())));
        if (r.getPrinters() != null) {
            List<PrinterScanDTO.Found> clean = new ArrayList<>();
            for (PrinterScanDTO.Found f : r.getPrinters()) {
                if (f == null || f.getIp() == null || !PRIVATE_IPV4.matcher(f.getIp().trim()).matches()) continue;   // only shop-network addresses
                int p = f.getPort() != null ? f.getPort() : current.port;
                if (p < PORT_MIN || p > PORT_MAX) continue;
                clean.add(PrinterScanDTO.Found.builder().ip(f.getIp().trim()).port(p)
                        .latencyMs(f.getLatencyMs() != null ? Math.max(0, f.getLatencyMs()) : null)
                        .busy(Boolean.TRUE.equals(f.getBusy())).build());
                if (clean.size() >= MAX_RESULTS) break;
            }
            clean.sort((a, b) -> compareIp(a.getIp(), b.getIp()));
            current.printers = clean;
        }
        if (r.getError() != null && !r.getError().isBlank()) {
            current.status = "FAILED";
            current.message = r.getError().length() > 200 ? r.getError().substring(0, 200) : r.getError();
        } else if (Boolean.TRUE.equals(r.getDone())) {
            current.status = "DONE";
            current.scanned = Math.max(current.scanned, current.total);
            current.message = current.printers.isEmpty()
                    ? "No printer found on port " + current.port + ". Check the printer is on, on the same Wi-Fi, and its raw port is " + current.port + "."
                    : current.printers.size() + (current.printers.size() == 1 ? " printer" : " printers") + " found.";
        } else {
            current.message = "Scanning the network... " + current.scanned + " of " + current.total;
        }
        return true;
    }

    private boolean isOpen(Scan s) {
        return "PENDING".equals(s.status) || "RUNNING".equals(s.status);
    }

    private void expire() {
        if (current == null) return;
        long now = clock.getAsLong();
        if ("PENDING".equals(current.status) && now - current.createdAt > PENDING_TIMEOUT_MS) {
            current.status = "TIMED_OUT";
            current.message = "The print bridge did not answer. Check it is powered and on Wi-Fi, and that its firmware supports network scan.";
        } else if ("RUNNING".equals(current.status) && now - current.updatedAt > RUNNING_TIMEOUT_MS) {
            current.status = "TIMED_OUT";
            current.message = "The scan stopped answering. Try again.";
        }
    }

    private PrinterScanDTO view(Scan s) {
        return PrinterScanDTO.builder().id(s.id).status(s.status).message(s.message).port(s.port).subnet(s.subnet)
                .scanned(s.scanned).total(s.total).bridgeId(s.bridgeId)
                .elapsedSeconds((clock.getAsLong() - s.createdAt) / 1000)
                .printers(new ArrayList<>(s.printers)).build();
    }

    static int compareIp(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < 4; i++) {
            int c = Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i]));
            if (c != 0) return c;
        }
        return 0;
    }
}
