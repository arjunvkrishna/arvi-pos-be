package com.neram.pos.service;

import com.neram.pos.dto.CafeSummaryDto;
import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.tenancy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SuperAdminServiceTest {

    private TenantRegistry registry;
    private TenantProvisioner provisioner;
    private TenantMigrator migrator;
    private TenantCrypto crypto;
    private TenantDatabaseAdmin dbAdmin;
    private TenantRoutingDataSource routing;
    private JdbcTemplate jdbcTemplate;
    private SuperAdminService service;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setSecretKey(Base64.getEncoder().encodeToString(new byte[32]));
        crypto = new TenantCrypto(props);
        registry = mock(TenantRegistry.class);
        provisioner = mock(TenantProvisioner.class);
        migrator = mock(TenantMigrator.class);
        dbAdmin = mock(TenantDatabaseAdmin.class);
        routing = mock(TenantRoutingDataSource.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new SuperAdminService(registry, provisioner, migrator, crypto, dbAdmin, routing, jdbcTemplate);
    }

    private TenantRecord cafe(long id, String code, String status) {
        return new TenantRecord(id, code, "Cafe " + code, status, "PRO", 5, "a@b.c", "123", "addr", "pg", 5432,
                "arvi_" + code, "arvi_" + code + "_app",
                crypto.encrypt("db-secret-" + code, TenantCrypto.purpose(code, "db-password")),
                crypto.encrypt("bridge-" + code, TenantCrypto.purpose(code, "bridge-token")),
                "1", OffsetDateTime.now(), null, OffsetDateTime.now());
    }

    @Test
    @DisplayName("Creating a cafe delegates to the provisioner and reports the new database")
    void createCafe() {
        CreateCafeRequest req = new CreateCafeRequest();
        req.setName("Blue Door");
        when(provisioner.provision(req, "root")).thenReturn(cafe(5, "bluedoor", "ACTIVE"));

        CafeSummaryDto dto = service.createCafe(req, "root");

        assertEquals(5L, dto.getId());
        assertEquals("arvi_bluedoor", dto.getDbName());
        assertEquals("ACTIVE", dto.getStatus());
    }

    @Test
    @DisplayName("Cafe metrics come from the cafe's own database")
    void metricsAreReadInsideTheCafesContext() {
        when(registry.findAll()).thenReturn(List.of(cafe(5, "bluedoor", "ACTIVE")));
        when(jdbcTemplate.queryForObject(contains("FROM outlets"), eq(Long.class))).thenAnswer(i -> {
            assertEquals(5L, TenantContext.requireId());
            return 3L;
        });
        when(jdbcTemplate.queryForObject(contains("FROM users"), eq(Long.class))).thenReturn(8L);
        when(jdbcTemplate.queryForObject(contains("FROM bills"), eq(Long.class))).thenReturn(150L);
        when(jdbcTemplate.queryForObject(contains("SUM(net_amount)"), eq(BigDecimal.class))).thenReturn(new BigDecimal("75420.50"));

        CafeSummaryDto s = service.getAllCafes().get(0);

        assertEquals(3L, s.getOutletsCount());
        assertEquals(8L, s.getUsersCount());
        assertEquals(150L, s.getTotalBillsCount());
        assertEquals(new BigDecimal("75420.50"), s.getTotalGmv());
        assertTrue(TenantContext.current().isEmpty());
    }

    @Test
    @DisplayName("Every cafe gets its own counts: the cafe list loops over cafes inside one request, so each lookup must be routed afresh")
    void eachCafeGetsItsOwnCounts() {
        when(registry.findAll()).thenReturn(List.of(cafe(5, "bluedoor", "ACTIVE"), cafe(6, "greenleaf", "ACTIVE")));
        when(jdbcTemplate.queryForObject(contains("FROM outlets"), eq(Long.class))).thenAnswer(i -> TenantContext.requireId() == 5L ? 2L : 1L);
        when(jdbcTemplate.queryForObject(contains("FROM users"), eq(Long.class))).thenAnswer(i -> TenantContext.requireId() == 5L ? 5L : 1L);
        when(jdbcTemplate.queryForObject(contains("FROM bills"), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("SUM(net_amount)"), eq(BigDecimal.class))).thenReturn(BigDecimal.ZERO);

        List<CafeSummaryDto> list = service.getAllCafes();

        assertEquals(2L, list.get(0).getOutletsCount());
        assertEquals(5L, list.get(0).getUsersCount());
        assertEquals(1L, list.get(1).getOutletsCount(), "the second cafe must not show the first cafe's outlets");
        assertEquals(1L, list.get(1).getUsersCount(), "the second cafe must not show the first cafe's users");
        assertEquals(6L, ((Number) service.getPlatformAnalytics().get("totalUsers")).longValue());
    }

    @Test
    @DisplayName("One broken cafe database does not break the cafe list")
    void aBrokenCafeIsListedWithZeroMetrics() {
        when(registry.findAll()).thenReturn(List.of(cafe(5, "bluedoor", "ACTIVE"), cafe(6, "greenleaf", "ACTIVE")));
        when(jdbcTemplate.queryForObject(contains("FROM outlets"), eq(Long.class))).thenThrow(new RuntimeException("db down")).thenReturn(2L);

        List<CafeSummaryDto> list = service.getAllCafes();

        assertEquals(2, list.size());
        assertEquals(0L, list.get(0).getOutletsCount());
    }

    @Test
    void onlyActiveAndSuspendedAreSettableAndProvisioningCafesAreProtected() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        when(registry.findById(9L)).thenReturn(Optional.of(cafe(9, "half", "PROVISIONING")));

        assertThrows(IllegalArgumentException.class, () -> service.updateCafe(5L, Map.of("status", "DELETED"), "root"));
        assertThrows(IllegalArgumentException.class, () -> service.updateCafe(5L, Map.of("maxOutlets", 0), "root"));
        assertThrows(IllegalArgumentException.class, () -> service.updateCafe(9L, Map.of("status", "ACTIVE"), "root"));
    }

    @Test
    void suspendingIsAuditedAndMirroredIntoTheCafeDatabase() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));

        service.updateCafe(5L, Map.of("status", "SUSPENDED", "maxOutlets", 9), "root");

        verify(registry).updateProfile(eq(5L), anyString(), any(), any(), any(), eq("PRO"), eq(9));
        verify(registry).updateStatus(5L, "SUSPENDED", null);
        verify(registry).audit(5L, "STATUS_SUSPENDED", "root", "was ACTIVE");
        verify(jdbcTemplate).update(contains("UPDATE tenants"), any(Object[].class));
    }

    @Test
    void revealingDatabaseCredentialsReturnsThePlaintextAndLeavesAnAuditTrail() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));

        Map<String, Object> creds = service.revealDbCredentials(5L, "root");

        assertEquals("db-secret-bluedoor", creds.get("password"));
        assertEquals("arvi_bluedoor", creds.get("database"));
        assertEquals("arvi_bluedoor_app", creds.get("username"));
        verify(registry).audit(eq(5L), eq("DB_CREDENTIALS_REVEALED"), eq("root"), anyString());
    }

    @Test
    void rotatingThePasswordChangesPostgresStoresItEncryptedAndRebuildsThePool() throws Exception {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        when(dbAdmin.generatePassword()).thenReturn("N".repeat(32));

        service.rotateDbPassword(5L, "root");

        org.mockito.InOrder order = inOrder(dbAdmin, registry, routing);
        order.verify(dbAdmin).changePassword("arvi_bluedoor_app", "N".repeat(32));
        org.mockito.ArgumentCaptor<String> stored = org.mockito.ArgumentCaptor.forClass(String.class);
        order.verify(registry).updateDbPassword(eq(5L), stored.capture());
        order.verify(routing).evict(5L);
        assertEquals("N".repeat(32), crypto.decrypt(stored.getValue(), TenantCrypto.purpose("bluedoor", "db-password")));
    }

    @Test
    void ifPostgresRejectsTheNewPasswordTheStoredOneIsLeftAlone() throws Exception {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        when(dbAdmin.generatePassword()).thenReturn("N".repeat(32));
        doThrow(new java.sql.SQLException("no")).when(dbAdmin).changePassword(anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> service.rotateDbPassword(5L, "root"));

        verify(registry, never()).updateDbPassword(anyLong(), anyString());
        verify(routing, never()).evict(anyLong());
    }

    @Test
    void bridgeTokensCanBeRevealedAndRotatedPerCafe() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        when(dbAdmin.generateToken()).thenReturn("T".repeat(40));

        assertEquals("bridge-bluedoor", service.revealBridgeToken(5L, "root").get("bridgeToken"));
        assertEquals("T".repeat(40), service.rotateBridgeToken(5L, "root").get("bridgeToken"));
        verify(registry).updateBridgeToken(eq(5L), startsWith("v1:"));
    }

    @Test
    void unknownCafeIsReportedAsNotFound() {
        when(registry.findById(404L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.getCafe(404L));
    }

    @Test
    void movingToAnotherPlanBringsThatPlansBranchCount() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));   // PRO, 5 branches

        service.updateCafe(5L, Map.of("subscriptionPlan", "starter"), "root");
        verify(registry).updateProfile(eq(5L), anyString(), any(), any(), any(), eq("STARTER"), eq(1));

        service.updateCafe(5L, Map.of("subscriptionPlan", "ENTERPRISE"), "root");
        verify(registry).updateProfile(eq(5L), anyString(), any(), any(), any(), eq("ENTERPRISE"), eq(com.neram.pos.plan.Plan.UNLIMITED));
    }

    @Test
    void aBranchCountNamedWithThePlanChangeIsKept() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        service.updateCafe(5L, Map.of("subscriptionPlan", "STARTER", "maxOutlets", 2), "root");
        verify(registry).updateProfile(eq(5L), anyString(), any(), any(), any(), eq("STARTER"), eq(2));
    }

    @Test
    void savingWithoutChangingThePlanLeavesTheBranchCountAlone() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        service.updateCafe(5L, Map.of("name", "Renamed", "subscriptionPlan", "PRO"), "root");
        verify(registry).updateProfile(eq(5L), eq("Renamed"), any(), any(), any(), eq("PRO"), eq(5));
    }

    @Test
    void aPlanThatDoesNotExistIsRefusedAndNothingIsSaved() {
        when(registry.findById(5L)).thenReturn(Optional.of(cafe(5, "bluedoor", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> service.updateCafe(5L, Map.of("subscriptionPlan", "GOLD"), "root"));
        verify(registry, never()).updateProfile(anyLong(), any(), any(), any(), any(), any(), anyInt());
    }
}
