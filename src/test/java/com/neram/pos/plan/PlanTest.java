package com.neram.pos.plan;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class PlanTest {

    @Test
    void starterIncludesNoOptionalModule() {
        assertTrue(Plan.STARTER.features().isEmpty());
        for (Feature f : Feature.values()) assertFalse(Plan.STARTER.includes(f), f + " must not be in STARTER");
    }

    @Test
    void proAddsTheDailyRunningModules() {
        assertEquals(EnumSet.of(Feature.STOCK_CONTROL, Feature.SUPPLIERS_EXPENSES, Feature.CRM, Feature.STAFF_PAYROLL,
                Feature.TELEGRAM, Feature.EXPORTS), EnumSet.copyOf(Plan.PRO.features()));
    }

    @Test
    void enterpriseIncludesEverything() {
        assertEquals(Feature.values().length, Plan.ENTERPRISE.features().size());
        assertTrue(Plan.ENTERPRISE.includes(Feature.CAMPAIGNS));
        assertTrue(Plan.ENTERPRISE.includes(Feature.ACCOUNTING));
    }

    @Test
    void eachPlanKeepsEverythingTheCheaperPlanHas() {
        for (Feature f : Feature.values()) {
            if (Plan.STARTER.includes(f)) assertTrue(Plan.PRO.includes(f));
            if (Plan.PRO.includes(f)) assertTrue(Plan.ENTERPRISE.includes(f));
        }
    }

    @Test
    void limitsGrowWithThePlan() {
        assertEquals(1, Plan.STARTER.defaultBranches());
        assertEquals(5, Plan.STARTER.maxLogins());
        assertEquals(5, Plan.PRO.defaultBranches());
        assertEquals(25, Plan.PRO.maxLogins());
        assertEquals(Plan.UNLIMITED, Plan.ENTERPRISE.defaultBranches());
        assertEquals(Plan.UNLIMITED, Plan.ENTERPRISE.maxLogins());
    }

    @Test
    void theOwnerOfThePlatformMustNameOneOfTheThreePlans() {
        assertEquals(Plan.PRO, Plan.parse(" pro "));
        assertEquals(Plan.ENTERPRISE, Plan.parse("Enterprise"));
        assertThrows(IllegalArgumentException.class, () -> Plan.parse("GOLD"));
        assertThrows(IllegalArgumentException.class, () -> Plan.parse(null));
        assertThrows(IllegalArgumentException.class, () -> Plan.parse(""));
    }

    @Test
    void anOldUnrecognisedStoredValueDoesNotLockACafeOut() {
        assertEquals(Plan.PRO, Plan.ofStored("LEGACY"));
        assertEquals(Plan.PRO, Plan.ofStored(null));
        assertEquals(Plan.STARTER, Plan.ofStored("starter"));
    }

    @Test
    void everyFeatureHasAPlainLabelAndACheapestPlan() {
        assertTrue(Arrays.stream(Feature.values()).allMatch(f -> f.label() != null && !f.label().isBlank() && f.minPlan() != Plan.STARTER));
    }

    @Test
    void theRefusalNamesTheModuleAndThePlanThatHasIt() {
        PlanFeatureException e = new PlanFeatureException(Feature.CRM, Plan.STARTER);
        assertTrue(e.getMessage().contains("STARTER"));
        assertTrue(e.getMessage().contains("PRO"));
        assertEquals(Feature.CRM, e.feature());
    }
}
