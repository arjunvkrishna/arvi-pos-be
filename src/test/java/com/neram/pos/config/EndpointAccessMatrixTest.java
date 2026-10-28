package com.neram.pos.config;

import com.neram.pos.entity.User;
import com.neram.pos.entity.User.Role;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.service.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who can reach which endpoint, checked for every endpoint at once and for every tier of the chain of command:
 * <pre>
 *   owner (ADMIN / CAFE_ADMIN)  >  main manager (GENERAL_MANAGER)  >  branch manager (MANAGER)  >  staff
 * </pre>
 * Authorization is a rule written above each handler, which is easy to forget on a new one. This test turns the default
 * around: an endpoint with no rule must be listed on purpose, each tier is shut out of what belongs to the tier above it,
 * and a staff member with no extra permission reaches only the short list of things staff actually do.
 * <p>
 * The people are built by the real {@link CustomUserDetailsService}, so the authorities tested are the ones production
 * hands out. Which <em>branch's data</em> a person reaches is a separate layer (the branch filter), tested in the access package.
 */
class EndpointAccessMatrixTest {

    private record Endpoint(String key, String path, String rule) {}

    /** Reachable by any signed-in user, or by no login at all (checked by their own token/secret). Add to this only on purpose. */
    private static final Set<String> NO_ROLE_RULE = Set.of(
            // sign-in and health
            "AuthController#login", "AuthController#refresh", "AuthController#logout", "AuthController#health",
            // own account
            "UserController#getMyProfile", "UserController#updateMyProfile", "UserController#changeOwnPassword",
            // public: the cafe is named in the URL and each is token or rate-limit protected
            "PublicMenuController#getPublicMenu", "PublicMenuController#getPublicMenuStatus", "PublicMenuController#getLogo",
            "GiftCardController#getBalance", "CustomerController#unsubscribe",
            "PublicCampaignController#unsubscribeGet", "PublicCampaignController#unsubscribePost",
            "TelegramWebhookController#handleTelegramWebhook",
            // the print bridge signs in with its cafe's own token, not a user
            "PrintBridgeController#getBridgePrinterConfig", "PrintBridgeController#pollJob", "PrintBridgeController#reportScan",
            "PrintBridgeController#ackReceipt", "PrintBridgeController#reportSent", "PrintBridgeController#reportComplete",
            "PrintBridgeController#reportFail", "PrintBridgeController#syncOffline", "PrintBridgeController#getJob",
            "PrintBridgeController#acknowledgeJob", "PrintBridgeController#heartbeat", "PrintBridgeController#getStatus",
            // what the order screen needs to show the menu
            "MenuItemController#getAllMenuItems", "MenuItemController#menuVersion", "MenuItemController#getAvailableMenuItems",
            "CategoryController#getAllCategories", "ComboController#getCombosForMenuItem",
            "ModifierController#getAllActiveGroups", "ModifierController#getGroupsForMenuItem",
            "DiningTableController#getAllTables",
            // the list is trimmed in the service: only owner and main managers get every branch, everyone else their own
            "OutletController#getOutlets");

    /** The cafe owner only. Main managers and branch managers must be refused on every one of these. */
    private static final Set<String> OWNER_ONLY = Set.of(
            "AuditController#getAuditLogs", "BillController#deleteBill",
            "SupplierController#deleteSupplier", "SupplierController#deleteInvoice", "SupplierController#deletePayment",
            "LoyaltyController#updateSettings");

    /** Brand-wide settings and records: owner and main manager. A branch manager must be refused, even with branch toggles. */
    private static final Set<String> CAFE_WIDE_ONLY = Set.of(
            "TelegramAdminController#getSettings", "TelegramAdminController#updateSettings",
            "TelegramAdminController#getRecipients", "TelegramAdminController#toggleRecipient",
            "TelegramAdminController#generateLinkCode", "TelegramAdminController#updateChatId",
            "TelegramAdminController#unlinkRecipient", "TelegramAdminController#sendTestMessage",
            "TelegramAdminController#previewLowStockReport", "TelegramAdminController#sendReportNow",
            "TelegramAdminController#getDeliveryHistory",
            "CampaignController#createCampaign", "CampaignController#updateCampaign",
            "CampaignController#deleteCampaign", "CampaignController#sendCampaign",
            "CouponController#createCoupon", "CouponController#updateCoupon", "CouponController#deleteCoupon",
            "BankReconciliationController#getAllStatements", "BankReconciliationController#importStatementFile",
            "BankReconciliationController#matchRow", "BankReconciliationController#autoMatch",
            "AccountingController#getBalanceSheet", "AccountingController#getGiftCardLiability",
            "DocumentController#createCategory", "DocumentController#deleteCategory",
            "OutletController#createOutlet", "OutletController#getFranchiseAnalytics");

