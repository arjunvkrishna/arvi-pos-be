package com.neram.pos.service;

import com.neram.pos.dto.CreateUserRequestDTO;
import com.neram.pos.dto.UpdateProfileRequestDTO;
import com.neram.pos.dto.UpdateUserPermissionsRequestDTO;
import com.neram.pos.dto.UserDTO;
import com.neram.pos.dto.UserProfileDTO;
import com.neram.pos.entity.Employee;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceRbacAndProfileTest {

    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private OutletRepository outletRepository;
    @Mock private TenantSecurityService tenantSecurityService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        when(tenantSecurityService.resolveTenantId(any())).thenReturn(1L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) u.setId(99L);
            return u;
        });
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee e = invocation.getArgument(0);
            if (e.getId() == null) e.setId(88L);
            return e;
        });
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "hashed_" + invocation.getArgument(0));
        when(passwordEncoder.matches(any(), any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            String hash = invocation.getArgument(1);
            return ("hashed_" + raw).equals(hash);
        });
    }

    @Test
    @DisplayName("Creating an Outlet Branch Manager fails if outlet assignment is missing")
    void testCreateOutletBranchManager_RequiresOutletAssignment() {
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("branch_mgr");
        dto.setPassword("Secret@12345");
        dto.setRole("MANAGER");
        dto.setOutletId(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(dto, "ADMIN"));
        assertTrue(ex.getMessage().contains("Outlet assignment is required when creating an Outlet Branch Manager"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Creating an Outlet Branch Manager with outlet assignment succeeds and sets elevated permissions")
    void testCreateOutletBranchManager_Success() {
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("branch_mgr");
        dto.setPassword("Secret@12345");
        dto.setRole("MANAGER");
        dto.setOutletId(5L);

        UserDTO created = userService.createUser(dto, "ADMIN");
        assertNotNull(created);
        assertEquals("branch_mgr", created.getUsername());
        assertEquals("MANAGER", created.getRole());
        assertEquals(5L, created.getOutletId());
        assertTrue(created.getCanTakeOrders());
        assertTrue(created.getCanManageInventory());
        assertTrue(created.getCanCancelOrder(), "a branch manager can always cancel orders");
    }

    @Test
    @DisplayName("Outlet Branch Manager cannot create Admin, General Manager, or Manager accounts")
    void testOutletManagerCannotCreateElevatedRoles() {
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("sub_mgr");
        dto.setPassword("Secret@12345");
        dto.setRole("MANAGER");
        dto.setOutletId(5L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.createUser(dto, "MANAGER"));
        assertTrue(ex.getMessage().contains("Access denied: Outlet Managers cannot create Manager or Admin accounts"));
    }

    @Test
    @DisplayName("General Manager can create branch Managers across multiple outlets")
    void testGeneralManagerCanCreateBranchManager() {
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("branch_mgr2");
        dto.setPassword("Secret@12345");
        dto.setRole("MANAGER");
        dto.setOutletId(7L);

        UserDTO created = userService.createUser(dto, "GENERAL_MANAGER");
        assertNotNull(created);
        assertEquals("MANAGER", created.getRole());
        assertEquals(7L, created.getOutletId());
    }

    @Test
    @DisplayName("General Manager cannot create Admin or Super Admin accounts")
    void testGeneralManagerCannotCreateAdmin() {
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("admin_new");
        dto.setPassword("Secret@12345");
        dto.setRole("ADMIN");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.createUser(dto, "GENERAL_MANAGER"));
        assertTrue(ex.getMessage().contains("Access denied: General Managers cannot create Admin accounts"));
    }

    @Test
    @DisplayName("Outlet Manager creating staff automatically assigns them to the manager's assigned outlet")
    void testOutletManagerCreatesStaff_EnforcesAssignedOutlet() {
        User managerUser = new User();
        managerUser.setId(10L);
        managerUser.setUsername("mgr1");
        managerUser.setRole(User.Role.MANAGER);
        managerUser.setOutletId(3L);
        when(tenantSecurityService.getCurrentUser()).thenReturn(Optional.of(managerUser));

        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("staff1");
        dto.setPassword("Secret@12345");
        dto.setRole("EMPLOYEE");
        dto.setOutletId(99L); // Manager attempts to set different outlet

        UserDTO created = userService.createUser(dto, "MANAGER");
        assertNotNull(created);
        assertEquals(3L, created.getOutletId(), "Must be bound to manager's outlet 3");
    }

    @Test
    @DisplayName("Creating staff with custom granular RBAC flags stores exact permissions")
    void testCreateStaffWithGranularRBAC() {
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("waiter_john");
        dto.setPassword("Secret@12345");
        dto.setRole("EMPLOYEE");
        dto.setCanTakeOrders(true);
        dto.setCanManageInventory(false);
        dto.setCanCancelOrder(false);

        UserDTO created = userService.createUser(dto, "ADMIN");
        assertNotNull(created);
        assertTrue(created.getCanTakeOrders());
        assertFalse(created.getCanManageInventory());
        assertFalse(created.getCanCancelOrder(), "taking orders does not include cancelling them");
    }

    @Test
    @DisplayName("Staff can be given the cancel-order permission at creation")
    void testCreateStaffWhoMayCancelOrders() {
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("waiter_cancel");
        dto.setPassword("Secret@12345");
        dto.setRole("EMPLOYEE");
        dto.setCanTakeOrders(true);
        dto.setCanCancelOrder(true);

        assertTrue(userService.createUser(dto, "ADMIN").getCanCancelOrder());
    }

    @Test
    @DisplayName("Cashier and Captain default privileges are automatically applied")
    void testCashierAndCaptainDefaultPrivileges() {
        CreateUserRequestDTO cashierReq = new CreateUserRequestDTO();
        cashierReq.setUsername("cashier1");
        cashierReq.setPassword("Secret@12345");
        cashierReq.setRole("CASHIER");

        UserDTO cashier = userService.createUser(cashierReq, "ADMIN");
        assertTrue(cashier.getCanTakeOrders());
        assertFalse(cashier.getCanCancelOrder(), "cancelling is granted per person, not by role");

        CreateUserRequestDTO captainReq = new CreateUserRequestDTO();
        captainReq.setUsername("captain1");
        captainReq.setPassword("Secret@12345");
        captainReq.setRole("CAPTAIN");

        UserDTO captain = userService.createUser(captainReq, "ADMIN");
        assertTrue(captain.getCanTakeOrders());
        assertFalse(captain.getCanCancelOrder());
    }

    @Test
    @DisplayName("Update user permissions modifies flags successfully")
    void testUpdateUserPermissions_Success() {
        User existing = new User();
        existing.setId(20L);
        existing.setUsername("staff_member");
        existing.setRole(User.Role.EMPLOYEE);
        existing.setCanCancelOrder(false);
        existing.setCanTakeOrders(false);
        when(userRepository.findById(20L)).thenReturn(Optional.of(existing));

        UpdateUserPermissionsRequestDTO updateReq = new UpdateUserPermissionsRequestDTO();
        updateReq.setCanCancelOrder(true);
        updateReq.setCanTakeOrders(true);
        updateReq.setCanManageInventory(true);

        UserDTO updated = userService.updateUserPermissions(20L, updateReq, "ADMIN");
        assertTrue(updated.getCanCancelOrder());
        assertTrue(updated.getCanTakeOrders());
        assertTrue(updated.getCanManageInventory());
    }

    @Test
    @DisplayName("Outlet Manager cannot update permissions of non-staff accounts")
    void testUpdateUserPermissions_OutletManagerRestricted() {
        User managerAcc = new User();
        managerAcc.setId(30L);
        managerAcc.setUsername("other_mgr");
        managerAcc.setRole(User.Role.MANAGER);
        when(userRepository.findById(30L)).thenReturn(Optional.of(managerAcc));

        UpdateUserPermissionsRequestDTO updateReq = new UpdateUserPermissionsRequestDTO();
        updateReq.setCanCancelOrder(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateUserPermissions(30L, updateReq, "MANAGER"));
        assertTrue(ex.getMessage().contains("Outlet Managers can only manage permissions for staff accounts"));
    }

    @Test
    @DisplayName("Change own password successfully verifies current password, hashes new password, and revokes tokens")
    void testChangeOwnPassword_Success() {
        User user = new User();
        user.setId(50L);
        user.setUsername("testuser");
        user.setPasswordHash("hashed_OldSecret@123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        com.neram.pos.tenancy.TenantContext.set(new com.neram.pos.tenancy.TenantInfo(5, "bluedoor"));
        when(refreshTokenService.issue(user)).thenReturn("RAW");
        String refreshToken;
        try {
            refreshToken = userService.changeOwnPassword("testuser", "OldSecret@123", "NewSecret@987");
        } finally {
            com.neram.pos.tenancy.TenantContext.clear();
        }

        assertEquals("bluedoor~RAW", refreshToken, "the refresh token must name its cafe");
        assertEquals("hashed_NewSecret@987", user.getPasswordHash());
        assertFalse(user.getMustChangePassword());
        verify(refreshTokenService).revokeAllForUser(50L);
    }

    @Test
    @DisplayName("Change own password fails when current password does not match")
    void testChangeOwnPassword_WrongCurrentPassword() {
        User user = new User();
        user.setId(50L);
        user.setUsername("testuser");
        user.setPasswordHash("hashed_OldSecret@123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changeOwnPassword("testuser", "WrongPassword", "NewSecret@987"));
        assertTrue(ex.getMessage().contains("Current password is incorrect"));
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("Change own password fails when new password is identical to current password")
    void testChangeOwnPassword_SamePassword() {
        User user = new User();
        user.setId(50L);
        user.setUsername("testuser");
        user.setPasswordHash("hashed_OldSecret@123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changeOwnPassword("testuser", "OldSecret@123", "OldSecret@123"));
        assertTrue(ex.getMessage().contains("New password cannot be the same as your current password"));
    }

    @Test
    @DisplayName("Get my profile returns employee information, role, and permissions")
    void testGetMyProfile_ReturnsCorrectDetails() {
        Employee emp = new Employee();
        emp.setId(101L);
        emp.setName("Arjun Kumar");
        emp.setEmail("arjun@cafe.internal");
        emp.setPhone("+91 9988776655");
        emp.setDesignation("General Manager");
        emp.setJoinedDate(LocalDate.of(2025, 1, 15));
        emp.setBankName("HDFC Bank");
        emp.setBankAccountNumber("123456789012");
        when(employeeRepository.findById(101L)).thenReturn(Optional.of(emp));

        User user = new User();
        user.setId(5L);
        user.setUsername("gm_arjun");
        user.setRole(User.Role.GENERAL_MANAGER);
        user.setEmployeeId(101L);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        when(userRepository.findByUsername("gm_arjun")).thenReturn(Optional.of(user));

        UserProfileDTO profile = userService.getMyProfile("gm_arjun");
        assertNotNull(profile);
        assertEquals("gm_arjun", profile.getUsername());
        assertEquals("GENERAL_MANAGER", profile.getRole());
        assertEquals("Arjun Kumar", profile.getEmployeeName());
        assertEquals("arjun@cafe.internal", profile.getEmail());
        assertEquals("HDFC Bank", profile.getBankName());
        assertTrue(profile.getCanTakeOrders());
        assertTrue(profile.getCanManageInventory());
        assertTrue(profile.getCanCancelOrder());
    }

    @Test
    @DisplayName("Update my profile saves personal employee information")
    void testUpdateMyProfile_Success() {
        Employee emp = new Employee();
        emp.setId(102L);
        emp.setName("Old Name");
        emp.setPhone("1111111111");
        emp.setEmail("old@email.com");
        emp.setAddress("Old Address");
        when(employeeRepository.findById(102L)).thenReturn(Optional.of(emp));

        User user = new User();
        user.setId(6L);
        user.setUsername("staff_update");
        user.setRole(User.Role.EMPLOYEE);
        user.setEmployeeId(102L);
        user.setActive(true);
        when(userRepository.findByUsername("staff_update")).thenReturn(Optional.of(user));

        UpdateProfileRequestDTO updateDto = new UpdateProfileRequestDTO();
        updateDto.setName("New Employee Name");
        updateDto.setPhone("+91 9876543210");
        updateDto.setEmail("new@cafe.internal");
        updateDto.setAddress("123 Coffee Lane, Central");

        UserProfileDTO updated = userService.updateMyProfile("staff_update", updateDto);
        assertNotNull(updated);
        assertEquals("New Employee Name", emp.getName());
        assertEquals("+91 9876543210", emp.getPhone());
        assertEquals("new@cafe.internal", emp.getEmail());
        assertEquals("123 Coffee Lane, Central", emp.getAddress());
        verify(employeeRepository).saveAndFlush(emp);
    }
}
