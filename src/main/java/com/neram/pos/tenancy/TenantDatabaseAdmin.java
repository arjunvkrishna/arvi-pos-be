package com.neram.pos.tenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Creates and maintains the PostgreSQL role + database that belong to one cafe. Uses an admin role that can
 * CREATE DATABASE / CREATE ROLE (TENANT_ADMIN_* or, if blank, the control role). Every cafe gets its own role that
 * owns only its database, with PUBLIC connect revoked, so cafe A's credentials cannot even open cafe B's database.
 */
@Slf4j
@Component
public class TenantDatabaseAdmin {

    private static final Pattern IDENT = Pattern.compile("^[a-z][a-z0-9_]{2,62}$");
    private static final Pattern PASSWORD = Pattern.compile("^[A-Za-z0-9_-]{24,128}$");
    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final TenantProperties props;
    private final SecureRandom random = new SecureRandom();

    public TenantDatabaseAdmin(TenantProperties props) {
        this.props = props;
    }

    /** Where a new cafe's database is created. */
    public String targetHost() {
        String h = props.getProvision().getDbHost();
        return h == null || h.isBlank() ? props.getControl().getHost() : h;
    }

    public int targetPort() {
        Integer p = props.getProvision().getDbPort();
        return p == null ? props.getControl().getPort() : p;
    }

    /** 32 random characters, no ambiguous ones, safe to embed in a quoted SQL literal and a JDBC URL. */
    public String generatePassword() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.length)]);
        }
        return sb.toString();
    }

    public String generateToken() {
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i++) {
            sb.append(PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.length)]);
        }
        return sb.toString();
    }

    public boolean databaseExists(String dbName) throws SQLException {
        try (Connection con = adminConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean roleExists(String role) throws SQLException {
        try (Connection con = adminConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void createRoleAndDatabase(String dbName, String role, String password) throws SQLException {
        requireIdent(dbName);
        requireIdent(role);
        requirePassword(password);
        try (Connection con = adminConnection(); Statement st = con.createStatement()) {
            st.execute("CREATE ROLE " + quoteIdent(role) + " LOGIN PASSWORD " + quoteLiteral(password)
                    + " NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT "
                    + Math.max(1, props.getProvision().getConnectionLimit()));
            // The admin must be a member of the role to hand it ownership (needed when it is not a superuser).
            tryGrantMembership(st, role);
            st.execute("CREATE DATABASE " + quoteIdent(dbName) + " OWNER " + quoteIdent(role) + " ENCODING 'UTF8'");
            st.execute("REVOKE ALL ON DATABASE " + quoteIdent(dbName) + " FROM PUBLIC");
        }
        log.info("Created database '{}' owned by role '{}'", dbName, role);
    }

    /** Changes the password of a cafe's role. Live pools are rebuilt by the routing datasource. */
    public void changePassword(String role, String newPassword) throws SQLException {
        requireIdent(role);
        requirePassword(newPassword);
        try (Connection con = adminConnection(); Statement st = con.createStatement()) {
            st.execute("ALTER ROLE " + quoteIdent(role) + " PASSWORD " + quoteLiteral(newPassword));
        }
    }

    /** Used only to undo a half-finished provisioning. Never exposed over HTTP. */
    public void dropDatabaseAndRole(String dbName, String role) {
        requireIdent(dbName);
        requireIdent(role);
        try (Connection con = adminConnection(); Statement st = con.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + quoteIdent(dbName) + " WITH (FORCE)");
            st.execute("DROP ROLE IF EXISTS " + quoteIdent(role));
        } catch (SQLException e) {
            log.error("Could not clean up database '{}' / role '{}': {}", dbName, role, e.getMessage());
        }
    }

    private void tryGrantMembership(Statement st, String role) {
        try {
            st.execute("GRANT " + quoteIdent(role) + " TO CURRENT_USER");
        } catch (SQLException ignored) {
            // superusers do not need it, and some server versions grant it automatically
        }
    }

    private Connection adminConnection() throws SQLException {
        TenantProperties.Control c = props.getControl();
        boolean separate = props.getAdmin().getUser() != null && !props.getAdmin().getUser().isBlank();
        String user = separate ? props.getAdmin().getUser() : c.getUser();
        String password = separate ? props.getAdmin().getPassword() : c.getPassword();
        String url = "jdbc:postgresql://" + targetHost() + ":" + targetPort() + "/postgres";
        Connection con = DriverManager.getConnection(url, user, password);
        con.setAutoCommit(true);
        return con;
    }

    static String quoteIdent(String ident) {
        requireIdent(ident);
        return "\"" + ident + "\"";
    }

    static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    static void requireIdent(String ident) {
        if (ident == null || !IDENT.matcher(ident).matches()) {
            throw new IllegalArgumentException("Unsafe database identifier: " + ident);
        }
    }

    private static void requirePassword(String password) {
        if (password == null || !PASSWORD.matcher(password).matches()) {
            throw new IllegalArgumentException("Generated database password has an unexpected shape");
        }
    }

    /** Derives the database name and role for a cafe code, e.g. "blue-door" -> arvi_blue_door / arvi_blue_door_app. */
    public static String databaseNameFor(String code, String suffix) {
        String base = "arvi_" + code.replace('-', '_');
        String full = suffix == null || suffix.isEmpty() ? base : base + "_" + suffix;
        return full.length() > 55 ? full.substring(0, 55) : full;
    }

    public static String roleNameFor(String dbName) {
        return dbName + "_app";
    }
}
