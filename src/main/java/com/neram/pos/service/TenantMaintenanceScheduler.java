package com.neram.pos.service;

import com.neram.pos.config.JobMode;
import com.neram.pos.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily housekeeping, run once for every active cafe inside that cafe's own database. These are kept to one run a day
 * on purpose: with many cafes, every extra run per day is one more query on every cafe's database.
 */
@Component
@RequiredArgsConstructor
public class TenantMaintenanceScheduler {

    private final TenantExecutor tenantExecutor;
    private final LoyaltyExpiryJob loyaltyExpiry;
    private final NotificationService notifications;

    /** Just after the 04:00 change of business day. Redeeming and earning also check expiry, so nothing waits on this. */
    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Kolkata")
    public void expireLoyaltyPoints() {
        if (!JobMode.interactive()) return;
        tenantExecutor.forEachActiveTenant("loyalty-expiry", loyaltyExpiry::expireLapsedPoints);
    }

    @Scheduled(cron = "0 40 3 * * *")
    public void removeOldNotifications() {
        if (!JobMode.interactive()) return;
        tenantExecutor.forEachActiveTenant("notification-cleanup", notifications::removeOld);
    }
}
