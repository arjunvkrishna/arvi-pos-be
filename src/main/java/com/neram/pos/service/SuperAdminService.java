package com.neram.pos.service;

import com.neram.pos.dto.CafeSummaryDto;
import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.Outlet;
import com.neram.pos.entity.Tenant;
import com.neram.pos.entity.User;
import com.neram.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final TenantRepository tenantRepository;
    private final OutletRepository outletRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public List<CafeSummaryDto> getAllCafes() {
        List<Tenant> tenants = tenantRepository.findAll();
        return tenants.stream().map(tenant -> {
            long outletsCount = outletRepository.countByTenantId(tenant.getId());
            long usersCount = 0;
            long billsCount = 0;
            BigDecimal gmv = BigDecimal.ZERO;
            try {
                Long uCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE tenant_id = ?", Long.class, tenant.getId());
                if (uCount != null) usersCount = uCount;

                Long bCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM bills WHERE tenant_id = ? AND status = 'PAID'", Long.class, tenant.getId());
                if (bCount != null) billsCount = bCount;

                BigDecimal total = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(net_amount), 0) FROM bills WHERE tenant_id = ? AND status = 'PAID'", BigDecimal.class, tenant.getId());
                if (total != null) gmv = total;
            } catch (Exception e) {
                log.warn("Could not fetch metrics for tenant {}: {}", tenant.getId(), e.getMessage());
            }

            return CafeSummaryDto.builder()
                    .id(tenant.getId())
                    .name(tenant.getName())
                    .code(tenant.getCode())
                    .contactEmail(tenant.getContactEmail())
                    .contactPhone(tenant.getContactPhone())
                    .address(tenant.getAddress())
                    .subscriptionPlan(tenant.getSubscriptionPlan())
                    .status(tenant.getStatus())
                    .maxOutlets(tenant.getMaxOutlets())
                    .outletsCount(outletsCount)
                    .usersCount(usersCount)
                    .totalBillsCount(billsCount)
                    .totalGmv(gmv)
                    .createdAt(tenant.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public CafeSummaryDto createCafe(CreateCafeRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Cafe name is required");
        }
        String code = req.getCode() != null && !req.getCode().trim().isEmpty()
                ? req.getCode().trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-")
                : req.getName().trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");

        if (tenantRepository.existsByCode(code)) {
            code = code + "-" + System.currentTimeMillis() % 1000;
        }

        if (userRepository.existsByUsername(req.getAdminUsername())) {
            throw new IllegalArgumentException("Admin username '" + req.getAdminUsername() + "' is already taken");
        }

        Tenant tenant = Tenant.builder()
                .name(req.getName().trim())
                .code(code)
                .contactEmail(req.getContactEmail())
                .contactPhone(req.getContactPhone())
                .address(req.getAddress())
                .subscriptionPlan(req.getSubscriptionPlan() != null ? req.getSubscriptionPlan() : "PRO")
                .status("ACTIVE")
                .maxOutlets(req.getMaxOutlets() != null && req.getMaxOutlets() > 0 ? req.getMaxOutlets() : 5)
                .currency("INR")
                .currencySymbol("₹")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Tenant savedTenant = tenantRepository.save(tenant);

        // Create initial default outlet for this cafe
        Outlet initialOutlet = Outlet.builder()
                .tenantId(savedTenant.getId())
                .name("Main Branch")
                .code("MAIN-01")
                .address(req.getAddress())
                .phone(req.getContactPhone())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Outlet savedOutlet = outletRepository.save(initialOutlet);

        // Create Cafe Admin Employee & User
        Employee emp = new Employee();
        emp.setName(req.getAdminFullName() != null && !req.getAdminFullName().trim().isEmpty()
                ? req.getAdminFullName().trim() : req.getName() + " Admin");
        emp.setEmail(req.getContactEmail());
        emp.setPhone(req.getContactPhone());
        emp.setRole(Employee.Role.ADMIN);
        emp.setDesignation("Cafe Administrator");
        emp.setBasicSalary(0.0);
        emp.setJoinedDate(LocalDate.now());
        emp.setActive(true);
        emp.setTenantId(savedTenant.getId());
        emp.setOutletId(savedOutlet.getId());
        emp.setCreatedAt(LocalDateTime.now());
        emp.setUpdatedAt(LocalDateTime.now());
        Employee savedEmp = employeeRepository.save(emp);

        User user = new User();
        user.setUsername(req.getAdminUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(req.getAdminPassword()));
        user.setRole(User.Role.ADMIN);
        user.setEmployeeId(savedEmp.getId());
        user.setTenantId(savedTenant.getId());
        user.setOutletId(savedOutlet.getId());
        user.setCanTakeOrders(true);
        user.setCanManageInventory(true);
        user.setMustChangePassword(false);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Super Admin created new cafe tenant '{}' (id={}) with admin '{}'",
                savedTenant.getName(), savedTenant.getId(), user.getUsername());

        return CafeSummaryDto.builder()
                .id(savedTenant.getId())
                .name(savedTenant.getName())
                .code(savedTenant.getCode())
                .contactEmail(savedTenant.getContactEmail())
                .contactPhone(savedTenant.getContactPhone())
                .address(savedTenant.getAddress())
                .subscriptionPlan(savedTenant.getSubscriptionPlan())
                .status(savedTenant.getStatus())
                .maxOutlets(savedTenant.getMaxOutlets())
                .outletsCount(1L)
                .usersCount(1L)
                .totalBillsCount(0L)
                .totalGmv(BigDecimal.ZERO)
                .createdAt(savedTenant.getCreatedAt())
                .build();
    }

    public Tenant updateCafeStatus(Long id, Map<String, Object> updates) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cafe not found with id: " + id));

        if (updates.containsKey("status")) {
            tenant.setStatus(String.valueOf(updates.get("status")));
        }
        if (updates.containsKey("subscriptionPlan")) {
            tenant.setSubscriptionPlan(String.valueOf(updates.get("subscriptionPlan")));
        }
        if (updates.containsKey("maxOutlets")) {
            tenant.setMaxOutlets(Integer.parseInt(String.valueOf(updates.get("maxOutlets"))));
        }
        if (updates.containsKey("name")) {
            tenant.setName(String.valueOf(updates.get("name")));
        }
        if (updates.containsKey("contactEmail")) {
            tenant.setContactEmail(String.valueOf(updates.get("contactEmail")));
        }
        if (updates.containsKey("contactPhone")) {
            tenant.setContactPhone(String.valueOf(updates.get("contactPhone")));
        }
        tenant.setUpdatedAt(LocalDateTime.now());
        return tenantRepository.save(tenant);
    }

    public Map<String, Object> getPlatformAnalytics() {
        long totalCafes = tenantRepository.count();
        long totalOutlets = outletRepository.count();
        long totalUsers = userRepository.count();
        long totalOrders = 0;
        BigDecimal totalGmv = BigDecimal.ZERO;

        try {
            Long bCount = jdbcTemplate.queryForObject("SELECT count(*) FROM bills WHERE status = 'PAID'", Long.class);
            if (bCount != null) totalOrders = bCount;
            BigDecimal gmv = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(net_amount), 0) FROM bills WHERE status = 'PAID'", BigDecimal.class);
            if (gmv != null) totalGmv = gmv;
        } catch (Exception e) {
            log.warn("Error calculating platform analytics: {}", e.getMessage());
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCafes", totalCafes);
        stats.put("totalOutlets", totalOutlets);
        stats.put("totalUsers", totalUsers);
        stats.put("totalOrders", totalOrders);
        stats.put("totalGmv", totalGmv);
        stats.put("activeCafes", totalCafes);
        return stats;
    }
}
