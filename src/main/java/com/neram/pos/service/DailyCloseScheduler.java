package com.neram.pos.service;

import com.neram.pos.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyCloseScheduler {
    private final DailyCloseService dailyClose;
    private final TenantExecutor tenantExecutor;

    @Scheduled(cron = "30 * * * * *")
    public void tick() {
        tenantExecutor.forEachActiveTenant("daily-close", dailyClose::sendIfDue);
    }
}
