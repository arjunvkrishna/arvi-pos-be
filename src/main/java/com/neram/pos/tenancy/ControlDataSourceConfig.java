package com.neram.pos.tenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Wires the two kinds of datasource:
 * <ul>
 *   <li>{@code controlDataSource}: the registry database (cafes, platform admins). Always the same database.</li>
 *   <li>the primary {@link DataSource}: a router that hands each request the database of the cafe in
 *       {@link TenantContext}. All JPA repositories and JdbcTemplates use it, so tenant code stays unchanged.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ControlDataSourceConfig {

    @Bean(name = "controlDataSource", destroyMethod = "close")
    public HikariDataSource controlDataSource(TenantProperties props) {
        TenantProperties.Control c = props.getControl();
        String url = "jdbc:postgresql://" + c.getHost() + ":" + c.getPort() + "/" + c.getName();
        createControlDatabaseIfMissing(props, url);

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("control");
        cfg.setJdbcUrl(url);
        cfg.setUsername(c.getUser());
        cfg.setPassword(c.getPassword());
        cfg.setMaximumPoolSize(c.getPoolSize());
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(30_000);
        cfg.addDataSourceProperty("ApplicationName", "arvi:control");
        HikariDataSource ds = new HikariDataSource(cfg);

        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/control")
                .baselineOnMigrate(true)
                .lockRetryCount(60)
                .load()
                .migrate();
        restrictControlDatabaseAccess(ds, c.getName());
        log.info("Control database '{}' is ready", c.getName());
        return ds;
    }

    /**
     * Cafe roles must not even be able to open the control database (it holds the registry). Revoking the default
     * PUBLIC connect right does that; only done when the app's own role owns the database, so it can never lock itself out.
     */
    private void restrictControlDatabaseAccess(DataSource ds, String dbName) {
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            boolean owner;
            try (java.sql.ResultSet rs = st.executeQuery("SELECT pg_get_userbyid(datdba) = current_user "
                    + "OR (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) FROM pg_database "
                    + "WHERE datname = current_database()")) {
                owner = rs.next() && rs.getBoolean(1);
            }
            if (owner) {
                st.execute("REVOKE CONNECT ON DATABASE " + TenantDatabaseAdmin.quoteIdent(dbName) + " FROM PUBLIC");
            } else {
                log.warn("Control database '{}' is not owned by the app role; not restricting PUBLIC connect", dbName);
            }
        } catch (SQLException | RuntimeException e) {
            log.warn("Could not restrict access to control database: {}", e.getMessage());
        }
    }

    @Bean
    @Primary
    public TenantRoutingDataSource dataSource(TenantRegistry registry, TenantCrypto crypto, TenantProperties props) {
        return new TenantRoutingDataSource(registry, crypto, props);
    }

    /** First-run convenience: create the control database when the configured admin role is allowed to. */
    private void createControlDatabaseIfMissing(TenantProperties props, String controlUrl) {
        TenantProperties.Control c = props.getControl();
        try (Connection ignored = DriverManager.getConnection(controlUrl, c.getUser(), c.getPassword())) {
            return;
        } catch (SQLException e) {
            if (!"3D000".equals(e.getSQLState())) {
                return; // other failures surface when the pool starts, with a clearer message
            }
        }
        String adminUser = hasText(props.getAdmin().getUser()) ? props.getAdmin().getUser() : c.getUser();
        String adminPassword = hasText(props.getAdmin().getUser()) ? props.getAdmin().getPassword() : c.getPassword();
        String maintenanceUrl = "jdbc:postgresql://" + c.getHost() + ":" + c.getPort() + "/postgres";
        try (Connection con = DriverManager.getConnection(maintenanceUrl, adminUser, adminPassword);
             Statement st = con.createStatement()) {
            st.execute("CREATE DATABASE " + TenantDatabaseAdmin.quoteIdent(c.getName()) + " OWNER "
                    + TenantDatabaseAdmin.quoteIdent(c.getUser()));
            log.info("Created control database '{}'", c.getName());
        } catch (SQLException e) {
            throw new IllegalStateException("Control database '" + c.getName()
                    + "' does not exist and could not be created: " + e.getMessage(), e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
