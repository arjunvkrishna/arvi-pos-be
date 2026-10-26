package com.neram.pos.tenancy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantDatabaseAdminTest {

    private final TenantDatabaseAdmin admin = new TenantDatabaseAdmin(new TenantProperties());

    @Test
    void databaseAndRoleNamesAreDerivedFromTheCafeCodeAndStayWithinPostgresLimits() {
        assertEquals("arvi_blue_door", TenantDatabaseAdmin.databaseNameFor("blue-door", null));
        assertEquals("arvi_blue_door_ab12", TenantDatabaseAdmin.databaseNameFor("blue-door", "ab12"));
        assertEquals("arvi_blue_door_app", TenantDatabaseAdmin.roleNameFor("arvi_blue_door"));

        String longCode = "a".repeat(30);
        String db = TenantDatabaseAdmin.databaseNameFor(longCode, "ab12");
        assertTrue(TenantDatabaseAdmin.roleNameFor(db).length() <= 63);
    }

    @Test
    void identifiersThatCouldInjectSqlAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> TenantDatabaseAdmin.quoteIdent("x\"; DROP DATABASE y; --"));
        assertThrows(IllegalArgumentException.class, () -> TenantDatabaseAdmin.quoteIdent("Upper"));
        assertThrows(IllegalArgumentException.class, () -> TenantDatabaseAdmin.quoteIdent("a b"));
        assertThrows(IllegalArgumentException.class, () -> TenantDatabaseAdmin.quoteIdent(null));
        assertEquals("\"arvi_blue\"", TenantDatabaseAdmin.quoteIdent("arvi_blue"));
    }

    @Test
    void literalsDoubleQuotes() {
        assertEquals("'it''s'", TenantDatabaseAdmin.quoteLiteral("it's"));
    }

    @Test
    void generatedPasswordsAreLongUniqueAndSafeInsideSqlAndUrls() {
        String a = admin.generatePassword();
        String b = admin.generatePassword();

        assertEquals(32, a.length());
        assertNotEquals(a, b);
        assertTrue(a.matches("[A-Za-z0-9]{32}"));
    }

    @Test
    void newDatabasesAreCreatedOnTheControlServerUnlessAnotherHostIsConfigured() {
        TenantProperties p = new TenantProperties();
        p.getControl().setHost("pg-main");
        p.getControl().setPort(5432);
        assertEquals("pg-main", new TenantDatabaseAdmin(p).targetHost());
        assertEquals(5432, new TenantDatabaseAdmin(p).targetPort());

        p.getProvision().setDbHost("pg-tenants");
        p.getProvision().setDbPort(6543);
        assertEquals("pg-tenants", new TenantDatabaseAdmin(p).targetHost());
        assertEquals(6543, new TenantDatabaseAdmin(p).targetPort());
    }
}
