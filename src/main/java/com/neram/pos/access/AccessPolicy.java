package com.neram.pos.access;

import com.neram.pos.entity.User;
import com.neram.pos.entity.User.Role;

/**
 * The cafe's chain of command, in one place.
 *
 * <pre>
 *   SUPER_ADMIN          platform owner (control database, not a cafe user)
 *   ADMIN / CAFE_ADMIN   cafe owner: everything in the cafe, all branches
 *   GENERAL_MANAGER      main manager: all branches, cannot create or change owners
 *   MANAGER              branch manager: own branch only
 *   CASHIER, CAPTAIN, KITCHEN, EMPLOYEE   branch staff: own branch only, what their toggles allow
 * </pre>
 * A person can only create, change, reset or deactivate people strictly below them, and branch-level people only
 * within their own branch. Owners may add co-owners.
 */
public final class AccessPolicy {

    public static final String PERM_MANAGE_MENU = "PERM_MANAGE_MENU";
    /** Cafe-wide settings: brand name/logo/tagline, GST, currency, public menu, default payment QR. */
    public static final String PERM_MANAGE_SETTINGS = "PERM_MANAGE_SETTINGS";
    /** Own-branch settings: branch profile, printers, receipt text, branch payment QR. */
    public static final String PERM_MANAGE_OUTLET_SETTINGS = "PERM_MANAGE_OUTLET_SETTINGS";
    public static final String PERM_MANAGE_STAFF = "PERM_MANAGE_STAFF";

    private AccessPolicy() {
    }

    public static int rank(Role role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case SUPER_ADMIN -> 100;
            case ADMIN, CAFE_ADMIN -> 80;
            case GENERAL_MANAGER -> 60;
            case MANAGER -> 40;
            case CASHIER, CAPTAIN, KITCHEN, EMPLOYEE -> 10;
        };
    }

    public static boolean isOwner(Role role) {
        return role == Role.SUPER_ADMIN || role == Role.ADMIN || role == Role.CAFE_ADMIN;
    }

    /** Owner and main manager see and run every branch. */
    public static boolean hasCafeWideAccess(Role role) {
        return rank(role) >= rank(Role.GENERAL_MANAGER);
    }

    public static boolean isManagement(Role role) {
        return rank(role) >= rank(Role.MANAGER);
    }

    /**
     * May {@code actor} (home branch {@code actorOutlet}) create or change a person with role {@code target} in branch
     * {@code targetOutlet}?
     */
    public static boolean canManage(Role actor, Long actorOutlet, Role target, Long targetOutlet) {
        if (actor == null || target == null || target == Role.SUPER_ADMIN) {
            return false;
        }
        boolean higher = rank(actor) > rank(target) || (isOwner(actor) && isOwner(target));
        if (!higher) {
            return false;
        }
        if (hasCafeWideAccess(actor)) {
            return true;
        }
        return actorOutlet != null && actorOutlet.equals(targetOutlet);
    }

    /**
     * May this person appear in {@code actor}'s user list? Owners are invisible below owner level; a branch manager
     * only sees people of their own branch up to their own level.
     */
    public static boolean canSee(Role actor, Long actorOutlet, Role target, Long targetOutlet) {
        if (target == Role.SUPER_ADMIN) {
            return false;
        }
        if (isOwner(actor)) {
            return true;
        }
        if (hasCafeWideAccess(actor)) {
            return rank(target) <= rank(actor);
        }
        return isManagement(actor) && actorOutlet != null && actorOutlet.equals(targetOutlet)
                && rank(target) <= rank(actor);
    }

    /** Authority names a user gets for the settings/menu areas, derived from role and toggles. */
    public static java.util.List<String> managementAuthorities(User user) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Role role = user.getRole();
        boolean cafeWide = hasCafeWideAccess(role);
        if (cafeWide || Boolean.TRUE.equals(user.getCanManageMenu())) {
            out.add(PERM_MANAGE_MENU);
        }
        if (cafeWide) {
            out.add(PERM_MANAGE_SETTINGS);
        }
        if (cafeWide || role == Role.MANAGER || Boolean.TRUE.equals(user.getCanManageSettings())) {
            out.add(PERM_MANAGE_OUTLET_SETTINGS);
        }
        if (isManagement(role)) {
            out.add(PERM_MANAGE_STAFF);
        }
        return out;
    }
}
