package com.neram.pos.access;

import com.neram.pos.entity.User;
import com.neram.pos.entity.User.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {

    private static User user(Role role, boolean menu, boolean settings) {
        User u = new User();
        u.setRole(role);
        u.setCanManageMenu(menu);
        u.setCanManageSettings(settings);
        return u;
    }

    @Test
    void chainOfCommandRanksOwnerAboveMainManagerAboveBranchManagerAboveStaff() {
        assertTrue(AccessPolicy.rank(Role.ADMIN) > AccessPolicy.rank(Role.GENERAL_MANAGER));
        assertTrue(AccessPolicy.rank(Role.GENERAL_MANAGER) > AccessPolicy.rank(Role.MANAGER));
        assertTrue(AccessPolicy.rank(Role.MANAGER) > AccessPolicy.rank(Role.CASHIER));
        assertEquals(AccessPolicy.rank(Role.ADMIN), AccessPolicy.rank(Role.CAFE_ADMIN));
        assertEquals(AccessPolicy.rank(Role.CASHIER), AccessPolicy.rank(Role.KITCHEN));
    }

    @Test
    void onlyOwnerAndMainManagerSeeAllBranches() {
        assertTrue(AccessPolicy.hasCafeWideAccess(Role.ADMIN));
        assertTrue(AccessPolicy.hasCafeWideAccess(Role.CAFE_ADMIN));
        assertTrue(AccessPolicy.hasCafeWideAccess(Role.GENERAL_MANAGER));
        assertFalse(AccessPolicy.hasCafeWideAccess(Role.MANAGER));
        assertFalse(AccessPolicy.hasCafeWideAccess(Role.CASHIER));
    }

    @Test
    void youCanOnlyManagePeopleBelowYou() {
        assertTrue(AccessPolicy.canManage(Role.ADMIN, 1L, Role.GENERAL_MANAGER, null));
        assertTrue(AccessPolicy.canManage(Role.ADMIN, 1L, Role.CAFE_ADMIN, 1L), "owners may add co-owners");
        assertTrue(AccessPolicy.canManage(Role.GENERAL_MANAGER, null, Role.MANAGER, 2L));
        assertFalse(AccessPolicy.canManage(Role.GENERAL_MANAGER, null, Role.ADMIN, 1L), "main manager cannot touch owners");
        assertFalse(AccessPolicy.canManage(Role.GENERAL_MANAGER, null, Role.GENERAL_MANAGER, null), "peers are not below you");
        assertFalse(AccessPolicy.canManage(Role.MANAGER, 1L, Role.MANAGER, 1L));
        assertFalse(AccessPolicy.canManage(Role.CASHIER, 1L, Role.KITCHEN, 1L));
        assertFalse(AccessPolicy.canManage(Role.ADMIN, 1L, Role.SUPER_ADMIN, null), "platform accounts are out of reach");
    }

    @Test
    void aBranchManagerManagesOnlyStaffOfTheirOwnBranch() {
        assertTrue(AccessPolicy.canManage(Role.MANAGER, 2L, Role.CASHIER, 2L));
        assertFalse(AccessPolicy.canManage(Role.MANAGER, 2L, Role.CASHIER, 1L));
        assertFalse(AccessPolicy.canManage(Role.MANAGER, null, Role.CASHIER, 1L), "a manager without a branch manages nobody");
        assertTrue(AccessPolicy.canManage(Role.GENERAL_MANAGER, null, Role.CASHIER, 7L), "main manager reaches every branch");
    }

    @Test
    void userListsHideOwnersFromEveryoneBelowOwner() {
        assertTrue(AccessPolicy.canSee(Role.ADMIN, 1L, Role.GENERAL_MANAGER, null));
        assertTrue(AccessPolicy.canSee(Role.ADMIN, 1L, Role.CAFE_ADMIN, 1L));
        assertFalse(AccessPolicy.canSee(Role.ADMIN, 1L, Role.SUPER_ADMIN, null));
        assertFalse(AccessPolicy.canSee(Role.GENERAL_MANAGER, null, Role.ADMIN, 1L));
        assertTrue(AccessPolicy.canSee(Role.GENERAL_MANAGER, null, Role.GENERAL_MANAGER, null));
        assertFalse(AccessPolicy.canSee(Role.MANAGER, 1L, Role.CASHIER, 2L), "other branch");
        assertTrue(AccessPolicy.canSee(Role.MANAGER, 1L, Role.CASHIER, 1L));
        assertFalse(AccessPolicy.canSee(Role.MANAGER, 1L, Role.GENERAL_MANAGER, null));
        assertFalse(AccessPolicy.canSee(Role.CASHIER, 1L, Role.CASHIER, 1L), "staff do not browse accounts");
    }

    @Test
    void settingsAuthoritiesFollowTheRoleAndTheDelegatedToggles() {
        assertEquals(List.of(AccessPolicy.PERM_MANAGE_MENU, AccessPolicy.PERM_MANAGE_SETTINGS,
                        AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS, AccessPolicy.PERM_MANAGE_STAFF),
                AccessPolicy.managementAuthorities(user(Role.ADMIN, false, false)));
        assertEquals(4, AccessPolicy.managementAuthorities(user(Role.GENERAL_MANAGER, false, false)).size());

        List<String> branchManager = AccessPolicy.managementAuthorities(user(Role.MANAGER, false, false));
        assertTrue(branchManager.contains(AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS));
        assertTrue(branchManager.contains(AccessPolicy.PERM_MANAGE_STAFF));
        assertFalse(branchManager.contains(AccessPolicy.PERM_MANAGE_SETTINGS), "cafe-wide branding is not a branch manager's");
        assertFalse(branchManager.contains(AccessPolicy.PERM_MANAGE_MENU), "the shared menu is delegated, not default");

        assertTrue(AccessPolicy.managementAuthorities(user(Role.MANAGER, true, false)).contains(AccessPolicy.PERM_MANAGE_MENU));

        assertEquals(List.of(), AccessPolicy.managementAuthorities(user(Role.CASHIER, false, false)));
        assertEquals(List.of(AccessPolicy.PERM_MANAGE_MENU, AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS),
                AccessPolicy.managementAuthorities(user(Role.CASHIER, true, true)));
    }
}