    /** The shared menu: dishes, add-on groups and combos change for every branch, so they need the menu right. */
    private static final Set<String> MENU_WRITES = Set.of(
            "ModifierController#createGroup", "ModifierController#updateGroup", "ModifierController#deleteGroup",
            "ModifierController#assignGroups", "ComboController#createComboGroup", "ComboController#deleteComboGroup");

    /** What a staff member with no extra permission is meant to do: their own attendance and leave, and nothing about the business. */
    private static final Set<String> STAFF_MAY_REACH = Set.of(
            "AttendanceController#getMyAttendanceAudit", "AttendanceController#getAttendanceByEmployee", "AttendanceController#punchStatus",
            "AttendanceController#checkIn", "AttendanceController#checkOut", "AttendanceController#getMonthlySummary",
            "LeaveController#applyForLeave", "LeaveController#getLeavesByEmployee",
            "AuditController#getMyAuditLogs", "NotificationController#reportLowStock",
            // the cafe logo and payment QR shown in the header and on bills (editing them needs a settings right)
            "BrandingController#overview", "BrandingController#get");

    /** Extra things one kind of staff does by their job: the cashier sells gift cards at the till. */
    private static final Map<Role, Set<String>> ROLE_SPECIFIC = Map.of(Role.CASHIER, Set.of("GiftCardController#issueCard"));

    // ---- the people --------------------------------------------------------------------------------------------

