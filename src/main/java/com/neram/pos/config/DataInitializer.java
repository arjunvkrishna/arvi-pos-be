package com.neram.pos.config;

import com.neram.pos.tenancy.PlatformAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup bootstrap for the platform: makes sure a SaaS owner account exists in the control database.
 * Cafe-level setup (first admin, outlet, demo data) happens when a cafe is created, see TenantBootstrapper.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PlatformAdminService platformAdminService;

    @Override
    public void run(String... args) {
        platformAdminService.bootstrapIfMissing();
    }
}
