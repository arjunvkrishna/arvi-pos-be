package com.neram.pos.tenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and writes the cafe registry in the control database. Lookups are cached for a few seconds so the
 * per-request tenant check is cheap, while a suspension made on another replica still takes effect almost at once.
 */
@Slf4j
@Component
public class TenantRegistry {

    static final long CACHE_TTL_MS = 10_000L;

    private static final String COLUMNS = "id, code, name, status, subscription_plan, max_outlets, contact_email, "
            + "contact_phone, address, db_host, db_port, db_name, db_user, db_password_enc, bridge_token_enc, "
            + "schema_version, migrated_at, last_error, created_at";

    private static final RowMapper<TenantRecord> MAPPER = (rs, i) -> new TenantRecord(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("status"),
            rs.getString("subscription_plan"),
            rs.getInt("max_outlets"),
            rs.getString("contact_email"),
            rs.getString("contact_phone"),
            rs.getString("address"),
            rs.getString("db_host"),
            rs.getInt("db_port"),
            rs.getString("db_name"),
            rs.getString("db_user"),
            rs.getString("db_password_enc"),
            rs.getString("bridge_token_enc"),
            rs.getString("schema_version"),
            rs.getObject("migrated_at", java.time.OffsetDateTime.class),
            rs.getString("last_error"),
            rs.getObject("created_at", java.time.OffsetDateTime.class));

    private record Cached(TenantRecord record, long loadedAt) {
        boolean fresh() {
            return System.currentTimeMillis() - loadedAt < CACHE_TTL_MS;
        }
    }

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<Long, Cached> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Cached> byCode = new ConcurrentHashMap<>();
    private volatile List<TenantRecord> activeCache = List.of();
    private volatile long activeLoadedAt = 0L;

    public TenantRegistry(@Qualifier("controlDataSource") DataSource controlDataSource) {
        this.jdbc = new JdbcTemplate(controlDataSource);
    }

    public Optional<TenantRecord> findById(long id) {
        Cached c = byId.get(id);
        if (c != null && c.fresh()) {
            return Optional.ofNullable(c.record());
        }
        List<TenantRecord> rows = jdbc.query("SELECT " + COLUMNS + " FROM tenants WHERE id = ?", MAPPER, id);
        TenantRecord rec = rows.isEmpty() ? null : rows.get(0);
        remember(id, rec == null ? null : rec.code(), rec);
        return Optional.ofNullable(rec);
    }

