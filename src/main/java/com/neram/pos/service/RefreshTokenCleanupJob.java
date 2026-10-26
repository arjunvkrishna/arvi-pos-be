package com.neram.pos.service;

import com.neram.pos.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly refresh-token and login-attempt cleanup, run once inside each cafe's own database. */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final TenantExecutor tenantExecutor;

    @Scheduled(cron = "0 15 3 * * *")
    public void purgeStaleTokens() {
        tenantExecutor.forEachActiveTenant("purge-refresh-tokens", refreshTokenService::purgeStale);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeStaleLoginAttempts() {
        tenantExecutor.forEachActiveTenant("purge-login-attempts", loginAttemptService::cleanup);
    }
}
