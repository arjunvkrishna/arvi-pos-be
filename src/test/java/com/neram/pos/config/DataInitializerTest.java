package com.neram.pos.config;

import com.neram.pos.entity.AppSetting;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.AppSettingRepository;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AppSettingRepository appSettingRepository;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "bcrypt_" + invocation.getArgument(0));
        lenient().when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee e = invocation.getArgument(0);
            e.setId(99L);
            return e;
        });
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(new User()));
        lenient().when(appSettingRepository.save(any(AppSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(appSettingRepository.findByKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void testWhenMarkerPresent_NeverCreateUpdateOrReactivateAnything() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash("already_configured_secure_hash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED))
                .thenReturn(Optional.of(AppSetting.builder().key(DataInitializer.SETTING_SEED_COMPLETED).value("true").build()));

        dataInitializer.run();

        // Must never touch or call save on any repository
        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
        verify(appSettingRepository, never()).save(any());
    }

    @Test
    void testWhenAdminHasLegacyDefaultPassword_EradicatesAndForcesPasswordChange() {
        User legacyAdmin = new User();
        legacyAdmin.setId(1L);
        legacyAdmin.setUsername("admin");
        legacyAdmin.setPasswordHash(DataInitializer.LEGACY_DEFAULT_ADMIN_HASH);
        legacyAdmin.setMustChangePassword(false);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(legacyAdmin));
        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED))
                .thenReturn(Optional.of(AppSetting.builder().key(DataInitializer.SETTING_SEED_COMPLETED).value("true").build()));

        dataInitializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User updated = captor.getValue();
        assertThat(updated.getUsername()).isEqualTo("admin");
        assertThat(updated.getMustChangePassword()).isTrue();
        assertThat(updated.getPasswordHash()).startsWith("bcrypt_");
        String plain = updated.getPasswordHash().replace("bcrypt_", "");
        assertThat(plain.length()).isEqualTo(16);
    }

    @Test
    void testFirstBootWithNoAdmin_BootstrapsAdminWithSecureRandomAndForcesPasswordChange() {
        ReflectionTestUtils.setField(dataInitializer, "seedDemoUsersRaw", "false");

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED)).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        dataInitializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User admin = userCaptor.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(admin.getActive()).isTrue();
        // Force password change on admin first login
        assertThat(admin.getMustChangePassword()).isTrue();
        // Bcrypt hash stored
        assertThat(admin.getPasswordHash()).startsWith("bcrypt_");
        String plainPassword = admin.getPasswordHash().replace("bcrypt_", "");
        assertThat(plainPassword.length()).isEqualTo(16);

        // Marker must be saved to settings table
        verify(appSettingRepository, times(1)).save(argThat(s ->
                DataInitializer.SETTING_SEED_COMPLETED.equals(s.getKey()) &&
                "true".equals(s.getValue())
        ));
    }

    @Test
    void testFirstBootDefault_SeedDemoUsersIsFalse_OnlyAdminCreated() {
        ReflectionTestUtils.setField(dataInitializer, "seedDemoUsersRaw", "false");

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED)).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        dataInitializer.run();

        // Exactly 1 user saved (admin)
        verify(userRepository, times(1)).save(any());
        verify(userRepository, never()).findByUsername("manager");
        verify(userRepository, never()).findByUsername("employee");
    }

    @Test
    void testFirstBoot_SeedDemoUsersIsTrue_SeedsDemoAccountsWithoutOverwritingExisting() {
        ReflectionTestUtils.setField(dataInitializer, "seedDemoUsersRaw", "true");

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED)).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        when(userRepository.findByUsername("manager")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("employee")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("waiter")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("inventory_staff")).thenReturn(Optional.empty());

        dataInitializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(5)).save(captor.capture());

        List<String> usernames = captor.getAllValues().stream().map(User::getUsername).toList();
        assertThat(usernames).containsExactly("admin", "manager", "employee", "waiter", "inventory_staff");
    }

    @Test
    void testRespectDeletesAndDeactivation_NeverSavesOrReactivatesExistingUser() {
        ReflectionTestUtils.setField(dataInitializer, "seedDemoUsersRaw", "true");

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED)).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        // Suppose manager already exists in DB but is DEACTIVATED (active=false)
        User deactivatedManager = new User();
        deactivatedManager.setId(2L);
        deactivatedManager.setUsername("manager");
        deactivatedManager.setActive(false);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(deactivatedManager));

        when(userRepository.findByUsername("employee")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("waiter")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("inventory_staff")).thenReturn(Optional.empty());

        dataInitializer.run();

        // Never call save() on deactivatedManager, never reactivate it
        assertThat(deactivatedManager.getActive()).isFalse();
        verify(userRepository, never()).save(deactivatedManager);
    }

    @Test
    void testExistingDatabaseWithUsers_MarksMarkerAndExitsImmediately() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash("already_configured_secure_hash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED)).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(3L); // 3 existing users

        dataInitializer.run();

        // Must record marker and not create any users
        verify(appSettingRepository, times(1)).save(argThat(s ->
                DataInitializer.SETTING_SEED_COMPLETED.equals(s.getKey()) &&
                "true".equals(s.getValue())
        ));
        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void testWhenAdminAlreadyConfigured_PreservesPasswordAcrossRestarts() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash("$2a$10$UserChangedThisPasswordInUI");
        admin.setMustChangePassword(false);
        admin.setActive(true);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED))
                .thenReturn(Optional.of(AppSetting.builder().key(DataInitializer.SETTING_SEED_COMPLETED).value("true").build()));

        dataInitializer.run();

        // Must preserve existing password and never overwrite
        verify(userRepository, never()).save(any());
        assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$UserChangedThisPasswordInUI");
        assertThat(admin.getMustChangePassword()).isFalse();
    }

    @Test
    void testFirstBoot_SeedDemoDataIsTrue_SeedsDemoAccounts() {
        ReflectionTestUtils.setField(dataInitializer, "seedDemoDataRaw", "true");

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED)).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        when(userRepository.findByUsername("manager")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("employee")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("waiter")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("inventory_staff")).thenReturn(Optional.empty());

        dataInitializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(5)).save(captor.capture());

        List<String> usernames = captor.getAllValues().stream().map(User::getUsername).toList();
        assertThat(usernames).containsExactly("admin", "manager", "employee", "waiter", "inventory_staff");
    }

    @Test
    void testFirstBoot_SeedDemoDataIsFalse_OnlyAdminCreated_CleanProdMode() {
        ReflectionTestUtils.setField(dataInitializer, "seedDemoDataRaw", "false");

        when(appSettingRepository.findByKey(DataInitializer.SETTING_SEED_COMPLETED)).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        dataInitializer.run();

        // Exactly 1 user saved (admin)
        verify(userRepository, times(1)).save(any());
        verify(userRepository, never()).findByUsername("manager");
        verify(userRepository, never()).findByUsername("employee");
    }
}
