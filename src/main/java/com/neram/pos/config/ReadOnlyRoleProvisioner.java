package com.neram.pos.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Creates (or rotates) the restricted read-only PostgreSQL role from DB_READONLY_USER / DB_READONLY_PASSWORD.
 *
 * <ul>
 *   <li>Both variables blank: does nothing (existing deployments behave exactly as before).</li>
 *   <li>The role only receives column-level SELECT on the public-menu tables; it can never modify data or schema.</li>
 *   <li>The legacy role {@code neram_readonly} (created by migrations V20/V33 with a published default password)
 *       is switched to NOLOGIN once a custom role is configured.</li>
 *   <li>Never fails startup and never logs the password.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadOnlyRoleProvisioner implements ApplicationRunner {

    static final String LEGACY_ROLE = "neram_readonly";
    static final int MIN_PASSWORD_LENGTH = 12;
    private static final Pattern ROLE_NAME = Pattern.compile("^[a-z_][a-z0-9_]{2,62}$");

    private final JdbcTemplate jdbc;

    @Value("${neram.db.readonly.user:}")
    private String roleName;

    @Value("${neram.db.readonly.password:}")
    private String rolePassword;

    @Override
    public void run(ApplicationArguments args) {
        // Each cafe has its own database and no cafe is selected at startup; the old single-database role is skipped.
        if (com.neram.pos.tenancy.TenantContext.current().isEmpty()) {
            log.debug("Read-only DB role provisioning skipped: databases are per cafe");
            return;
        }
        try {
            provision();
        } catch (Exception e) {
            log.warn("Read-only DB role provisioning skipped: {}", e.getMessage());
        }
    }

    void provision() {
        String user = roleName == null ? "" : roleName.trim();
        String password = rolePassword == null ? "" : rolePassword;

        if (user.isEmpty() && password.isEmpty()) {
            if (roleCanLogin(LEGACY_ROLE)) {
                log.warn("Legacy read-only role '{}' can still log in with its default password. "
                        + "Set DB_READONLY_USER and DB_READONLY_PASSWORD to replace it.", LEGACY_ROLE);
            }
            return;
        }
        if (user.isEmpty() || password.isEmpty()) {
            log.warn("Set both DB_READONLY_USER and DB_READONLY_PASSWORD (or neither). Read-only role not provisioned.");
            return;
        }
        if (!ROLE_NAME.matcher(user).matches() || user.startsWith("pg_") || "postgres".equals(user)) {
            log.warn("DB_READONLY_USER must be 3-63 chars of a-z, 0-9, _ (not starting with a digit or 'pg_'). Not provisioned.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.indexOf('\0') >= 0) {
            log.warn("DB_READONLY_PASSWORD must be at least {} characters. Read-only role not provisioned.", MIN_PASSWORD_LENGTH);
            return;
        }
        String currentUser = jdbc.queryForObject("select current_user", String.class);
        if (user.equals(currentUser)) {
            log.warn("DB_READONLY_USER must differ from the application's own DB user. Not provisioned.");
            return;
        }
        Boolean elevated = firstOrNull(jdbc.queryForList(
                "select (rolsuper or rolcreaterole or rolcreatedb or rolreplication) from pg_roles where rolname = ?",
                Boolean.class, user));
        if (Boolean.TRUE.equals(elevated)) {
            log.warn("Role '{}' already exists with elevated privileges; refusing to reuse it as the read-only role.", user);
            return;
        }

        boolean exists = elevated != null;
        // The server quotes the identifier/literal for us; the password never appears in our own SQL text or logs.
        String ddl = jdbc.queryForObject(
                "select format(?, ?::text, ?::text)", String.class,
                (exists ? "ALTER ROLE %I" : "CREATE ROLE %I")
                        + " WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L",
                user, password);
        jdbc.execute(ddl);

        String db = jdbc.queryForObject("select current_database()", String.class);
        jdbc.execute("GRANT CONNECT ON DATABASE \"" + db.replace("\"", "\"\"") + "\" TO " + user);
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + user);
        jdbc.execute("REVOKE CREATE ON SCHEMA public FROM " + user);
        jdbc.execute("REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM " + user);
        jdbc.execute("GRANT SELECT (id, name, price, category_id, is_veg, is_available, description, active) ON menu_items TO " + user);
        jdbc.execute("GRANT SELECT (id, name, icon, sort_order, active) ON categories TO " + user);
        jdbc.execute("GRANT SELECT (setting_key, setting_value) ON app_settings TO " + user);
        log.info("Read-only DB role '{}' {} (column-level SELECT on menu data only).", user, exists ? "updated" : "created");

        if (!LEGACY_ROLE.equals(user) && roleCanLogin(LEGACY_ROLE)) {
            jdbc.execute("ALTER ROLE " + LEGACY_ROLE + " NOLOGIN");
            log.info("Legacy role '{}' (default password) has been disabled for login.", LEGACY_ROLE);
        }
    }

    private boolean roleCanLogin(String role) {
        return Boolean.TRUE.equals(firstOrNull(jdbc.queryForList(
                "select rolcanlogin from pg_roles where rolname = ?", Boolean.class, role)));
    }

    private static <T> T firstOrNull(java.util.List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }
}
