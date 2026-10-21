package com.neram.pos.config;

import com.neram.pos.entity.AppSetting;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.AppSettingRepository;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    public static final String SETTING_SEED_COMPLETED = "system.seed_completed";
    public static final String LEGACY_DEFAULT_ADMIN_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppSettingRepository appSettingRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${SEED_DEMO_DATA:${SEED_TEST_DATA:}}")
    private String seedDemoDataRaw;

    @Value("${SEED_DEMO_USERS:${CREATE_DEFAULT_USERS:false}}")
    private String seedDemoUsersRaw;

    private boolean isSeedDemoData() {
        if (seedDemoDataRaw != null && !seedDemoDataRaw.trim().isEmpty()) {
            return "true".equalsIgnoreCase(seedDemoDataRaw) || "1".equals(seedDemoDataRaw);
        }
        return "true".equalsIgnoreCase(seedDemoUsersRaw) || "1".equals(seedDemoUsersRaw);
    }

    @Override
    public void run(String... args) {
        // 1. Initial super admin and cafe admin bootstrap:
        bootstrapSuperAdminOnFirstInitializationIfMissing();
        bootstrapAdminOnFirstInitializationIfMissing();

        // 2. Marker check: If marker row says it has run, NEVER create, update, or reactivate anything.
        boolean alreadyRan = appSettingRepository.findByKey(SETTING_SEED_COMPLETED)
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false);

        if (alreadyRan) {
            log.info("Database seed marker '{}' is present. Skipping all seeding (no create, update, or reactivate).", SETTING_SEED_COMPLETED);
            return;
        }

        // 3. For an existing database where users already exist prior to this marker:
        // Record marker and exit immediately so existing deletions/deactivations are never overwritten.
        if (userRepository.count() > 1) {
            log.info("Existing database with active/deactivated users detected. Recording seed marker and skipping seeding.");
            markSeedCompleted();
            return;
        }

        // 4. First boot on a fresh database. The admin was already bootstrapped above.
        log.info("First boot on clean database. Admin account is ready.");

        // 5. Demo business data & demo accounts (controlled by SEED_DEMO_DATA / SEED_TEST_DATA, default false for prod)
        if (isSeedDemoData()) {
            log.info("SEED_DEMO_DATA=true: Bootstrapping demo business data (categories, menu items, inventory, suppliers)...");
            seedDemoBusinessData();

            log.info("SEED_DEMO_DATA=true: Bootstrapping demo accounts...");
            seedDemoUserIfNotExists("manager", "manager123", User.Role.MANAGER, "Cafe Manager", "Cafe Manager", 25000.0, true, true);
            seedDemoUserIfNotExists("employee", "emp123", User.Role.EMPLOYEE, "Staff Member", "Staff", 12000.0, true, false);
            seedDemoUserIfNotExists("waiter", "waiter123", User.Role.EMPLOYEE, "Waiter Ramesh", "Table Waiter", 13000.0, true, false);
            seedDemoUserIfNotExists("inventory_staff", "inv123", User.Role.EMPLOYEE, "Inventory Sunil", "Storekeeper", 14000.0, false, true);
        } else {
            log.info("SEED_DEMO_DATA=false: Clean production mode. No demo business data (menu, suppliers, inventory) or demo accounts seeded.");
        }

        // 6. Save marker so seeding never runs again
        markSeedCompleted();
        log.info("Initial database bootstrapping complete. Marker '{}' recorded.", SETTING_SEED_COMPLETED);
    }

    /**
     * Seeds demo categories, menu items, inventory, and suppliers from SQL script.
     */
    private void seedDemoBusinessData() {
        if (jdbcTemplate == null || jdbcTemplate.getDataSource() == null) {
            log.warn("JdbcTemplate or DataSource not available; skipping demo business data execution.");
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource("db/seed/demo_business_data.sql");
            if (!resource.exists()) {
                log.warn("Demo business data script not found at classpath:db/seed/demo_business_data.sql");
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
            populator.setContinueOnError(true);
            populator.execute(jdbcTemplate.getDataSource());
            log.info("Successfully executed db/seed/demo_business_data.sql (categories, menu items, inventory, suppliers)");
        } catch (Exception e) {
            log.error("Could not seed demo business data: {}", e.getMessage(), e);
        }
    }

    private void bootstrapSuperAdminOnFirstInitializationIfMissing() {
        User superadmin = userRepository.findByUsername("superadmin").orElse(null);
        if (superadmin != null) {
            return;
        }
        String rawPassword = generateSecurePassword();
        Employee emp = new Employee();
        emp.setName("SaaS Super Admin");
        emp.setRole(Employee.Role.ADMIN);
        emp.setDesignation("Platform Super Administrator");
        emp.setBasicSalary(0.0);
        emp.setJoinedDate(LocalDate.now());
        emp.setActive(true);
        emp.setCreatedAt(LocalDateTime.now());
        emp.setUpdatedAt(LocalDateTime.now());
        Employee savedEmp = employeeRepository.save(emp);

        superadmin = new User();
        superadmin.setUsername("superadmin");
        superadmin.setPasswordHash(passwordEncoder.encode(rawPassword));
        superadmin.setRole(User.Role.SUPER_ADMIN);
        superadmin.setEmployeeId(savedEmp.getId());
        superadmin.setCanTakeOrders(true);
        superadmin.setCanManageInventory(true);
        superadmin.setMustChangePassword(false);
        superadmin.setActive(true);
        superadmin.setCreatedAt(LocalDateTime.now());
        superadmin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(superadmin);

        log.warn("\n" +
                "========================================================================================\n" +
                "[BOOTSTRAP] INITIAL SAAS SUPER ADMIN ACCOUNT CREATED:\n" +
                "  Username: superadmin\n" +
                "  Password: " + rawPassword + "\n" +
                "  Role: SUPER_ADMIN (Platform SaaS Owner)\n" +
                "========================================================================================");
    }

    /**
     * Initializes the admin account with a secure random password ONLY on initial database bootstrap
     * (when the admin user does not exist or has no password set).
     * Once configured, the admin password is NEVER regenerated on restarts; it must only be changed
     * by the admin from the UI.
     */
    private void bootstrapAdminOnFirstInitializationIfMissing() {
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin != null) {
            boolean hasPassword = admin.getPasswordHash() != null &&
                    !admin.getPasswordHash().trim().isEmpty() &&
                    !LEGACY_DEFAULT_ADMIN_HASH.equals(admin.getPasswordHash());

            if (hasPassword) {
                log.info("Admin user already configured. Preserving existing password.");
                return;
            }

            // Admin exists but has no password or has legacy default admin123: bootstrap initial password
            String rawPassword = generateSecurePassword();
            admin.setPasswordHash(passwordEncoder.encode(rawPassword));
            admin.setMustChangePassword(true);
            admin.setActive(true);
            userRepository.save(admin);
            logBootstrapBanner(rawPassword);
            return;
        }

        // Fresh database: Admin does not exist at all. Create initial admin account.
        String rawPassword = generateSecurePassword();
        Employee emp = new Employee();
        emp.setName("Admin User");
        emp.setRole(Employee.Role.ADMIN);
        emp.setDesignation("System Administrator");
        emp.setBasicSalary(0.0);
        emp.setJoinedDate(LocalDate.now());
        emp.setActive(true);
        emp.setTenantId(1L);
        emp.setOutletId(1L);
        emp.setCreatedAt(LocalDateTime.now());
        emp.setUpdatedAt(LocalDateTime.now());
        Employee savedEmp = employeeRepository.save(emp);

        admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setRole(User.Role.ADMIN);
        admin.setEmployeeId(savedEmp.getId());
        admin.setTenantId(1L);
        admin.setOutletId(1L);
        admin.setCanTakeOrders(true);
        admin.setCanManageInventory(true);
        admin.setMustChangePassword(true);
        admin.setActive(true);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        logBootstrapBanner(rawPassword);
    }

    private void logBootstrapBanner(String rawPassword) {
        log.warn("\n" +
                "========================================================================================\n" +
                "[BOOTSTRAP] INITIAL ADMIN ACCOUNT CREATED:\n" +
                "  Username: admin\n" +
                "  Password: " + rawPassword + "\n" +
                "  Note: Password change is required on admin's first login.\n" +
                "========================================================================================");
    }

    /**
     * Seeds demo user if not existing.
     * NEVER uses upsert, ON CONFLICT DO UPDATE, or save() on an existing user.
     * If row exists in any state (active or inactive/deactivated), skips it.
     */
    private void seedDemoUserIfNotExists(String username, String rawPassword, User.Role role,
                                         String employeeName, String designation, Double basicSalary,
                                         boolean canTakeOrders, boolean canManageInventory) {
        if (userRepository.findByUsername(username).isPresent()) {
            log.info("User '{}' already exists in database (active or deactivated). Skipping.", username);
            return;
        }

        Employee emp = new Employee();
        emp.setName(employeeName);
        emp.setRole(Employee.Role.valueOf(role.name()));
        emp.setDesignation(designation);
        emp.setBasicSalary(basicSalary);
        emp.setJoinedDate(LocalDate.now());
        emp.setActive(true);
        emp.setCreatedAt(LocalDateTime.now());
        emp.setUpdatedAt(LocalDateTime.now());
        Employee savedEmp = employeeRepository.save(emp);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEmployeeId(savedEmp.getId());
        user.setCanTakeOrders(canTakeOrders);
        user.setCanManageInventory(canManageInventory);
        user.setMustChangePassword(false);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Created demo user account: '{}' with role {}", username, role);
    }

    private void markSeedCompleted() {
        try {
            AppSetting setting = appSettingRepository.findByKey(SETTING_SEED_COMPLETED)
                    .orElse(AppSetting.builder().key(SETTING_SEED_COMPLETED).build());
            setting.setValue("true");
            setting.setUpdatedBy("SYSTEM_SEEDER");
            appSettingRepository.save(setting);
        } catch (Exception e) {
            log.warn("Could not save seed marker to settings: {}", e.getMessage());
        }
    }

    private String generateSecurePassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnopqrstuvwxyz";
        final String digits = "23456789";
        final String special = "!@#$%^&*";
        final String all = upper + lower + digits + special;

        SecureRandom random = new SecureRandom();
        String password;
        do {
            StringBuilder sb = new StringBuilder(16);

            sb.append(upper.charAt(random.nextInt(upper.length())));
            sb.append(lower.charAt(random.nextInt(lower.length())));
            sb.append(digits.charAt(random.nextInt(digits.length())));
            sb.append(special.charAt(random.nextInt(special.length())));

            for (int i = 4; i < 16; i++) {
                sb.append(all.charAt(random.nextInt(all.length())));
            }

            char[] chars = sb.toString().toCharArray();
            for (int i = chars.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                char temp = chars[i];
                chars[i] = chars[j];
                chars[j] = temp;
            }

            password = new String(chars);
        } while (!com.neram.pos.util.PasswordValidator.isValid(password));

        return password;
    }
}
