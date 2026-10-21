package com.neram.pos.service;

import com.neram.pos.dto.CafeSummaryDto;
import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.Outlet;
import com.neram.pos.entity.Tenant;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.OutletRepository;
import com.neram.pos.repository.TenantRepository;
import com.neram.pos.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuperAdminServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private OutletRepository outletRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SuperAdminService superAdminService;

    @BeforeEach
    void setUp() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            if (t.getId() == null) t.setId(10L);
            return t;
        });
        when(outletRepository.save(any(Outlet.class))).thenAnswer(i -> {
            Outlet o = i.getArgument(0);
            if (o.getId() == null) o.setId(100L);
            return o;
        });
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> {
            Employee e = i.getArgument(0);
            if (e.getId() == null) e.setId(200L);
            return e;
        });
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (u.getId() == null) u.setId(300L);
            return u;
        });
        when(passwordEncoder.encode(any())).thenAnswer(i -> "hash_" + i.getArgument(0));
    }

    @Test
    @DisplayName("Super Admin creates a new cafe tenant with default outlet and cafe admin")
    void testCreateCafe_Success() {
        CreateCafeRequest req = new CreateCafeRequest();
        req.setName("Blueberry Brew");
        req.setCode("blueberry-brew");
        req.setContactEmail("admin@blueberrybrew.com");
        req.setContactPhone("+91 9998887776");
        req.setAddress("Kochi Infopark");
        req.setSubscriptionPlan("ENTERPRISE");
        req.setMaxOutlets(10);
        req.setAdminUsername("blueberry_admin");
        req.setAdminPassword("AdminPass@123");
        req.setAdminFullName("Blueberry Admin Manager");

        CafeSummaryDto result = superAdminService.createCafe(req);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("Blueberry Brew", result.getName());
        assertEquals("blueberry-brew", result.getCode());
        assertEquals("admin@blueberrybrew.com", result.getContactEmail());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(10, result.getMaxOutlets());

        verify(tenantRepository).save(any(Tenant.class));
        verify(outletRepository).save(any(Outlet.class));
        verify(employeeRepository).save(any(Employee.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Create cafe fails if cafe name is missing")
    void testCreateCafe_MissingNameThrows() {
        CreateCafeRequest req = new CreateCafeRequest();
        req.setName("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> superAdminService.createCafe(req));
        assertTrue(ex.getMessage().contains("Cafe name is required"));
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create cafe fails if admin username is already taken")
    void testCreateCafe_DuplicateAdminUsernameThrows() {
        CreateCafeRequest req = new CreateCafeRequest();
        req.setName("Cafe Mocha");
        req.setAdminUsername("existing_user");
        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> superAdminService.createCafe(req));
        assertTrue(ex.getMessage().contains("already taken"));
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("GetAllCafes aggregates tenant metrics and outlets count")
    void testGetAllCafes_MetricsAggregation() {
        Tenant t1 = Tenant.builder()
                .id(1L)
                .name("Central Cafe")
                .code("central-cafe")
                .status("ACTIVE")
                .maxOutlets(5)
                .createdAt(LocalDateTime.now())
                .build();
        when(tenantRepository.findAll()).thenReturn(List.of(t1));
        when(outletRepository.countByTenantId(1L)).thenReturn(3L);
        when(jdbcTemplate.queryForObject(contains("users"), eq(Long.class), eq(1L))).thenReturn(8L);
        when(jdbcTemplate.queryForObject(contains("bills"), eq(Long.class), eq(1L))).thenReturn(150L);
        when(jdbcTemplate.queryForObject(contains("SUM(net_amount)"), eq(BigDecimal.class), eq(1L))).thenReturn(new BigDecimal("75420.50"));

        List<CafeSummaryDto> cafes = superAdminService.getAllCafes();
        assertNotNull(cafes);
        assertEquals(1, cafes.size());
        CafeSummaryDto summary = cafes.get(0);
        assertEquals("Central Cafe", summary.getName());
        assertEquals(3L, summary.getOutletsCount());
        assertEquals(8L, summary.getUsersCount());
        assertEquals(150L, summary.getTotalBillsCount());
        assertEquals(new BigDecimal("75420.50"), summary.getTotalGmv());
    }
}
