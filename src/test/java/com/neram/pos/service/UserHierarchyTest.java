package com.neram.pos.service;

import com.neram.pos.dto.CreateUserRequestDTO;
import com.neram.pos.dto.UpdateUserPermissionsRequestDTO;
import com.neram.pos.dto.UserDTO;
import com.neram.pos.entity.User;
import com.neram.pos.entity.User.Role;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.OutletRepository;
import com.neram.pos.repository.TenantRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Who may see and manage whom: the chain of command and branch walls in the user service. */
class UserHierarchyTest {

    private UserRepository users;
    private TenantSecurityService security;
    private UserService service;

    private User owner, gm, mgr1, mgr2, cashier1, cashier2;

    private static User user(long id, String name, Role role, Long outlet) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setRole(role);
        u.setOutletId(outlet);
        u.setActive(true);
        u.setPasswordHash("h");
        return u;
    }

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        security = mock(TenantSecurityService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        service = new UserService(users, mock(EmployeeRepository.class), mock(TenantRepository.class),
                mock(OutletRepository.class), security, encoder, mock(AuditLogService.class), mock(RefreshTokenService.class));
        when(security.resolveTenantId(any())).thenReturn(1L);
        owner = user(1, "owner", Role.ADMIN, 1L);
        gm = user(2, "gm", Role.GENERAL_MANAGER, null);
        mgr1 = user(3, "mgr1", Role.MANAGER, 1L);
        mgr2 = user(4, "mgr2", Role.MANAGER, 2L);
        cashier1 = user(5, "cash1", Role.CASHIER, 1L);
        cashier2 = user(6, "cash2", Role.CASHIER, 2L);
        List<User> all = List.of(owner, gm, mgr1, mgr2, cashier1, cashier2);
        when(users.findAll()).thenReturn(all);
        all.forEach(u -> when(users.findById(u.getId())).thenReturn(Optional.of(u)));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void actingAs(User caller) {
        when(security.getCurrentUser()).thenReturn(Optional.of(caller));
    }

    private List<String> visibleTo(User caller) {
        actingAs(caller);
        return service.getUsers(caller.getRole().name()).stream().map(UserDTO::getUsername).sorted().toList();
    }

    @Test
    void everyoneSeesOnlyWhatTheirPlaceInTheChainAllows() {
        assertEquals(List.of("cash1", "cash2", "gm", "mgr1", "mgr2", "owner"), visibleTo(owner));
        assertEquals(List.of("cash1", "cash2", "gm", "mgr1", "mgr2"), visibleTo(gm));
        assertEquals(List.of("cash1", "mgr1"), visibleTo(mgr1));
        assertEquals(List.of("cash2", "mgr2"), visibleTo(mgr2));
    }

    @Test
    void thePagedListAppliesTheSameRulesAndWorksWithoutPaging() {
        actingAs(mgr2);
        assertEquals(2, service.getUsers("MANAGER", Pageable.unpaged()).getTotalElements());
        assertEquals(1, service.getUsers("MANAGER", PageRequest.of(0, 1)).getContent().size());
        assertEquals(2, service.getUsers("MANAGER", PageRequest.of(0, 1)).getTotalElements());
    }

    @Test
    void aBranchManagerCannotTouchAnotherBranchesStaff() {
        actingAs(mgr1);
        assertThrows(RuntimeException.class, () -> service.resetPassword(6L, "Secret@12345", "MANAGER"));
        assertThrows(RuntimeException.class, () -> service.toggleUserActive(6L, "MANAGER", "mgr1"));
        assertThrows(RuntimeException.class, () -> service.getUserById(6L, "MANAGER"));
        assertDoesNotThrow(() -> service.resetPassword(5L, "Secret@12345", "MANAGER"));
    }

    @Test
    void anyStaffRoleCanBeManagedByTheirBranchManagerNotJustEmployee() {
        actingAs(mgr1);
        User kitchen = user(7, "kitchen1", Role.KITCHEN, 1L);
        when(users.findById(7L)).thenReturn(Optional.of(kitchen));
        assertDoesNotThrow(() -> service.resetPassword(7L, "Secret@12345", "MANAGER"));
    }

    @Test
    void theMainManagerCannotTouchTheOwnerButCanReachAnyBranch() {
        actingAs(gm);
        assertThrows(RuntimeException.class, () -> service.resetPassword(1L, "Secret@12345", "GENERAL_MANAGER"));
        assertDoesNotThrow(() -> service.resetPassword(6L, "Secret@12345", "GENERAL_MANAGER"));
    }

    @Test
    void creatingPeopleFollowsTheChainAndForcesBranchManagersIntoTheirOwnBranch() {
        actingAs(mgr2);
        CreateUserRequestDTO staff = new CreateUserRequestDTO();
        staff.setUsername("new_cashier");
        staff.setPassword("Secret@12345");
        staff.setRole("CASHIER");
        staff.setOutletId(1L); // tries to put them in someone else's branch
        UserDTO created = service.createUser(staff, "MANAGER");
        assertEquals(2L, created.getOutletId());

        CreateUserRequestDTO peer = new CreateUserRequestDTO();
        peer.setUsername("peer");
        peer.setPassword("Secret@12345");
        peer.setRole("MANAGER");
        peer.setOutletId(2L);
        assertThrows(RuntimeException.class, () -> service.createUser(peer, "MANAGER"));
    }

    @Test
    void permissionsCannotBeSelfGrantedHandedOutBeyondOnesOwnOrUsedToChangeBranch() {
        actingAs(mgr2);
        UpdateUserPermissionsRequestDTO move = new UpdateUserPermissionsRequestDTO();
        move.setOutletId(1L);
        assertThrows(RuntimeException.class, () -> service.updateUserPermissions(6L, move, "MANAGER"));

        UpdateUserPermissionsRequestDTO own = new UpdateUserPermissionsRequestDTO();
        own.setCanBill(true);
        assertThrows(RuntimeException.class, () -> service.updateUserPermissions(5L, own, "MANAGER"), "other branch");
        assertDoesNotThrow(() -> service.updateUserPermissions(6L, own, "MANAGER"));
    }

    @Test
    void theMainManagerCanDelegateMenuEditingToABranchManager() {
        actingAs(gm);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("gm", null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                com.neram.pos.access.AccessPolicy.PERM_MANAGE_MENU))));
        try {
            UpdateUserPermissionsRequestDTO grant = new UpdateUserPermissionsRequestDTO();
            grant.setCanManageMenu(true);

            UserDTO updated = service.updateUserPermissions(4L, grant, "GENERAL_MANAGER");

            assertTrue(updated.getCanManageMenu());
            assertTrue(com.neram.pos.access.AccessPolicy.managementAuthorities(mgr2)
                    .contains(com.neram.pos.access.AccessPolicy.PERM_MANAGE_MENU));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void aBranchManagerWithoutTheMenuRightCannotGrantIt() {
        actingAs(mgr2);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("mgr2", null, List.of()));
        try {
            UpdateUserPermissionsRequestDTO grant = new UpdateUserPermissionsRequestDTO();
            grant.setCanManageMenu(true);
            assertThrows(RuntimeException.class, () -> service.updateUserPermissions(6L, grant, "MANAGER"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void aManagerCreatedWithTheMenuToggleGetsIt() {
        actingAs(gm);
        CreateUserRequestDTO dto = new CreateUserRequestDTO();
        dto.setUsername("branch_mgr");
        dto.setPassword("Secret@12345");
        dto.setRole("MANAGER");
        dto.setOutletId(2L);
        dto.setCanManageMenu(true);

        UserDTO created = service.createUser(dto, "GENERAL_MANAGER");

        assertTrue(created.getCanManageMenu());
    }
}
