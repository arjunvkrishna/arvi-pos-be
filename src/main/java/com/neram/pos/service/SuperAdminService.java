package com.neram.pos.service;

import com.neram.pos.dto.CafeSummaryDto;
import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.repository.OutletRepository;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantCrypto;
import com.neram.pos.tenancy.TenantDatabaseAdmin;
import com.neram.pos.tenancy.TenantMigrator;
import com.neram.pos.tenancy.TenantProvisioner;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import com.neram.pos.tenancy.TenantRoutingDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Platform (SaaS owner) operations on cafes. Works on the control-database registry; a cafe's own database is only
 * touched for read-only statistics and to mirror profile changes, always inside that cafe's context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private static final String PWD_FIELD = "db-password";
    private static final String BRIDGE_FIELD = "bridge-token";
    private static final Set<String> EDITABLE_STATUSES = Set.of(TenantRecord.ACTIVE, TenantRecord.SUSPENDED);

    private final TenantRegistry registry;
    private final TenantProvisioner provisioner;
    private final TenantMigrator migrator;
    private final TenantCrypto crypto;
    private final TenantDatabaseAdmin dbAdmin;
    private final TenantRoutingDataSource routing;
    private final OutletRepository outletRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<CafeSummaryDto> getAllCafes() {
        return registry.findAll().stream().map(this::summarise).toList();
    }

    public CafeSummaryDto createCafe(CreateCafeRequest req, String actor) {
        TenantRecord created = provisioner.provision(req, actor);
        return summarise(created);
    }

    public CafeSummaryDto getCafe(Long id) {
        return summarise(require(id));
    }

    /** Updates plan/limits/status/contact in the registry and mirrors them into the cafe's own tenants row. */
    public CafeSummaryDto updateCafe(Long id, Map<String, Object> updates, String actor) {
        TenantRecord t = require(id);
        String newStatus = t.status();
        if (updates.containsKey("status")) {
            String requested = String.valueOf(updates.get("status")).toUpperCase();
            if (!EDITABLE_STATUSES.contains(requested)) {
                throw new IllegalArgumentException("Status must be ACTIVE or SUSPENDED");
            }
            if (!t.isActive() && !TenantRecord.SUSPENDED.equals(t.status())) {
                throw new IllegalArgumentException("Cafe is " + t.status() + " and cannot be changed this way");
            }
            newStatus = requested;
        }
        final String status = newStatus;
        String name = text(updates, "name", t.name());
        String email = text(updates, "contactEmail", t.contactEmail());
        String phone = text(updates, "contactPhone", t.contactPhone());
        String address = text(updates, "address", t.address());
        String plan = text(updates, "subscriptionPlan", t.subscriptionPlan());
        int maxOutlets = updates.containsKey("maxOutlets")
                ? Integer.parseInt(String.valueOf(updates.get("maxOutlets"))) : t.maxOutlets();
        if (maxOutlets < 1) {
            throw new IllegalArgumentException("maxOutlets must be at least 1");
        }

        registry.updateProfile(t.id(), name, email, phone, address, plan, maxOutlets);
        if (!status.equals(t.status())) {
            registry.updateStatus(t.id(), status, null);
            registry.audit(t.id(), "STATUS_" + status, actor, "was " + t.status());
        }

        if (t.isActive() || TenantRecord.SUSPENDED.equals(t.status())) {
            try {
                TenantContext.runAs(t.info(), () -> jdbcTemplate.update(
                        "UPDATE tenants SET name = ?, contact_email = ?, contact_phone = ?, address = ?, "
                                + "subscription_plan = ?, max_outlets = ?, status = ?, updated_at = now() WHERE id = ?",
                        name, email, phone, address, plan, maxOutlets, "ACTIVE".equals(status) ? "ACTIVE" : "SUSPENDED",
                        t.id()));
            } catch (Exception e) {
                log.warn("Could not mirror profile into cafe '{}' database: {}", t.code(), e.getMessage());
            }
        }
        return summarise(require(id));
    }

    /** Connection details for support work. The password is only returned here, and every call is logged. */
    public Map<String, Object> revealDbCredentials(Long id, String actor) {
        TenantRecord t = require(id);
        registry.audit(t.id(), "DB_CREDENTIALS_REVEALED", actor, "db=" + t.dbName());
        log.warn("Platform admin '{}' revealed database credentials of cafe '{}'", actor, t.code());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("host", t.dbHost());
        out.put("port", t.dbPort());
        out.put("database", t.dbName());
        out.put("username", t.dbUser());
        out.put("password", crypto.decrypt(t.dbPasswordEnc(), TenantCrypto.purpose(t.code(), PWD_FIELD)));
        return out;
    }

    /** Generates a new database password, applies it in PostgreSQL, stores it and rebuilds live pools. No restart. */
    public Map<String, Object> rotateDbPassword(Long id, String actor) {
        TenantRecord t = require(id);
        String newPassword = dbAdmin.generatePassword();
        try {
            dbAdmin.changePassword(t.dbUser(), newPassword);
        } catch (Exception e) {
            throw new IllegalStateException("Could not change the database password: " + e.getMessage(), e);
        }
        registry.updateDbPassword(t.id(), crypto.encrypt(newPassword, TenantCrypto.purpose(t.code(), PWD_FIELD)));
        routing.evict(t.id());
        registry.audit(t.id(), "DB_PASSWORD_ROTATED", actor, "db=" + t.dbName());
        return Map.of("rotated", true, "database", t.dbName());
    }

    public Map<String, Object> revealBridgeToken(Long id, String actor) {
        TenantRecord t = require(id);
        registry.audit(t.id(), "BRIDGE_TOKEN_REVEALED", actor, null);
        return Map.of("tenantCode", t.code(),
                "bridgeToken", crypto.decrypt(t.bridgeTokenEnc(), TenantCrypto.purpose(t.code(), BRIDGE_FIELD)));
    }

    public Map<String, Object> rotateBridgeToken(Long id, String actor) {
        TenantRecord t = require(id);
        String token = dbAdmin.generateToken();
        registry.updateBridgeToken(t.id(), crypto.encrypt(token, TenantCrypto.purpose(t.code(), BRIDGE_FIELD)));
        registry.audit(t.id(), "BRIDGE_TOKEN_ROTATED", actor, null);
        return Map.of("tenantCode", t.code(), "bridgeToken", token);
    }

    public CafeSummaryDto migrate(Long id) {
        TenantRecord t = require(id);
        migrator.migrate(t);
        return summarise(require(id));
    }

    public List<Map<String, Object>> events(Long id) {
        return registry.recentEvents(require(id).id(), 50);
    }

    public Map<String, Object> getPlatformAnalytics() {
        List<TenantRecord> all = registry.findAll();
        long outlets = 0;
        long users = 0;
        long orders = 0;
        BigDecimal gmv = BigDecimal.ZERO;
        for (TenantRecord t : all) {
            if (!t.isActive()) {
                continue;
            }
            Stats s = statsFor(t);
            outlets += s.outlets;
            users += s.users;
            orders += s.bills;
            gmv = gmv.add(s.gmv);
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCafes", all.size());
        stats.put("totalOutlets", outlets);
        stats.put("totalUsers", users);
        stats.put("totalOrders", orders);
        stats.put("totalGmv", gmv);
        stats.put("activeCafes", all.stream().filter(TenantRecord::isActive).count());
        return stats;
    }

    private record Stats(long outlets, long users, long bills, BigDecimal gmv) {
        static final Stats EMPTY = new Stats(0, 0, 0, BigDecimal.ZERO);
    }

    private Stats statsFor(TenantRecord t) {
        try {
            return TenantContext.callAs(t.info(), () -> {
                long outlets = outletRepository.count();
                long users = userRepository.count();
                Long bills = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM bills WHERE status = 'PAID'", Long.class);
                BigDecimal gmv = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(net_amount), 0) FROM bills WHERE status = 'PAID'", BigDecimal.class);
                return new Stats(outlets, users, bills == null ? 0 : bills, gmv == null ? BigDecimal.ZERO : gmv);
            });
        } catch (Exception e) {
            log.warn("Could not fetch metrics for cafe '{}': {}", t.code(), e.getMessage());
            return Stats.EMPTY;
        }
    }

    private CafeSummaryDto summarise(TenantRecord t) {
        Stats s = t.isActive() ? statsFor(t) : Stats.EMPTY;
        return CafeSummaryDto.builder()
                .id(t.id())
                .name(t.name())
                .code(t.code())
                .contactEmail(t.contactEmail())
                .contactPhone(t.contactPhone())
                .address(t.address())
                .subscriptionPlan(t.subscriptionPlan())
                .status(t.status())
                .maxOutlets(t.maxOutlets())
                .dbName(t.dbName())
                .schemaVersion(t.schemaVersion())
                .lastError(t.lastError())
                .outletsCount(s.outlets)
                .usersCount(s.users)
                .totalBillsCount(s.bills)
                .totalGmv(s.gmv)
                .createdAt(t.createdAt() == null ? null : t.createdAt().toLocalDateTime())
                .build();
    }

    private TenantRecord require(Long id) {
        return registry.findById(id).orElseThrow(() -> new NoSuchElementException("Cafe not found with id: " + id));
    }

    private static String text(Map<String, Object> m, String key, String current) {
        return m.containsKey(key) && m.get(key) != null ? String.valueOf(m.get(key)).trim() : current;
    }
}
