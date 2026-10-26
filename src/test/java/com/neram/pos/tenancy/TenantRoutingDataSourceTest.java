package com.neram.pos.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantRoutingDataSourceTest {

    private final TenantRegistry registry = mock(TenantRegistry.class);

    private TenantRoutingDataSource routing() {
        TenantProperties props = new TenantProperties();
        props.setSecretKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new TenantRoutingDataSource(registry, new TenantCrypto(props), props);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void refusesToHandOutAConnectionWhenNoCafeIsSelected() {
        TenantRoutingDataSource ds = routing();
        try {
            assertThrows(TenantNotSelectedException.class, ds::getConnection);
            verifyNoInteractions(registry);
        } finally {
            ds.destroy();
        }
    }

    @Test
    void unknownCafeIsReportedAsUnavailableNotAsAnotherCafesData() {
        TenantRoutingDataSource ds = routing();
        when(registry.findById(99L)).thenReturn(Optional.empty());
        TenantContext.set(new TenantInfo(99, "ghost"));
        try {
            TenantUnavailableException ex = assertThrows(TenantUnavailableException.class, ds::getConnection);
            assertEquals(404, ex.getHttpStatus());
        } finally {
            ds.destroy();
        }
    }

    @Test
    void perCallCredentialsAreNotSupported() {
        TenantRoutingDataSource ds = routing();
        try {
            assertThrows(UnsupportedOperationException.class, () -> ds.getConnection("u", "p"));
        } finally {
            ds.destroy();
        }
    }
}
