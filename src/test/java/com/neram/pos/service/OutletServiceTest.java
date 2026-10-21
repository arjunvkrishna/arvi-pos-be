package com.neram.pos.service;

import com.neram.pos.entity.Outlet;
import com.neram.pos.entity.Tenant;
import com.neram.pos.entity.User;
import com.neram.pos.repository.OutletRepository;
import com.neram.pos.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutletServiceTest {

    @Mock private OutletRepository outletRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantSecurityService tenantSecurityService;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OutletService outletService;

    private Tenant tenant;
    private Outlet outlet1;
    private Outlet outlet2;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder()
                .id(1L)
                .name("Arvi Cafe")
                .maxOutlets(3)
                .build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantSecurityService.resolveTenantId(any())).thenReturn(1L);

        outlet1 = Outlet.builder().id(10L).tenantId(1L).name("Downtown").code("DT-01").isActive(true).build();
        outlet2 = Outlet.builder().id(20L).tenantId(1L).name("Uptown").code("UT-02").isActive(true).build();

        when(outletRepository.findByTenantId(1L)).thenReturn(List.of(outlet1, outlet2));
        when(outletRepository.findById(10L)).thenReturn(Optional.of(outlet1));
        when(outletRepository.findById(20L)).thenReturn(Optional.of(outlet2));
        when(outletRepository.save(any(Outlet.class))).thenAnswer(i -> {
            Outlet o = i.getArgument(0);
            if (o.getId() == null) o.setId(99L);
            return o;
        });
    }

    @Test
    @DisplayName("Outlet Manager only sees their assigned branch outlet")
    void testGetOutlets_OutletManagerRestricted() {
        User manager = new User();
        manager.setRole(User.Role.MANAGER);
        manager.setOutletId(10L);
        when(tenantSecurityService.getCurrentUser()).thenReturn(Optional.of(manager));

        List<Outlet> outlets = outletService.getOutletsForCurrentTenant();
        assertNotNull(outlets);
        assertEquals(1, outlets.size());
        assertEquals("Downtown", outlets.get(0).getName());
    }

    @Test
    @DisplayName("General Manager and Admin see all branch outlets under the brand")
    void testGetOutlets_AdminOrGeneralManagerSeesAll() {
        User gm = new User();
        gm.setRole(User.Role.GENERAL_MANAGER);
        when(tenantSecurityService.getCurrentUser()).thenReturn(Optional.of(gm));

        List<Outlet> outlets = outletService.getOutletsForCurrentTenant();
        assertNotNull(outlets);
        assertEquals(2, outlets.size());
    }

    @Test
    @DisplayName("Create outlet successfully adds branch when under subscription limit")
    void testCreateOutlet_Success() {
        when(outletRepository.countByTenantId(1L)).thenReturn(1L);

        Outlet newBranch = new Outlet();
        newBranch.setName("Airport Kiosk");
        newBranch.setCode("AP-01");

        Outlet created = outletService.createOutlet(newBranch);
        assertNotNull(created);
        assertEquals("Airport Kiosk", created.getName());
        assertEquals("AP-01", created.getCode());
        assertEquals(1L, created.getTenantId());
        assertTrue(created.getIsActive());
        verify(outletRepository).save(any(Outlet.class));
    }

    @Test
    @DisplayName("Create outlet fails when franchise reaches maxOutlets quota")
    void testCreateOutlet_ExceedingLimitThrows() {
        when(outletRepository.countByTenantId(1L)).thenReturn(3L); // maxOutlets is 3

        Outlet newBranch = new Outlet();
        newBranch.setName("Fourth Outlet");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> outletService.createOutlet(newBranch));
        assertTrue(ex.getMessage().contains("Maximum outlet limit (3) reached"));
        verify(outletRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create outlet fails if outlet name is missing")
    void testCreateOutlet_MissingNameThrows() {
        when(outletRepository.countByTenantId(1L)).thenReturn(1L);

        Outlet newBranch = new Outlet();
        newBranch.setName("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> outletService.createOutlet(newBranch));
        assertTrue(ex.getMessage().contains("Outlet name is required"));
    }

    @Test
    @DisplayName("Update outlet saves updated contact and status")
    void testUpdateOutlet_Success() {
        Outlet update = new Outlet();
        update.setName("Downtown Flagship");
        update.setPhone("+91 9123456789");

        Outlet updated = outletService.updateOutlet(10L, update);
        assertNotNull(updated);
        assertEquals("Downtown Flagship", outlet1.getName());
        assertEquals("+91 9123456789", outlet1.getPhone());
        verify(outletRepository).save(outlet1);
    }

    @Test
    @DisplayName("Updating an outlet belonging to another tenant throws SecurityException")
    void testUpdateOutlet_CrossTenantThrows() {
        Outlet foreignOutlet = Outlet.builder().id(50L).tenantId(999L).name("Other Brand").build();
        when(outletRepository.findById(50L)).thenReturn(Optional.of(foreignOutlet));
        when(tenantSecurityService.isSuperAdmin()).thenReturn(false);

        Outlet update = new Outlet();
        update.setName("Hacked Outlet");

        SecurityException ex = assertThrows(SecurityException.class,
                () -> outletService.updateOutlet(50L, update));
        assertTrue(ex.getMessage().contains("Unauthorized to update outlet of another tenant"));
        verify(outletRepository, never()).save(foreignOutlet);
    }
}
