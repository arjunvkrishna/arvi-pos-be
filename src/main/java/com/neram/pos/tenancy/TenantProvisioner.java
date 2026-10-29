package com.neram.pos.tenancy;

import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Creates a new cafe end to end while the application keeps running: registry row (with encrypted credentials),
 * PostgreSQL role + database, schema migration, first admin. No env file edit or restart is involved; the routing
 * datasource opens the new database from the registry on the first request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioner {

    private static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$");
    private static final Pattern ADMIN_USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    private static final String PWD_FIELD = "db-password";
    private static final String BRIDGE_FIELD = "bridge-token";

    private final TenantRegistry registry;
    private final TenantCrypto crypto;
    private final TenantDatabaseAdmin dbAdmin;
    private final TenantMigrator migrator;
    private final TenantBootstrapper bootstrapper;
    private final TenantRoutingDataSource routing;
    private final SecureRandom random = new SecureRandom();

    public TenantRecord provision(CreateCafeRequest req, String actor) {
        validate(req);
        com.neram.pos.plan.Plan plan = req.getSubscriptionPlan() == null || req.getSubscriptionPlan().isBlank()
                ? com.neram.pos.plan.Plan.PRO : com.neram.pos.plan.Plan.parse(req.getSubscriptionPlan());
        String code = chooseCode(req);

        String dbName = TenantDatabaseAdmin.databaseNameFor(code, null);
        try {
            if (dbAdmin.databaseExists(dbName) || dbAdmin.roleExists(TenantDatabaseAdmin.roleNameFor(dbName))) {
                dbName = TenantDatabaseAdmin.databaseNameFor(code, HexFormat.of().formatHex(randomBytes(2)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot reach the database server to provision a cafe: " + e.getMessage(), e);
        }
        String role = TenantDatabaseAdmin.roleNameFor(dbName);
        String dbPassword = dbAdmin.generatePassword();
        String bridgeToken = dbAdmin.generateToken();

        // Credentials are stored (encrypted) before anything is created, so they can never be lost if we crash midway.
        TenantRecord record = registry.insert(new TenantRegistry.NewTenant(
                code, req.getName().trim(),
                plan.name(),
                req.getMaxOutlets() != null && req.getMaxOutlets() > 0 ? req.getMaxOutlets() : plan.defaultBranches(),
                req.getContactEmail(), req.getContactPhone(), req.getAddress(),
                dbAdmin.targetHost(), dbAdmin.targetPort(), dbName, role,
                crypto.encrypt(dbPassword, TenantCrypto.purpose(code, PWD_FIELD)),
                crypto.encrypt(bridgeToken, TenantCrypto.purpose(code, BRIDGE_FIELD))));
        registry.audit(record.id(), "PROVISION_STARTED", actor, "db=" + dbName);

        try {
            dbAdmin.createRoleAndDatabase(dbName, role, dbPassword);
            migrator.migrate(record);
            TenantContext.runAs(record.info(), () -> bootstrapper.bootstrap(record, req));
            registry.updateStatus(record.id(), TenantRecord.ACTIVE, null);
            registry.audit(record.id(), "PROVISIONED", actor, "db=" + dbName);
            log.info("Provisioned cafe '{}' (id={}) in database '{}'", code, record.id(), dbName);
            return registry.findById(record.id()).orElseThrow();
        } catch (Exception e) {
            log.error("Provisioning cafe '{}' failed: {}", code, e.getMessage(), e);
            routing.evict(record.id());
            registry.audit(record.id(), "PROVISION_FAILED", actor, e.getMessage());
            rollback(record, dbName, role, e);
            throw new IllegalStateException("Could not create cafe '" + code + "': " + e.getMessage(), e);
        }
    }

    private void rollback(TenantRecord record, String dbName, String role, Exception cause) {
        dbAdmin.dropDatabaseAndRole(dbName, role);
        try {
            if (dbAdmin.databaseExists(dbName)) {
                // Cleanup failed: keep the registry row (with its credentials) so an operator can finish the job.
                registry.updateStatus(record.id(), TenantRecord.FAILED, cause.getMessage());
                return;
            }
        } catch (Exception e) {
            registry.updateStatus(record.id(), TenantRecord.FAILED, cause.getMessage());
            return;
        }
        registry.delete(record.id());
    }

    private void validate(CreateCafeRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Cafe name is required");
        }
        if (req.getAdminUsername() == null || !ADMIN_USERNAME.matcher(req.getAdminUsername().trim()).matches()) {
            throw new IllegalArgumentException("Admin username must be 3-50 characters (letters, digits, . _ -)");
        }
        PasswordValidator.validate(req.getAdminPassword());
    }

    private String chooseCode(CreateCafeRequest req) {
        boolean explicit = req.getCode() != null && !req.getCode().trim().isEmpty();
        String code = normalise(explicit ? req.getCode() : req.getName());
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Cafe code must be 3-30 characters: lowercase letters, digits and hyphens");
        }
        if (registry.findByCode(code).isEmpty()) {
            return code;
        }
        if (explicit) {
            throw new IllegalArgumentException("A cafe with code '" + code + "' already exists");
        }
        for (int i = 0; i < 20; i++) {
            String candidate = trimTo(code, 25) + "-" + (100 + random.nextInt(900));
            if (registry.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Could not derive a free cafe code from the name; provide one");
    }

    static String normalise(String raw) {
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
        s = s.replaceAll("^-+|-+$", "");
        return trimTo(s, 30).replaceAll("-+$", "");
    }

    private static String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }
}
