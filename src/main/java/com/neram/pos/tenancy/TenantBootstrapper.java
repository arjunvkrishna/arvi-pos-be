package com.neram.pos.tenancy;

import com.neram.pos.dto.CreateCafeRequest;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns a freshly migrated, empty cafe database into that cafe: re-keys the baseline rows to the cafe's registry id
 * (so every row carries the real restaurant id), names the cafe, and creates its first admin.
 * Must run with the cafe selected in {@link TenantContext}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBootstrapper {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantProperties props;

    /** The baseline migration seeds tenant/outlet id 1; every other cafe id needs those rows re-keyed. */
    private static final long BASELINE_ID = 1L;

    public void bootstrap(TenantRecord t, CreateCafeRequest req) {
        long id = t.id();
        tx.executeWithoutResult(status -> {
            if (id != BASELINE_ID) {
                rekeyBaseline(id);
            }
            jdbc.update("UPDATE tenants SET name = ?, code = ?, contact_email = ?, contact_phone = ?, address = ?, "
                            + "subscription_plan = ?, max_outlets = ?, status = 'ACTIVE', gst_number = NULL, "
                            + "updated_at = now() WHERE id = ?",
                    t.name(), t.code(), t.contactEmail(), t.contactPhone(), t.address(),
                    t.subscriptionPlan(), t.maxOutlets(), id);
            jdbc.update("UPDATE outlets SET address = ?, phone = ?, gst_number = NULL, updated_at = now() "
                    + "WHERE tenant_id = ?", t.address(), t.contactPhone(), id);
            // The baseline migration ships another business's printer header and GSTIN; a new cafe starts blank.
            jdbc.update("UPDATE printer_settings SET header_title = ?, subheader = '', "
                    + "kot_subheader = ?, juice_kot_subheader = ?", fit(t.name(), 100), fit(t.name(), 100) + " - KITCHEN",
                    fit(t.name(), 100) + " - JUICE BAR");
            jdbc.update("UPDATE gst_settings SET enabled = FALSE, gstin = ''");
            // The payment QR is one shop's UPI code; a new cafe must never print another business's QR on its bills.
            jdbc.update("UPDATE printer_settings SET payment_qr_enabled = FALSE");
        });

        Long outletId = jdbc.queryForObject("SELECT min(id) FROM outlets WHERE tenant_id = ?", Long.class, id);
        createAdmin(id, outletId, t, req);

        if (props.getProvision().isSeedDemoData()) {
            seedDemoData();
        }
    }

    private void rekeyBaseline(long newId) {
        jdbc.update("INSERT INTO tenants (id, name, code, subscription_plan, status, max_outlets, currency, "
                + "currency_symbol) SELECT ?, name || ' ', code || '-new', subscription_plan, status, max_outlets, "
                + "currency, currency_symbol FROM tenants WHERE id = ?", newId, BASELINE_ID);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND column_name = 'tenant_id' AND table_name <> 'tenants'", String.class);
        for (String table : tables) {
            jdbc.update("UPDATE " + TenantDatabaseAdmin.quoteIdent(table) + " SET tenant_id = ? WHERE tenant_id = ?",
                    newId, BASELINE_ID);
        }
        jdbc.update("DELETE FROM tenants WHERE id = ?", BASELINE_ID);
        normalizeTenantIdDefaults(newId);
    }

    /**
     * Makes every tenant_id column default to this cafe's id, so even code that forgets to set it stores the right
     * restaurant id. Safe to re-run after later migrations add tables.
     */
    public void normalizeTenantIdDefaults(long tenantId) {
        setTenantIdDefaults(jdbc, tenantId);
    }

    /** Also used by the migrator, with a connection of its own, after every migration run. */
    static void setTenantIdDefaults(JdbcTemplate jdbc, long tenantId) {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND column_name = 'tenant_id' AND table_name <> 'tenants'", String.class);
        for (String table : tables) {
            jdbc.execute("ALTER TABLE " + TenantDatabaseAdmin.quoteIdent(table)
                    + " ALTER COLUMN tenant_id SET DEFAULT " + tenantId);
        }
    }

    private void createAdmin(long tenantId, Long outletId, TenantRecord t, CreateCafeRequest req) {
        Employee emp = new Employee();
        emp.setName(req.getAdminFullName() != null && !req.getAdminFullName().trim().isEmpty()
                ? req.getAdminFullName().trim() : t.name() + " Admin");
        emp.setEmail(req.getContactEmail());
        emp.setPhone(req.getContactPhone());
        emp.setRole(Employee.Role.ADMIN);
        emp.setDesignation("Cafe Administrator");
        emp.setBasicSalary(0.0);
        emp.setJoinedDate(LocalDate.now());
        emp.setActive(true);
        emp.setTenantId(tenantId);
        emp.setOutletId(outletId);
        emp.setCreatedAt(LocalDateTime.now());
        emp.setUpdatedAt(LocalDateTime.now());
        Employee savedEmp = employeeRepository.save(emp);

        User user = new User();
        user.setUsername(req.getAdminUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(req.getAdminPassword()));
        user.setRole(User.Role.ADMIN);
        user.setEmployeeId(savedEmp.getId());
        user.setTenantId(tenantId);
        user.setOutletId(outletId);
        user.setCanTakeOrders(true);
        user.setCanManageInventory(true);
        user.setMustChangePassword(false);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private void seedDemoData() {
        try {
            ClassPathResource resource = new ClassPathResource("db/seed/demo_business_data.sql");
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
            populator.setContinueOnError(true);
            populator.execute(jdbc.getDataSource());
            log.info("Seeded demo menu/inventory data into the new cafe");
        } catch (Exception e) {
            log.warn("Could not seed demo data: {}", e.getMessage());
        }
    }

    private static String fit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
