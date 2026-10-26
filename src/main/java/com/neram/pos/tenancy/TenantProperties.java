package com.neram.pos.tenancy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings under {@code neram.tenancy}. The control database holds the cafe registry; every cafe's own data lives
 * in a separate database on the same PostgreSQL server (or another one, via the per-cafe host/port in the registry).
 */
@Data
@Component
@ConfigurationProperties(prefix = "neram.tenancy")
public class TenantProperties {

    /** Base64 of 32 random bytes; encrypts the per-cafe database passwords stored in the registry. */
    private String secretKey;
    /** Optional previous key, accepted for decryption only, so the key can be rotated without downtime. */
    private String previousSecretKey;

    private Control control = new Control();
    private Admin admin = new Admin();
    private Provision provision = new Provision();
    private Pool pool = new Pool();
    private Migrate migrate = new Migrate();

    @Data
    public static class Control {
        private String host = "localhost";
        private int port = 5432;
        private String name = "arvi_control";
        private String user;
        private String password;
        private int poolSize = 8;
    }

    /** Role used to CREATE DATABASE / CREATE ROLE. Blank means "same as the control user". */
    @Data
    public static class Admin {
        private String user;
        private String password;
    }

    @Data
    public static class Provision {
        /** Where new cafe databases are created. Blank means the control database's host/port. */
        private String dbHost;
        private Integer dbPort;
        /** Max simultaneous connections the cafe's own role may hold (protects other cafes from a noisy one). */
        private int connectionLimit = 30;
        /** Load db/seed/demo_business_data.sql into new cafes. */
        private boolean seedDemoData = false;
    }

    @Data
    public static class Pool {
        private int maxSize = 6;
        private long idleTimeoutMs = 120_000;
        /** A cafe pool unused for this long is closed (reopened on the next request). */
        private long evictAfterIdleMs = 600_000;
        /** Soft cap on simultaneously open cafe pools; the least recently used idle one is closed beyond it. */
        private int maxPools = 100;
    }

    @Data
    public static class Migrate {
        private boolean onStartup = true;
        private int parallelism = 4;
    }
}
