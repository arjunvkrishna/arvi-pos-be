package com.neram.pos.tenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import com.neram.pos.util.PasswordValidator;

/**
 * The SaaS owner accounts. They live in the control database, outside every cafe, and can manage cafes but cannot
 * open any cafe's business data.
 */
@Slf4j
@Service
public class PlatformAdminService {

    public static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    public record PlatformAdmin(long id, String username, String passwordHash, String fullName, boolean active,
                                boolean mustChangePassword) {
    }

    private final JdbcTemplate jdbc;
    // Own encoder (same BCrypt as the app's): injecting the security config's bean would make a cycle with the JWT filter.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String initialPassword;
    private final SecureRandom random = new SecureRandom();

    public PlatformAdminService(@Qualifier("controlDataSource") DataSource controlDataSource,
                                @Value("${neram.platform.initial-admin-password:}") String initialPassword) {
        this.jdbc = new JdbcTemplate(controlDataSource);
        this.initialPassword = initialPassword;
    }

    public Optional<PlatformAdmin> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        List<PlatformAdmin> rows = jdbc.query(
                "SELECT id, username, password_hash, full_name, active, must_change_password "
                        + "FROM platform_admins WHERE username = ?",
                (rs, i) -> new PlatformAdmin(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("full_name"), rs.getBoolean("active"),
                        rs.getBoolean("must_change_password")),
                username);
        return rows.stream().findFirst();
    }

    /** Returns the account when the credentials are valid and the account is active. */
    public Optional<PlatformAdmin> authenticate(String username, String rawPassword) {
        Optional<PlatformAdmin> admin = findByUsername(username);
        // Compare against a hash even when the user is unknown, so response time does not reveal valid usernames.
        String hash = admin.map(PlatformAdmin::passwordHash).orElse(DUMMY_HASH);
        boolean ok = rawPassword != null && passwordEncoder.matches(rawPassword, hash);
        return admin.filter(a -> ok && a.active());
    }

    public UserDetails toUserDetails(PlatformAdmin admin) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(SUPER_ADMIN_ROLE));
        return new org.springframework.security.core.userdetails.User(
                admin.username(), admin.passwordHash(), admin.active(), true, true, true, authorities);
    }

    public void changePassword(String username, String currentPassword, String newPassword) {
        PlatformAdmin admin = findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Unknown account"));
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, admin.passwordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        PasswordValidator.validate(newPassword);
        jdbc.update("UPDATE platform_admins SET password_hash = ?, must_change_password = FALSE, updated_at = now() "
                + "WHERE id = ?", passwordEncoder.encode(newPassword), admin.id());
    }

    /**
     * Creates the first platform admin when none exists. The password comes from PLATFORM_ADMIN_INITIAL_PASSWORD
     * if set; otherwise a random one is generated and printed once in the startup log.
     */
    public void bootstrapIfMissing() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM platform_admins", Long.class);
        if (count != null && count > 0) {
            return;
        }
        boolean generated = initialPassword == null || initialPassword.isBlank();
        String password = generated ? generatePassword() : initialPassword;
        if (!generated) {
            PasswordValidator.validate(password);
        }
        jdbc.update("INSERT INTO platform_admins (username, password_hash, full_name, must_change_password) "
                        + "VALUES ('superadmin', ?, 'SaaS Super Admin', ?)",
                passwordEncoder.encode(password), generated);
        if (generated) {
            log.warn("\n"
                    + "========================================================================================\n"
                    + "[BOOTSTRAP] INITIAL SAAS SUPER ADMIN ACCOUNT CREATED:\n"
                    + "  Username: superadmin\n"
                    + "  Password: " + password + "\n"
                    + "  Role: SUPER_ADMIN (Platform SaaS Owner). Change this password after first login.\n"
                    + "========================================================================================");
        } else {
            log.info("Platform super admin 'superadmin' created from PLATFORM_ADMIN_INITIAL_PASSWORD");
        }
    }

    private String generatePassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnopqrstuvwxyz";
        final String digits = "23456789";
        final String special = "!@#$%^&*";
        final String all = upper + lower + digits + special;
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
            password = sb.toString();
        } while (!PasswordValidator.isValid(password));
        return password;
    }

    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
}
