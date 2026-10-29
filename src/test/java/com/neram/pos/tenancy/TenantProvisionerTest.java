package com.neram.pos.tenancy;

import com.neram.pos.dto.CreateCafeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantProvisionerTest {

    private TenantRegistry registry;
    private TenantCrypto crypto;
    private TenantDatabaseAdmin dbAdmin;
    private TenantMigrator migrator;
    private TenantBootstrapper bootstrapper;
    private TenantRoutingDataSource routing;
    private TenantProvisioner provisioner;

    @BeforeEach
    void setUp() throws Exception {
        TenantProperties props = new TenantProperties();
        props.setSecretKey(Base64.getEncoder().encodeToString(new byte[32]));
        crypto = new TenantCrypto(props);
        registry = mock(TenantRegistry.class);
        dbAdmin = spy(new TenantDatabaseAdmin(props));
        migrator = mock(TenantMigrator.class);
        bootstrapper = mock(TenantBootstrapper.class);
        routing = mock(TenantRoutingDataSource.class);
        provisioner = new TenantProvisioner(registry, crypto, dbAdmin, migrator, bootstrapper, routing);

        lenient().when(registry.findByCode(anyString())).thenReturn(Optional.empty());
        lenient().doReturn(false).when(dbAdmin).databaseExists(anyString());
        lenient().doReturn(false).when(dbAdmin).roleExists(anyString());
        lenient().doNothing().when(dbAdmin).createRoleAndDatabase(anyString(), anyString(), anyString());
        lenient().doNothing().when(dbAdmin).dropDatabaseAndRole(anyString(), anyString());
        lenient().when(registry.insert(any())).thenAnswer(i -> {
            TenantRegistry.NewTenant n = i.getArgument(0);
            return recordOf(7L, n, TenantRecord.PROVISIONING);
        });
        lenient().when(registry.findById(7L)).thenAnswer(i -> Optional.of(
                new TenantRecord(7, "bluedoor", "Blue Door", TenantRecord.ACTIVE, "PRO", 5, null, null, null,
                        "h", 5432, "arvi_bluedoor", "arvi_bluedoor_app", "enc", "enc", "1", null, null, null)));
    }

    private static TenantRecord recordOf(long id, TenantRegistry.NewTenant n, String status) {
        return new TenantRecord(id, n.code(), n.name(), status, n.subscriptionPlan(), n.maxOutlets(), n.contactEmail(),
                n.contactPhone(), n.address(), n.dbHost(), n.dbPort(), n.dbName(), n.dbUser(), n.dbPasswordEnc(),
                n.bridgeTokenEnc(), null, null, null, null);
    }

    private static CreateCafeRequest request(String name, String code) {
        CreateCafeRequest r = new CreateCafeRequest();
        r.setName(name);
        r.setCode(code);
        r.setAdminUsername("owner");
        r.setAdminPassword("Str0ng!Pass1");
        return r;
    }

    @Test
    void createsRoleAndDatabaseMigratesAndActivates() throws Exception {
        provisioner.provision(request("Blue Door", "bluedoor"), "superadmin");

        verify(dbAdmin).createRoleAndDatabase(eq("arvi_bluedoor"), eq("arvi_bluedoor_app"), anyString());
        verify(migrator).migrate(any());
        verify(bootstrapper).bootstrap(any(), any());
        verify(registry).updateStatus(7L, TenantRecord.ACTIVE, null);
    }

    @Test
    void storesCredentialsEncryptedBeforeCreatingAnything() throws Exception {
        ArgumentCaptor<TenantRegistry.NewTenant> stored = ArgumentCaptor.forClass(TenantRegistry.NewTenant.class);
        ArgumentCaptor<String> createdWith = ArgumentCaptor.forClass(String.class);
        org.mockito.InOrder order = inOrder(registry, dbAdmin);

        provisioner.provision(request("Blue Door", "bluedoor"), "superadmin");

        order.verify(registry).insert(stored.capture());
        order.verify(dbAdmin).createRoleAndDatabase(anyString(), anyString(), createdWith.capture());
        String plainPassword = createdWith.getValue();
        assertNotEquals(plainPassword, stored.getValue().dbPasswordEnc());
        assertEquals(plainPassword, crypto.decrypt(stored.getValue().dbPasswordEnc(),
                TenantCrypto.purpose("bluedoor", "db-password")));
        assertTrue(stored.getValue().bridgeTokenEnc().startsWith("v1:"));
    }

    @Test
    void failureRollsBackTheDatabaseAndTheRegistryRow() throws Exception {
        doThrow(new IllegalStateException("flyway exploded")).when(migrator).migrate(any());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provisioner.provision(request("Blue Door", "bluedoor"), "superadmin"));

        assertTrue(ex.getMessage().contains("flyway exploded"));
        verify(dbAdmin).dropDatabaseAndRole("arvi_bluedoor", "arvi_bluedoor_app");
        verify(routing).evict(7L);
        verify(registry).delete(7L);
        verify(registry, never()).updateStatus(eq(7L), eq(TenantRecord.ACTIVE), any());
    }

    @Test
    void ifCleanupFailsTheRowIsKeptAsFailedSoCredentialsAreNotLost() throws Exception {
        doThrow(new IllegalStateException("boom")).when(migrator).migrate(any());
        // free at the pre-check, but still present after the drop attempt
        doReturn(false, true).when(dbAdmin).databaseExists("arvi_bluedoor");

        assertThrows(IllegalStateException.class, () -> provisioner.provision(request("Blue Door", "bluedoor"), "x"));

        verify(registry).updateStatus(eq(7L), eq(TenantRecord.FAILED), anyString());
        verify(registry, never()).delete(anyLong());
    }

    @Test
    void explicitCodeThatAlreadyExistsIsRejectedButDerivedCodesGetASuffix() {
        TenantRecord existing = new TenantRecord(1, "bluedoor", "x", "ACTIVE", "PRO", 1, null, null, null, "h", 1,
                "d", "u", "e", "e", null, null, null, null);
        when(registry.findByCode("bluedoor")).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> provisioner.provision(request("Blue Door", "bluedoor"), "x"));

        CreateCafeRequest derived = request("Bluedoor", null);
        provisioner.provision(derived, "x");
        ArgumentCaptor<TenantRegistry.NewTenant> captor = ArgumentCaptor.forClass(TenantRegistry.NewTenant.class);
        verify(registry).insert(captor.capture());
        assertTrue(captor.getValue().code().matches("bluedoor-\\d{3}"), captor.getValue().code());
    }

    @Test
    void ifTheDatabaseNameIsTakenAnotherIsChosenInsteadOfTouchingTheExistingOne() throws Exception {
        doReturn(true).when(dbAdmin).databaseExists("arvi_bluedoor");

        provisioner.provision(request("Blue Door", "bluedoor"), "x");

        ArgumentCaptor<String> db = ArgumentCaptor.forClass(String.class);
        verify(dbAdmin).createRoleAndDatabase(db.capture(), anyString(), anyString());
        assertTrue(db.getValue().matches("arvi_bluedoor_[0-9a-f]{4}"), db.getValue());
    }

    @Test
    void invalidRequestsNeverReachTheDatabase() throws Exception {
        CreateCafeRequest noName = request("  ", "x1");
        CreateCafeRequest weakPassword = request("Cafe", "cafe");
        weakPassword.setAdminPassword("weak");
        CreateCafeRequest badUser = request("Cafe", "cafe");
        badUser.setAdminUsername("a b");
        CreateCafeRequest badCode = request("Cafe", "x!");

        for (CreateCafeRequest r : new CreateCafeRequest[]{noName, weakPassword, badUser, badCode}) {
            assertThrows(IllegalArgumentException.class, () -> provisioner.provision(r, "x"));
        }
        verify(registry, never()).insert(any());
        verify(dbAdmin, never()).createRoleAndDatabase(anyString(), anyString(), anyString());
    }

    @Test
    void unreachableDatabaseServerGivesAClearError() throws Exception {
        doThrow(new SQLException("connection refused")).when(dbAdmin).databaseExists(anyString());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provisioner.provision(request("Blue Door", "bluedoor"), "x"));
        assertTrue(ex.getMessage().contains("Cannot reach the database server"));
        verify(registry, never()).insert(any());
    }

    @Test
    void codesAreNormalised() {
        assertEquals("blue-door-cafe", TenantProvisioner.normalise("  Blue Door  Cafe! "));
        assertEquals("a-b", TenantProvisioner.normalise("A__B"));
        assertTrue(TenantProvisioner.normalise("x".repeat(60)).length() <= 30);
    }

    private TenantRegistry.NewTenant provisionedWith(String plan, Integer maxOutlets) throws Exception {
        clearInvocations(registry);
        CreateCafeRequest r = request("Blue Door", "bluedoor");
        r.setSubscriptionPlan(plan);
        r.setMaxOutlets(maxOutlets);
        ArgumentCaptor<TenantRegistry.NewTenant> stored = ArgumentCaptor.forClass(TenantRegistry.NewTenant.class);
        provisioner.provision(r, "superadmin");
        verify(registry).insert(stored.capture());
        return stored.getValue();
    }

    @Test
    void aNewCafeGetsTheBranchCountOfItsPlanUnlessOneIsNamed() throws Exception {
        assertEquals(1, provisionedWith("STARTER", null).maxOutlets());
        assertEquals("STARTER", provisionedWith("starter", null).subscriptionPlan());
    }

    @Test
    void aCafeWithNoPlanNamedStartsOnProWithFiveBranches() throws Exception {
        TenantRegistry.NewTenant t = provisionedWith(null, null);
        assertEquals("PRO", t.subscriptionPlan());
        assertEquals(5, t.maxOutlets());
    }

    @Test
    void anExplicitBranchCountWinsOverThePlanDefault() throws Exception {
        assertEquals(3, provisionedWith("STARTER", 3).maxOutlets());
    }

    @Test
    void aPlanThatDoesNotExistIsRefusedBeforeAnythingIsCreated() throws Exception {
        CreateCafeRequest r = request("Blue Door", "bluedoor");
        r.setSubscriptionPlan("GOLD");
        assertThrows(IllegalArgumentException.class, () -> provisioner.provision(r, "superadmin"));
        verify(registry, never()).insert(any());
        verify(dbAdmin, never()).createRoleAndDatabase(anyString(), anyString(), anyString());
    }
}
