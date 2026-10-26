package com.neram.pos.service;

import com.neram.pos.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Printer/bridge alerting, evaluated inside each cafe whose bridge has connected since startup. */
@Component
@RequiredArgsConstructor
public class PrinterHealthJob {

    private final PrinterHealthMonitor monitor;
    private final PrintBridgeService bridge;
    private final TenantExecutor tenantExecutor;

    @Scheduled(fixedDelayString = "${neram.alerts.printer.check-interval-ms:30000}",
            initialDelayString = "${neram.alerts.printer.initial-delay-ms:60000}")
    public void check() {
        tenantExecutor.runFor(bridge.tenantsWithBridgeHeartbeat(), "printer-health", monitor::scheduledCheck);
    }
}