    public Optional<TenantRecord> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Cached c = byCode.get(code);
        if (c != null && c.fresh()) {
            return Optional.ofNullable(c.record());
        }
        List<TenantRecord> rows = jdbc.query("SELECT " + COLUMNS + " FROM tenants WHERE code = ?", MAPPER, code);
        TenantRecord rec = rows.isEmpty() ? null : rows.get(0);
        if (rec != null) {
            remember(rec.id(), code, rec);
        }
        return Optional.ofNullable(rec);
    }

    public List<TenantRecord> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM tenants ORDER BY id", MAPPER);
    }

    /** Active cafes, cached briefly; used by background jobs that visit every cafe. */
    public List<TenantRecord> findActive() {
        if (System.currentTimeMillis() - activeLoadedAt < CACHE_TTL_MS) {
            return activeCache;
        }
        List<TenantRecord> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM tenants WHERE status = 'ACTIVE' ORDER BY id", MAPPER);
        activeCache = rows;
        activeLoadedAt = System.currentTimeMillis();
        return rows;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM tenants", Long.class);
        return n == null ? 0 : n;
    }

    /** Inserts the cafe with its (already encrypted) credentials before any database is created. */
    public TenantRecord insert(NewTenant n) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO tenants (code, name, status, subscription_plan, max_outlets, contact_email, "
                                + "contact_phone, address, db_host, db_port, db_name, db_user, db_password_enc, "
                                + "bridge_token_enc) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setString(i++, n.code());
                ps.setString(i++, n.name());
                ps.setString(i++, TenantRecord.PROVISIONING);
                ps.setString(i++, n.subscriptionPlan());
                ps.setInt(i++, n.maxOutlets());
                ps.setString(i++, n.contactEmail());
                ps.setString(i++, n.contactPhone());
                ps.setString(i++, n.address());
                ps.setString(i++, n.dbHost());
                ps.setInt(i++, n.dbPort());
                ps.setString(i++, n.dbName());
                ps.setString(i++, n.dbUser());
                ps.setString(i++, n.dbPasswordEnc());
                ps.setString(i, n.bridgeTokenEnc());
                return ps;
            }, keys);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("A cafe with code '" + n.code() + "' already exists");
        }
        Number id = (Number) keys.getKeys().get("id");
        invalidate();
        return findById(id.longValue()).orElseThrow();
    }

    public void updateStatus(long id, String status, String lastError) {
        jdbc.update("UPDATE tenants SET status = ?, last_error = ?, updated_at = now() WHERE id = ?",
                status, truncate(lastError), id);
        invalidate();
    }

    public void updateProfile(long id, String name, String email, String phone, String address,
                              String plan, int maxOutlets) {
        jdbc.update("UPDATE tenants SET name = ?, contact_email = ?, contact_phone = ?, address = ?, "
                        + "subscription_plan = ?, max_outlets = ?, updated_at = now() WHERE id = ?",
                name, email, phone, address, plan, maxOutlets, id);
        invalidate();
    }

    /** Keeps the registry's display data in step when a cafe edits its own profile. */
    public void updateContact(long id, String name, String email, String phone, String address) {
        jdbc.update("UPDATE tenants SET name = ?, contact_email = ?, contact_phone = ?, address = ?, "
                + "updated_at = now() WHERE id = ?", name, email, phone, address, id);
        invalidate();
    }

    public void updateDbPassword(long id, String dbPasswordEnc) {
        jdbc.update("UPDATE tenants SET db_password_enc = ?, updated_at = now() WHERE id = ?", dbPasswordEnc, id);
        invalidate();
    }

    public void updateBridgeToken(long id, String bridgeTokenEnc) {
        jdbc.update("UPDATE tenants SET bridge_token_enc = ?, updated_at = now() WHERE id = ?", bridgeTokenEnc, id);
        invalidate();
    }

    public void markMigrated(long id, String schemaVersion, String error) {
        if (error == null) {
            jdbc.update("UPDATE tenants SET schema_version = ?, migrated_at = now(), last_error = NULL WHERE id = ?",
                    schemaVersion, id);
        } else {
            jdbc.update("UPDATE tenants SET last_error = ? WHERE id = ?", truncate(error), id);
        }
        invalidate();
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM tenants WHERE id = ?", id);
        invalidate();
    }

    /** Append-only trail of sensitive operations (provisioning, suspension, credential reveal/rotation). */
    public void audit(Long tenantId, String event, String actor, String detail) {
        try {
            jdbc.update("INSERT INTO tenant_events (tenant_id, event, actor, detail) VALUES (?,?,?,?)",
                    tenantId, event, actor, truncate(detail));
        } catch (Exception e) {
            log.warn("Could not write tenant audit event {}: {}", event, e.getMessage());
        }
    }

    public List<java.util.Map<String, Object>> recentEvents(long tenantId, int limit) {
        return jdbc.queryForList("SELECT event, actor, detail, created_at FROM tenant_events "
                + "WHERE tenant_id = ? ORDER BY id DESC LIMIT ?", tenantId, limit);
    }

    public void invalidate() {
        byId.clear();
        byCode.clear();
        activeLoadedAt = 0L;
    }

    private void remember(long id, String code, TenantRecord rec) {
        Cached c = new Cached(rec, System.currentTimeMillis());
        byId.put(id, c);
        if (code != null) {
            byCode.put(code, c);
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }

    public record NewTenant(String code, String name, String subscriptionPlan, int maxOutlets, String contactEmail,
                            String contactPhone, String address, String dbHost, int dbPort, String dbName,
                            String dbUser, String dbPasswordEnc, String bridgeTokenEnc) {
    }
}
