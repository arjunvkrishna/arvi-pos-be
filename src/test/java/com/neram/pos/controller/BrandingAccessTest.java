package com.neram.pos.controller;

import com.neram.pos.access.CafeUserDetails;
import com.neram.pos.access.AccessPolicy;
import com.neram.pos.entity.User.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BrandingAccessTest {

    private static Authentication as(Role role, Long outlet, String... authorities) {
        CafeUserDetails principal = new CafeUserDetails("u", "x", true,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList(), 1L, role, outlet);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void cafeWideImagesNeedTheCafeSettingsPermission() {
        assertDoesNotThrow(() -> BrandingController.assertMayEdit(as(Role.ADMIN, 1L, AccessPolicy.PERM_MANAGE_SETTINGS), null));
        assertDoesNotThrow(() -> BrandingController.assertMayEdit(as(Role.GENERAL_MANAGER, null, AccessPolicy.PERM_MANAGE_SETTINGS), null));
        assertThrows(AccessDeniedException.class,
                () -> BrandingController.assertMayEdit(as(Role.MANAGER, 1L, AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS), null));
        assertThrows(AccessDeniedException.class, () -> BrandingController.assertMayEdit(as(Role.CASHIER, 1L), null));
    }

    @Test
    void aBranchManagerChangesOnlyTheirOwnBranch() {
        Authentication manager = as(Role.MANAGER, 2L, AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS);
        assertDoesNotThrow(() -> BrandingController.assertMayEdit(manager, 2L));
        assertThrows(AccessDeniedException.class, () -> BrandingController.assertMayEdit(manager, 1L));
    }

    @Test
    void mainManagerAndOwnerChangeAnyBranch() {
        assertDoesNotThrow(() -> BrandingController.assertMayEdit(
                as(Role.GENERAL_MANAGER, null, AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS), 7L));
        assertDoesNotThrow(() -> BrandingController.assertMayEdit(
                as(Role.ADMIN, 1L, AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS), 7L));
    }

    @Test
    void aStaffMemberDelegatedBranchSettingsStillCannotReachOtherBranches() {
        Authentication staff = as(Role.CASHIER, 3L, AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS);
        assertDoesNotThrow(() -> BrandingController.assertMayEdit(staff, 3L));
        assertThrows(AccessDeniedException.class, () -> BrandingController.assertMayEdit(staff, 4L));
        assertThrows(AccessDeniedException.class, () -> BrandingController.assertMayEdit(staff, null));
    }
}
