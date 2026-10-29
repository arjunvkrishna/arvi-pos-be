package com.neram.pos.plan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every controller must have been decided on: part of a plan-gated module, or deliberately open on every plan. A new
 * controller that is neither fails here, so a module can never be added to the product without choosing which plans get it.
 */
class PlanCoverageTest {

    /** Belongs to every plan: the till and everything a cafe needs to run a day, plus platform and public plumbing. */
    private static final Set<String> EVERY_PLAN = Set.of(
            "AuthController", "BillController", "BillRefundController", "BrandingController", "CashierShiftController",
            "CategoryController", "ComboController", "DiningTableController", "GstSettingController", "ItemsSoldController",
            "KitchenController", "MenuItemController", "ModifierController", "NotificationController", "OutletController",
            "OwnerDashboardController", "PrintBridgeController", "PrinterController", "PromotionController",
            "PublicCampaignController", "PublicMenuController", "RecipeController", "SettingController", "SuperAdminController",
            "TelegramWebhookController", "TenantController", "UserController",
            // partly gated: only the endpoints marked with @RequiresFeature belong to a module
            "AnalyticsController", "InventoryController", "MoneyReportsController");

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.neram.pos.controller")) {
            out.add(Class.forName(bd.getBeanClassName()));
        }
        return out;
    }

    private static boolean gatedWholly(Class<?> c) {
        return AnnotatedElementUtils.hasAnnotation(c, RequiresFeature.class);
    }

    private static boolean gatedPartly(Class<?> c) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.isAnnotationPresent(RequiresFeature.class)) return true;
        }
        return false;
    }

    @Test
    void everyControllerIsEitherPartOfAPlanModuleOrDeclaredOpenOnEveryPlan() throws Exception {
        List<String> undecided = new ArrayList<>();
        for (Class<?> c : controllers()) {
            if (!gatedWholly(c) && !gatedPartly(c) && !EVERY_PLAN.contains(c.getSimpleName())) undecided.add(c.getSimpleName());
        }
        assertTrue(undecided.isEmpty(), "Decide which plans get these controllers (@RequiresFeature, or add to EVERY_PLAN): " + undecided);
    }

    @Test
    void aControllerIsNotBothGatedAsAWholeAndListedAsOpen() throws Exception {
        List<String> clash = new ArrayList<>();
        for (Class<?> c : controllers()) {
            if (gatedWholly(c) && EVERY_PLAN.contains(c.getSimpleName())) clash.add(c.getSimpleName());
        }
        assertTrue(clash.isEmpty(), "Both gated and listed as open on every plan: " + clash);
    }

    @Test
    void theOpenListNamesRealControllers() throws Exception {
        Set<String> real = new TreeSet<>();
        for (Class<?> c : controllers()) real.add(c.getSimpleName());
        Set<String> stale = new TreeSet<>(EVERY_PLAN);
        stale.removeAll(real);
        assertTrue(stale.isEmpty(), "Listed but no longer a controller: " + stale);
    }

    @Test
    void theTilledPlanSplitMatchesWhatWasPromised() throws Exception {
        assertEquals(Feature.CRM, feature("CustomerController"));
        assertEquals(Feature.CRM, feature("CouponController"));
        assertEquals(Feature.STAFF_PAYROLL, feature("SalaryController"));
        assertEquals(Feature.SUPPLIERS_EXPENSES, feature("ExpenseController"));
        assertEquals(Feature.CAMPAIGNS, feature("CampaignController"));
        assertEquals(Feature.ACCOUNTING, feature("BankReconciliationController"));
        assertEquals(Feature.SCHEDULING, feature("TipPoolController"));
        assertEquals(Feature.DOCUMENTS, feature("DocumentController"));
        assertEquals(Feature.AUDIT, feature("AuditController"));
        assertEquals(Feature.TELEGRAM, feature("TelegramAdminController"));
        assertEquals(Feature.STOCK_CONTROL, feature("StockCountController"));
        assertEquals(Feature.EXPORTS, feature("ReportExportController"));
    }

    private static Feature feature(String controller) throws Exception {
        for (Class<?> c : controllers()) {
            if (c.getSimpleName().equals(controller)) return AnnotatedElementUtils.findMergedAnnotation(c, RequiresFeature.class).value();
        }
        throw new AssertionError("No controller " + controller);
    }
}
