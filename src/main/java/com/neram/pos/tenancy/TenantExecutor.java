package com.neram.pos.tenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Runs background work once per cafe, each inside that cafe's context; one cafe failing never stops the rest. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExecutor {

    private final TenantRegistry registry;

    public void forEachActiveTenant(String jobName, Runnable job) {
        runFor(registry.findActive().stream().map(TenantRecord::info).toList(), jobName, job);
    }

    public void runFor(Collection<TenantInfo> tenants, String jobName, Runnable job) {
        for (TenantInfo tenant : tenants) {
            try {
                TenantContext.runAs(tenant, job);
            } catch (Exception e) {
                log.warn("Job '{}' failed for cafe '{}': {}", jobName, tenant.code(), e.getMessage());
            }
        }
    }
}
