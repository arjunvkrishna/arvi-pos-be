package com.neram.pos.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReadOnlyRoleProvisionerTest {

    private JdbcTemplate jdbc;
    private ReadOnlyRoleProvisioner provisioner;
    private static final String STRONG = "S3cure-Read0nly-Pass";

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        provisioner = new ReadOnlyRoleProvisioner(jdbc);
        when(jdbc.queryForObject("select current_user", String.class)).thenReturn("neram_user");
        when(jdbc.queryForObject("select current_database()", String.class)).thenReturn("neram_pos");
        when(jdbc.queryForObject(startsWith("select format("), eq(String.class), any(), any(), any()))
                .thenAnswer(i -> "DDL-FOR-" + i.getArgument(2));
        // default: no such role exists, legacy role exists and can log in
        when(jdbc.queryForList(contains("rolsuper"), eq(Boolean.class), any())).thenReturn(List.of());
        when(jdbc.queryForList(contains("rolcanlogin"), eq(Boolean.class), any())).thenReturn(List.of(true));
    }

    private void configure(String user, String password) {
        ReflectionTestUtils.setField(provisioner, "roleName", user);
        ReflectionTestUtils.setField(provisioner, "rolePassword", password);
    }

    private List<String> executed() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(0)).execute(c.capture());
        return c.getAllValues();
    }

    @Test
    @DisplayName("Nothing configured: no DDL at all (existing deployments unchanged)")
    void unsetDoesNothing() {
        configure("", "");
        provisioner.provision();
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("Only one of user/password set, bad names, short passwords and reused app user are all refused")
    void invalidConfigurationRefused() {
        for (String[] c : new String[][]{{"reports_ro", ""}, {"", STRONG}, {"Bad-Name", STRONG}, {"1abc", STRONG},
                {"pg_monitor", STRONG}, {"postgres", STRONG}, {"x", STRONG}, {"reports_ro", "short"}, {"neram_user", STRONG}}) {
            configure(c[0], c[1]);
            provisioner.provision();
        }
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("An existing role with elevated privileges is never reused or altered")
    void elevatedRoleRefused() {
        when(jdbc.queryForList(contains("rolsuper"), eq(Boolean.class), eq("some_admin"))).thenReturn(List.of(true));
        configure("some_admin", STRONG);
        provisioner.provision();
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("A valid config creates the role with narrow grants and disables the legacy default-password role")
    void createsRoleAndDisablesLegacy() {
        configure("reports_ro", STRONG);
        provisioner.provision();
        List<String> sql = executed();
        assertEquals("DDL-FOR-CREATE ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L", sql.get(0).replace("DDL-FOR-", "DDL-FOR-"));
        assertTrue(sql.stream().anyMatch(s -> s.equals("GRANT CONNECT ON DATABASE \"neram_pos\" TO reports_ro")));
        assertTrue(sql.stream().anyMatch(s -> s.startsWith("REVOKE ALL PRIVILEGES ON ALL TABLES")));
        assertTrue(sql.stream().anyMatch(s -> s.contains("ON menu_items TO reports_ro")));
        assertTrue(sql.stream().anyMatch(s -> s.contains("ON categories TO reports_ro")));
        assertTrue(sql.stream().anyMatch(s -> s.contains("ON app_settings TO reports_ro")));
        assertTrue(sql.contains("ALTER ROLE neram_readonly NOLOGIN"));
        // read-only: no write privileges are ever granted
        assertTrue(sql.stream().noneMatch(s -> s.matches("(?i).*(INSERT|UPDATE|DELETE|TRUNCATE|ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO).*")));
        // the password never appears in any statement we build ourselves
        assertTrue(sql.stream().noneMatch(s -> s.contains(STRONG)));
    }

    @Test
    @DisplayName("Re-running with an existing role rotates it with ALTER ROLE and does not create again")
    void existingRoleIsAltered() {
        when(jdbc.queryForList(contains("rolsuper"), eq(Boolean.class), eq("reports_ro"))).thenReturn(List.of(false));
        configure("reports_ro", STRONG);
        provisioner.provision();
        ArgumentCaptor<Object> pattern = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(startsWith("select format("), eq(String.class), pattern.capture(), any(), any());
        assertTrue(pattern.getValue().toString().startsWith("ALTER ROLE"));
    }

    @Test
    @DisplayName("Choosing the legacy role name only rotates its password and keeps login enabled")
    void legacyNameKeptWhenChosen() {
        configure("neram_readonly", STRONG);
        provisioner.provision();
        assertFalse(executed().contains("ALTER ROLE neram_readonly NOLOGIN"));
    }

    @Test
    @DisplayName("A database error never propagates out of startup")
    void failuresDoNotBreakStartup() {
        configure("reports_ro", STRONG);
        doThrow(new RuntimeException("permission denied")).when(jdbc).execute(anyString());
        assertDoesNotThrow(() -> provisioner.run(null));
    }
}
