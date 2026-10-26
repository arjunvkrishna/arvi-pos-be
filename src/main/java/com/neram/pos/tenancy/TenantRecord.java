package com.neram.pos.tenancy;

import java.time.OffsetDateTime;

/** One row of the control database's cafe registry. The DB and bridge secrets are still encrypted here. */
public record TenantRecord(
        long id,
        String code,
        String name,
        String status,
        String subscriptionPlan,
        int maxOutlets,
        String contactEmail,
        String contactPhone,
        String address,
        String dbHost,
        int dbPort,
        String dbName,
        String dbUser,
        String dbPasswordEnc,
        String bridgeTokenEnc,
        String schemaVersion,
        OffsetDateTime migratedAt,
        String lastError,
        OffsetDateTime createdAt) {

    public static final String PROVISIONING = "PROVISIONING";
    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String FAILED = "FAILED";

    public TenantInfo info() {
        return new TenantInfo(id, code);
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    @Override
    public String toString() {
        return "TenantRecord[id=" + id + ", code=" + code + ", status=" + status + ", db=" + dbName + "]";
    }
}
