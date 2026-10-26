package com.neram.pos.service;

import com.neram.pos.dto.PrinterScanDTO;
import com.neram.pos.dto.PrinterScanReportDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The "scan for printers" request: one at a time, handed to the bridge once, results limited to shop-network addresses. */
class PrinterScanServiceTest {

    private AtomicLong now;
    private PrinterScanService service;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000_000L);
        service = new PrinterScanService(now::get);
    }

    private static PrinterScanDTO.Found found(String ip, long ms) {
        return PrinterScanDTO.Found.builder().ip(ip).port(9100).latencyMs(ms).build();
    }

    private static PrinterScanReportDTO report(String bridge, boolean done, PrinterScanDTO.Found... printers) {
        return new PrinterScanReportDTO(bridge, "192.168.1.0/24", done ? 254 : 100, 254, done, null, List.of(printers));
    }

    @Test
    void asking_twice_returns_the_same_open_scan() {
        PrinterScanDTO a = service.start(9100);
        PrinterScanDTO b = service.start(9100);
        assertEquals(a.getId(), b.getId());
        assertEquals("PENDING", a.getStatus());
    }

    @Test
    void only_ports_in_the_printer_range_are_allowed() {
        assertThrows(IllegalArgumentException.class, () -> service.start(80));
        assertThrows(IllegalArgumentException.class, () -> service.start(9110));
        assertDoesNotThrow(() -> service.start(9105));
    }

    @Test
    void the_bridge_takes_the_scan_once_and_reports_results_sorted_by_address() {
        PrinterScanDTO scan = service.start(9100);
        assertTrue(service.claimPending("B1").isPresent());
        assertTrue(service.claimPending("B1").isEmpty(), "a second poll gets nothing");
        assertEquals("RUNNING", service.get(scan.getId()).orElseThrow().getStatus());

        assertTrue(service.report(scan.getId(), report("B1", false, found("192.168.1.47", 12))));
        PrinterScanDTO midway = service.get(scan.getId()).orElseThrow();
        assertEquals("RUNNING", midway.getStatus());
        assertEquals(1, midway.getPrinters().size(), "found printers show while the scan is still going");

        assertTrue(service.report(scan.getId(), report("B1", true, found("192.168.1.47", 12), found("192.168.1.9", 7), found("10.0.0.5", 20))));
        PrinterScanDTO done = service.get(scan.getId()).orElseThrow();
        assertEquals("DONE", done.getStatus());
        assertEquals(List.of("10.0.0.5", "192.168.1.9", "192.168.1.47"), done.getPrinters().stream().map(PrinterScanDTO.Found::getIp).toList());
        assertEquals(254, done.getScanned());
        assertTrue(done.getMessage().contains("3 printers"));
    }

    @Test
    void public_or_malformed_addresses_and_foreign_ports_are_dropped() {
        PrinterScanDTO scan = service.start(9100);
        service.claimPending("B1");
        service.report(scan.getId(), report("B1", true, found("8.8.8.8", 5), found("not-an-ip", 5), found("192.168.1.20", 5),
                PrinterScanDTO.Found.builder().ip("192.168.1.21").port(80).build()));
        assertEquals(List.of("192.168.1.20"), service.get(scan.getId()).orElseThrow().getPrinters().stream().map(PrinterScanDTO.Found::getIp).toList());
    }

    @Test
    void a_scan_that_finds_nothing_says_what_to_check() {
        PrinterScanDTO scan = service.start(9100);
        service.claimPending("B1");
        service.report(scan.getId(), report("B1", true));
        PrinterScanDTO done = service.get(scan.getId()).orElseThrow();
        assertEquals("DONE", done.getStatus());
        assertTrue(done.getMessage().startsWith("No printer found"));
    }

    @Test
    void another_bridge_cannot_answer_and_a_wrong_id_is_ignored() {
        PrinterScanDTO scan = service.start(9100);
        service.claimPending("B1");
        assertFalse(service.report(scan.getId(), report("OTHER", true, found("192.168.1.9", 1))));
        assertFalse(service.report("nope", report("B1", true)));
        assertEquals("RUNNING", service.get(scan.getId()).orElseThrow().getStatus());
    }

    @Test
    void a_bridge_error_fails_the_scan() {
        PrinterScanDTO scan = service.start(9100);
        service.claimPending("B1");
        service.report(scan.getId(), new PrinterScanReportDTO("B1", null, 0, 0, true, "Wi-Fi is not connected", null));
        PrinterScanDTO failed = service.get(scan.getId()).orElseThrow();
        assertEquals("FAILED", failed.getStatus());
        assertEquals("Wi-Fi is not connected", failed.getMessage());
        assertNotEquals(scan.getId(), service.start(9100).getId(), "a finished scan does not block the next one");
    }

    @Test
    void nobody_picking_it_up_times_out_and_a_stuck_scan_is_released() {
        PrinterScanDTO scan = service.start(9100);
        now.addAndGet(PrinterScanService.PENDING_TIMEOUT_MS + 1);
        assertEquals("TIMED_OUT", service.get(scan.getId()).orElseThrow().getStatus());

        PrinterScanDTO second = service.start(9100);
        service.claimPending("B1");
        now.addAndGet(PrinterScanService.RUNNING_TIMEOUT_MS + 1);
        assertEquals("TIMED_OUT", service.get(second.getId()).orElseThrow().getStatus());
        assertFalse(service.report(second.getId(), report("B1", true)), "a late report after the timeout is refused");
    }
}
