package com.neram.pos.service;

import com.neram.pos.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Print queue housekeeping, run inside each cafe's own database. */
@Component
@RequiredArgsConstructor
public class PrintBridgeScheduler {

    private final PrintBridgeService printBridgeService;
    private final TenantExecutor tenantExecutor;

    /** Only cafes whose bridge was recently active can have a claim lease to expire, so idle cafes are not touched. */
    @Scheduled(fixedDelay = 15000)
    public void sweepExpiredLeases() {
        tenantExecutor.runFor(printBridgeService.recentlyActiveTenants(), "print-lease-sweep",
                printBridgeService::sweepExpiredLeases);
    }

    @Scheduled(cron = "0 0 * * * *") // hourly
    public void purgeOldPayloads() {
        tenantExecutor.forEachActiveTenant("print-payload-purge", printBridgeService::purgeOldPayloads);
    }
}
