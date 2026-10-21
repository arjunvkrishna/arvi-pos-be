package com.neram.pos.service;

import com.neram.pos.entity.Outlet;
import com.neram.pos.entity.Tenant;
import com.neram.pos.repository.OutletRepository;
import com.neram.pos.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutletService {

    private final OutletRepository outletRepository;
    private final TenantRepository tenantRepository;
    private final TenantSecurityService tenantSecurityService;
    private final JdbcTemplate jdbcTemplate;

    public List<Outlet> getOutletsForCurrentTenant() {
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        return outletRepository.findByTenantId(tenantId);
    }

    public Outlet createOutlet(Outlet outlet) {
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found"));

        long currentCount = outletRepository.countByTenantId(tenantId);
        if (currentCount >= tenant.getMaxOutlets()) {
            throw new IllegalStateException("Maximum outlet limit (" + tenant.getMaxOutlets() + ") reached for your subscription plan. Contact Super Admin to upgrade.");
        }

        if (outlet.getName() == null || outlet.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Outlet name is required");
        }

        String code = outlet.getCode() != null && !outlet.getCode().trim().isEmpty()
                ? outlet.getCode().trim().toUpperCase()
                : "BR-" + (currentCount + 1);

        outlet.setTenantId(tenantId);
        outlet.setName(outlet.getName().trim());
        outlet.setCode(code);
        if (outlet.getIsActive() == null) outlet.setIsActive(true);
        outlet.setCreatedAt(LocalDateTime.now());
        outlet.setUpdatedAt(LocalDateTime.now());
        return outletRepository.save(outlet);
    }

    public Outlet updateOutlet(Long id, Outlet update) {
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        Outlet outlet = outletRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Outlet not found"));

        if (!tenantSecurityService.isSuperAdmin() && !outlet.getTenantId().equals(tenantId)) {
            throw new SecurityException("Unauthorized to update outlet of another tenant");
        }

        if (update.getName() != null) outlet.setName(update.getName().trim());
        if (update.getAddress() != null) outlet.setAddress(update.getAddress().trim());
        if (update.getPhone() != null) outlet.setPhone(update.getPhone().trim());
        if (update.getGstNumber() != null) outlet.setGstNumber(update.getGstNumber().trim());
        if (update.getIsActive() != null) outlet.setIsActive(update.getIsActive());
        outlet.setUpdatedAt(LocalDateTime.now());
        return outletRepository.save(outlet);
    }

    public Map<String, Object> getFranchiseAnalytics() {
        Long tenantId = tenantSecurityService.resolveTenantId(null);
        List<Outlet> outlets = outletRepository.findByTenantId(tenantId);

        List<Map<String, Object>> outletStats = new ArrayList<>();
        BigDecimal totalBrandRevenue = BigDecimal.ZERO;
        long totalBrandOrders = 0;

        for (Outlet o : outlets) {
            long orders = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            try {
                Long oCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM bills WHERE tenant_id = ? AND outlet_id = ? AND status = 'PAID'",
                        Long.class, tenantId, o.getId());
                if (oCount != null) orders = oCount;

                BigDecimal rev = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(net_amount), 0) FROM bills WHERE tenant_id = ? AND outlet_id = ? AND status = 'PAID'",
                        BigDecimal.class, tenantId, o.getId());
                if (rev != null) revenue = rev;
            } catch (Exception e) {
                log.warn("Error getting stats for outlet {}: {}", o.getId(), e.getMessage());
            }

            totalBrandRevenue = totalBrandRevenue.add(revenue);
            totalBrandOrders += orders;

            BigDecimal aov = orders > 0
                    ? revenue.divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> stat = new HashMap<>();
            stat.put("outletId", o.getId());
            stat.put("outletName", o.getName());
            stat.put("outletCode", o.getCode());
            stat.put("isActive", o.getIsActive());
            stat.put("ordersCount", orders);
            stat.put("totalRevenue", revenue);
            stat.put("averageOrderValue", aov);
            outletStats.add(stat);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("tenantId", tenantId);
        response.put("totalOutlets", outlets.size());
        response.put("totalBrandRevenue", totalBrandRevenue);
        response.put("totalBrandOrders", totalBrandOrders);
        response.put("outlets", outletStats);
        return response;
    }
}
