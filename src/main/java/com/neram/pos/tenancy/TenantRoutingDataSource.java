package com.neram.pos.tenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gives every cafe its own connection pool to its own database. Pools are created on first use from the registry
 * (so a new cafe works immediately, without touching env files or restarting), closed again after a quiet period,
 * and rebuilt if the cafe's credentials are rotated. With no cafe selected it refuses to connect.
 */
@Slf4j
public class TenantRoutingDataSource extends AbstractDataSource implements DisposableBean {

    private static final String PWD_FIELD = "db-password";

    private static final class Pool {
        final HikariDataSource ds;
        final String credentialFingerprint;
        volatile long lastUsed = System.currentTimeMillis();

        Pool(HikariDataSource ds, String credentialFingerprint) {
            this.ds = ds;
            this.credentialFingerprint = credentialFingerprint;
        }
    }

    private final TenantRegistry registry;
    private final TenantCrypto crypto;
    private final TenantProperties props;
    private final Map<Long, Pool> pools = new ConcurrentHashMap<>();
    private final ScheduledExecutorService janitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tenant-pool-janitor");
        t.setDaemon(true);
        return t;
    });

    public TenantRoutingDataSource(TenantRegistry registry, TenantCrypto crypto, TenantProperties props) {
        this.registry = registry;
        this.crypto = crypto;
        this.props = props;
        janitor.scheduleWithFixedDelay(this::evictIdlePools, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public Connection getConnection() throws SQLException {
        TenantInfo tenant = TenantContext.require();
        try {
            return poolFor(tenant.id()).getConnection();
        } catch (SQLException e) {
            if (isAuthFailure(e)) {
                // Credentials were rotated (maybe by another replica): drop the stale pool and retry once.
                log.warn("Authentication failed for cafe '{}'; reloading its credentials", tenant.code());
                registry.invalidate();
                evict(tenant.id());
                return poolFor(tenant.id()).getConnection();
            }
            throw e;
        }
    }

    @Override
    public Connection getConnection(String username, String password) {
        throw new UnsupportedOperationException("Per-call credentials are not supported; the cafe's own role is used");
    }

    /** Closes the cafe's pool; the next request rebuilds it from the registry. */
    public void evict(long tenantId) {
        Pool p = pools.remove(tenantId);
        if (p != null) {
            p.ds.close();
        }
    }

    public int openPoolCount() {
        return pools.size();
    }

    private HikariDataSource poolFor(long tenantId) {
        Pool existing = pools.get(tenantId);
        TenantRecord rec = registry.findById(tenantId)
                .orElseThrow(() -> new TenantUnavailableException(404, "Unknown cafe"));
        String fingerprint = rec.dbHost() + rec.dbPort() + rec.dbName() + rec.dbUser() + rec.dbPasswordEnc();
        if (existing != null && existing.credentialFingerprint.equals(fingerprint)) {
            existing.lastUsed = System.currentTimeMillis();
            return existing.ds;
        }
        if (existing != null) {
            evict(tenantId);
        }
        Pool created = pools.computeIfAbsent(tenantId, id -> {
            makeRoomForNewPool();
            return new Pool(build(rec), fingerprint);
        });
        created.lastUsed = System.currentTimeMillis();
        return created.ds;
    }

    private HikariDataSource build(TenantRecord rec) {
        TenantProperties.Pool p = props.getPool();
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("tenant-" + rec.code());
        cfg.setJdbcUrl(rec.jdbcUrl());
        cfg.setUsername(rec.dbUser());
        cfg.setPassword(crypto.decrypt(rec.dbPasswordEnc(), TenantCrypto.purpose(rec.code(), PWD_FIELD)));
        cfg.setMaximumPoolSize(p.getMaxSize());
        cfg.setMinimumIdle(0);
        cfg.setIdleTimeout(Math.max(10_000L, p.getIdleTimeoutMs()));
        cfg.setMaxLifetime(1_800_000);
        cfg.setConnectionTimeout(30_000);
        cfg.setLeakDetectionThreshold(30_000);
        cfg.setInitializationFailTimeout(-1);
        cfg.addDataSourceProperty("ApplicationName", "arvi:" + rec.code());
        log.info("Opening connection pool for cafe '{}' -> {}", rec.code(), rec.dbName());
        return new HikariDataSource(cfg);
    }

    private void makeRoomForNewPool() {
        if (pools.size() < props.getPool().getMaxPools()) {
            return;
        }
        pools.entrySet().stream()
                .filter(e -> e.getValue().ds.getHikariPoolMXBean() == null
                        || e.getValue().ds.getHikariPoolMXBean().getActiveConnections() == 0)
                .min(Comparator.comparingLong(e -> e.getValue().lastUsed))
                .ifPresentOrElse(e -> {
                    log.info("Pool cap reached; closing least recently used pool (cafe id {})", e.getKey());
                    evict(e.getKey());
                }, () -> log.warn("Pool cap {} exceeded and every pool is busy", props.getPool().getMaxPools()));
    }

    private void evictIdlePools() {
        try {
            long cutoff = System.currentTimeMillis() - props.getPool().getEvictAfterIdleMs();
            pools.forEach((id, p) -> {
                boolean busy = p.ds.getHikariPoolMXBean() != null
                        && p.ds.getHikariPoolMXBean().getActiveConnections() > 0;
                if (p.lastUsed < cutoff && !busy) {
                    log.info("Closing idle connection pool for cafe id {}", id);
                    evict(id);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Pool janitor failed: {}", e.getMessage());
        }
    }

    private static boolean isAuthFailure(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && s.getSQLState() != null
                    && (s.getSQLState().equals("28P01") || s.getSQLState().equals("28000"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        janitor.shutdownNow();
        pools.keySet().forEach(this::evict);
    }
}