    private static Authentication person(Role role, Consumer<User> toggles) {
        User user = new User();
        user.setUsername("u");
        user.setPasswordHash("not-checked-here");
        user.setRole(role);
        user.setActive(true);
        user.setOutletId(10L);
        // a bare account: no toggles on, so only what the role itself brings
        user.setCanBill(false);
        user.setCanTakeOrders(false);
        user.setCanManageInventory(false);
        user.setCanManageExpenses(false);
        user.setCanViewReports(false);
        user.setCanPrintBill(false);
        user.setCanManageMenu(false);
        user.setCanManageSettings(false);
        toggles.accept(user);
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByUsername("u")).thenReturn(Optional.of(user));
        UserDetails details = new CustomUserDetailsService(repo).loadUserByUsername("u");
        return new UsernamePasswordAuthenticationToken(details, "x", details.getAuthorities());
    }

    private static Authentication person(Role role) {
        return person(role, u -> { });
    }

    private static final List<Role> OWNERS = List.of(Role.ADMIN, Role.CAFE_ADMIN);
    private static final List<Role> STAFF_ROLES = List.of(Role.EMPLOYEE, Role.CASHIER, Role.CAPTAIN, Role.KITCHEN);

    // ---- the scan ----------------------------------------------------------------------------------------------

    private static List<Endpoint> endpoints() throws Exception {
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Endpoint> out = new ArrayList<>();
        for (BeanDefinition bd : scan.findCandidateComponents("com.neram.pos.controller")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            PreAuthorize onClass = type.getAnnotation(PreAuthorize.class);
            for (Method m : type.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)) continue;
                PreAuthorize own = m.getAnnotation(PreAuthorize.class);
                String rule = own != null ? own.value() : onClass != null ? onClass.value() : null;
                out.add(new Endpoint(type.getSimpleName() + "#" + m.getName(), type.getSimpleName() + "." + m.getName(), rule));
            }
        }
        out.sort(Comparator.comparing(Endpoint::key));
        return out;
    }

    private static boolean allows(String rule, Authentication who) {
        SecurityExpressionRoot root = new SecurityExpressionRoot(who) {};
        root.setTrustResolver(new AuthenticationTrustResolverImpl());
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(rule).getValue(new StandardEvaluationContext(root), Boolean.class));
    }

    private static List<Endpoint> listed(Set<String> keys) throws Exception {
        return endpoints().stream().filter(e -> keys.contains(e.key())).toList();
    }

    // ---- tests -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the scan really finds the API (guards against the test silently checking nothing)")
    void scanFindsTheApi() throws Exception {
        List<Endpoint> all = endpoints();
        assertTrue(all.size() > 300, "found only " + all.size() + " endpoints");
        for (Set<String> keys : List.of(OWNER_ONLY, CAFE_WIDE_ONLY, MENU_WRITES, STAFF_MAY_REACH, NO_ROLE_RULE)) {
            for (String k : keys) assertTrue(all.stream().anyMatch(e -> e.key().equals(k)), "not an endpoint any more: " + k);
        }
    }

    @Test
    @DisplayName("every endpoint has a role rule unless it is listed as open on purpose")
    void noEndpointIsOpenByAccident() throws Exception {
        List<String> open = endpoints().stream().filter(e -> e.rule() == null && !NO_ROLE_RULE.contains(e.key()))
                .map(Endpoint::key).collect(Collectors.toList());
        assertTrue(open.isEmpty(), "These endpoints let any signed-in user in. Give each a @PreAuthorize, or list it in NO_ROLE_RULE if that is really intended: " + open);
    }

    @Test
    @DisplayName("owner-only work: both kinds of owner get in; main managers and branch managers are refused")
    void ownerOnlyStaysWithTheOwner() throws Exception {
        for (Endpoint e : listed(OWNER_ONLY)) {
            assertNotNull(e.rule(), e.key());
            for (Role owner : OWNERS) assertTrue(allows(e.rule(), person(owner)), owner + " cannot call " + e.path());
            assertFalse(allows(e.rule(), person(Role.GENERAL_MANAGER)), "a main manager can call " + e.path());
            assertFalse(allows(e.rule(), person(Role.MANAGER)), "a branch manager can call " + e.path());
        }
    }

    @Test
    @DisplayName("cafe-wide work: owner and main manager get in; a branch manager is refused, even one given the branch-settings toggle")
    void cafeWideWorkIsNotForBranchManagers() throws Exception {
        Authentication delegated = person(Role.MANAGER, u -> { u.setCanManageSettings(true); u.setCanManageMenu(true); });
        for (Endpoint e : listed(CAFE_WIDE_ONLY)) {
            assertNotNull(e.rule(), e.key());
            for (Role wide : List.of(Role.ADMIN, Role.CAFE_ADMIN, Role.GENERAL_MANAGER)) {
                assertTrue(allows(e.rule(), person(wide)), wide + " cannot call " + e.path());
            }
            assertFalse(allows(e.rule(), person(Role.MANAGER)), "a branch manager can call " + e.path());
            assertFalse(allows(e.rule(), delegated), "a branch manager with branch toggles can call " + e.path());
            for (Role staff : STAFF_ROLES) assertFalse(allows(e.rule(), person(staff)), staff + " can call " + e.path());
        }
    }

    @Test
    @DisplayName("shared menu changes (dishes, add-ons, combos) need the menu right: only the owner, main manager, or a branch manager it was delegated to")
    void menuWritesNeedTheMenuRight() throws Exception {
        Authentication plainBranchManager = person(Role.MANAGER);
        Authentication menuBranchManager = person(Role.MANAGER, u -> u.setCanManageMenu(true));
        Authentication menuStaff = person(Role.EMPLOYEE, u -> u.setCanManageMenu(true));
        Authentication busyStaff = person(Role.CASHIER, u -> { u.setCanBill(true); u.setCanTakeOrders(true); u.setCanManageInventory(true); });
        for (Endpoint e : listed(MENU_WRITES)) {
            assertNotNull(e.rule(), e.key());
            for (Role wide : List.of(Role.ADMIN, Role.CAFE_ADMIN, Role.GENERAL_MANAGER)) assertTrue(allows(e.rule(), person(wide)), wide + " " + e.path());
            assertTrue(allows(e.rule(), menuBranchManager), "a delegated branch manager cannot " + e.path());
            assertTrue(allows(e.rule(), menuStaff), "staff given the menu right cannot " + e.path());
            assertFalse(allows(e.rule(), plainBranchManager), "a branch manager without the menu right can " + e.path());
            assertFalse(allows(e.rule(), busyStaff), "staff with order/billing/stock rights can change the shared menu: " + e.path());
        }
    }

    @Test
    @DisplayName("platform-owner endpoints are for the SaaS owner only: no cafe user, not even a cafe owner, gets in")
    void platformEndpointsAreNotForCafeUsers() throws Exception {
        List<Endpoint> platform = endpoints().stream().filter(e -> e.key().startsWith("SuperAdminController#")).toList();
        assertFalse(platform.isEmpty());
        for (Endpoint e : platform) {
            assertNotNull(e.rule(), e.key());
            assertTrue(allows(e.rule(), person(Role.SUPER_ADMIN)), "the platform owner cannot call " + e.path());
            for (Role cafeRole : List.of(Role.ADMIN, Role.CAFE_ADMIN, Role.GENERAL_MANAGER, Role.MANAGER, Role.EMPLOYEE)) {
                assertFalse(allows(e.rule(), person(cafeRole)), cafeRole + " can call platform endpoint " + e.path());
            }
        }
    }

    @Test
    @DisplayName("staff of every kind, with no extra permission, reach only their own attendance and leave")
    void staffCannotReachBusinessData() throws Exception {
        for (Role role : STAFF_ROLES) {
            Authentication staff = person(role);
            List<String> reachable = new ArrayList<>();
            for (Endpoint e : endpoints()) {
                if (e.rule() != null && allows(e.rule(), staff) && !STAFF_MAY_REACH.contains(e.key())
                        && !ROLE_SPECIFIC.getOrDefault(role, Set.of()).contains(e.key())) reachable.add(e.key());
            }
            assertTrue(reachable.isEmpty(), role + " can call these, which no staff screen needs: " + reachable);
        }
    }

    @Test
    @DisplayName("every kind of staff can use their own attendance and leave screens (cashiers, captains and kitchen are staff too)")
    void everyKindOfStaffCanPunchIn() throws Exception {
        for (Role role : STAFF_ROLES) {
            Authentication staff = person(role);
            for (Endpoint e : listed(Set.of("AttendanceController#checkIn", "AttendanceController#checkOut",
                    "AttendanceController#punchStatus", "LeaveController#applyForLeave"))) {
                assertTrue(allows(e.rule(), staff), role + " cannot use " + e.path());
            }
        }
    }

    @Test
    @DisplayName("staff given the order, print, stock, expense, billing or report permission get that work and no manager screen")
    void staffPermissionsStayInTheirLane() throws Exception {
        Map<String, Authentication> staff = new LinkedHashMap<>();
        staff.put("canTakeOrders", person(Role.EMPLOYEE, u -> u.setCanTakeOrders(true)));
        staff.put("canBill", person(Role.CASHIER, u -> u.setCanBill(true)));
        staff.put("canPrintBill", person(Role.EMPLOYEE, u -> u.setCanPrintBill(true)));
        staff.put("canManageInventory", person(Role.EMPLOYEE, u -> u.setCanManageInventory(true)));
        staff.put("canManageExpenses", person(Role.EMPLOYEE, u -> u.setCanManageExpenses(true)));
        staff.put("canViewReports", person(Role.EMPLOYEE, u -> u.setCanViewReports(true)));
        staff.put("everything at once", person(Role.CAPTAIN, u -> {
            u.setCanBill(true); u.setCanTakeOrders(true); u.setCanPrintBill(true);
            u.setCanManageInventory(true); u.setCanManageExpenses(true); u.setCanViewReports(true);
        }));
        // the controllers whose screens are for managers only: no staff permission may open any of them
        Set<String> managerOnly = Set.of("CustomerController", "CouponController", "DocumentController", "ShiftScheduleController",
                "TipPoolController", "SalaryController", "EmployeeController", "UserController", "SupplierController",
                "AccountingController", "BankReconciliationController", "CampaignController", "ReportExportController",
                "AnalyticsController", "MoneyReportsController", "OwnerDashboardController", "ExpenseController",
                "CashierShiftController", "BillRefundController", "TelegramAdminController", "AuditController", "LoyaltyController",
                "OutletController", "TenantController", "LeaveController");
        List<String> leaks = new ArrayList<>();
        for (Endpoint e : endpoints()) {
            String controller = e.key().substring(0, e.key().indexOf('#'));
            if (e.rule() == null || !managerOnly.contains(controller) || STAFF_MAY_REACH.contains(e.key())) continue;
            staff.forEach((perm, who) -> { if (allows(e.rule(), who)) leaks.add(perm + " -> " + e.key()); });
        }
        assertTrue(leaks.isEmpty(), "A single permission opens a manager screen: " + leaks);
    }

    @Test
    @DisplayName("a branch manager's toggles never grant more than their tier: delegating branch settings does not unlock cafe-wide rights")
    void delegationDoesNotEscalate() {
        Authentication delegated = person(Role.MANAGER, u -> { u.setCanManageSettings(true); u.setCanManageMenu(true); });
        Set<String> granted = delegated.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
        assertTrue(granted.contains("PERM_MANAGE_OUTLET_SETTINGS"));
        assertTrue(granted.contains("PERM_MANAGE_MENU"));
        assertFalse(granted.contains("PERM_MANAGE_SETTINGS"), "cafe-wide settings must stay with the owner and main manager");
        assertFalse(granted.contains("ROLE_ADMIN"));
        assertFalse(granted.contains("ROLE_GENERAL_MANAGER"));

        Set<String> staff = person(Role.CASHIER, u -> { u.setCanManageSettings(true); u.setCanManageMenu(true); })
                .getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
        assertFalse(staff.contains("ROLE_MANAGER") || staff.contains("ROLE_ADMIN"), "toggles must not turn staff into managers");
    }

    @Test
    @DisplayName("a person who is not signed in is refused wherever a rule exists")
    void anonymousIsRefused() throws Exception {
        Authentication nobody = new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        for (Endpoint e : endpoints()) {
            if (e.rule() != null) assertFalse(allows(e.rule(), nobody), "an anonymous caller passes " + e.path() + " (" + e.rule() + ")");
        }
    }
}
