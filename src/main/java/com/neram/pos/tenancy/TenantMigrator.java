package com.neram.pos.tenancy;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Applies the versioned schema (db/migration) to a cafe's own database, and on startup to every active cafe.
 * One cafe failing to migrate never blocks the others or the application: it is marked in the registry and its
 * requests are answered with 503 until fixed.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantMigrator implements ApplicationRunner {

    private static final String PWD_FIELD = "db-password";

    private final TenantRegistry registry;
    private final TenantCrypto crypto;
    private final TenantProperties props;

    public TenantMigrator(TenantRegistry registry, TenantCrypto crypto, TenantProperties props) {
        this.registry = registry;
        this.crypto = crypto;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.getMigrate().isOnStartup()) {
            log.info("Tenant migration on startup is disabled");
            return;
        }
        migrateAllActive();
    }

    public void migrateAllActive() {
        List<TenantRecord> tenants = registry.findAll().stream()
                .filter(t -> t.isActive() || TenantRecord.SUSPENDED.equals(t.status()))
                .toList();
        if (tenants.isEmpty()) {
            return;
        }
        int threads = Math.max(1, Math.min(props.getMigrate().getParallelism(), tenants.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (TenantRecord t : tenants) {
                futures.add(pool.submit(() -> migrateQuietly(t)));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.error("Tenant migration task failed: {}", e.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
        log.info("Checked schema of {} cafe database(s)", tenants.size());
    }

    private void migrateQuietly(TenantRecord t) {
        try {
            migrate(t);
        } catch (Exception e) {
            log.error("Schema migration failed for cafe '{}': {}", t.code(), e.getMessage());
            registry.markMigrated(t.id(), null, "migration failed: " + e.getMessage());
        }
    }

    /** Runs Flyway against the cafe's database and records the resulting schema version in the registry. */
    public String migrate(TenantRecord t) {
        String password = crypto.decrypt(t.dbPasswordEnc(), TenantCrypto.purpose(t.code(), PWD_FIELD));
        Flyway flyway = Flyway.configure()
                .dataSource(t.jdbcUrl(), t.dbUser(), password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .outOfOrder(true)
                .lockRetryCount(60)
                .load();
        try {
            flyway.repair();
        } catch (Exception e) {
            log.warn("Flyway repair for cafe '{}' reported: {}", t.code(), e.getMessage());
        }
        MigrateResult result = flyway.migrate();
        // Tables added by new migrations get this cafe's id as the default for tenant_id, like the original ones.
        org.springframework.jdbc.core.JdbcTemplate direct = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(t.jdbcUrl(), t.dbUser(), password));
        TenantBootstrapper.setTenantIdDefaults(direct, t.id());
        MigrationInfo current = flyway.info().current();
        String version = current == null ? "none" : String.valueOf(current.getVersion());
        if (result.migrationsExecuted > 0) {
            log.info("Cafe '{}' migrated to schema version {} ({} migration(s) applied)",
                    t.code(), version, result.migrationsExecuted);
        }
        registry.markMigrated(t.id(), version, null);
        return version;
    }
}
