package com.neram.pos.plan;

import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Answers "what does this cafe's plan include?" for the cafe the request (or background job) belongs to. The plan comes from
 * the registry, which keeps each cafe's record for a few seconds, so this costs no extra query per request and a plan change
 * takes effect within seconds.
 */
@Component
@RequiredArgsConstructor
public class PlanService {

    private final TenantRegistry registry;

    private Optional<TenantRecord> currentCafe() {
        return TenantContext.currentId().flatMap(registry::findById);
    }

    /** Outside a cafe (the platform owner, start-up work) nothing is gated, so this is the top plan. */
    public Plan currentPlan() {
        return currentCafe().map(c -> Plan.ofStored(c.subscriptionPlan())).orElse(Plan.ENTERPRISE);
    }

    public boolean has(Feature feature) {
        return currentPlan().includes(feature);
    }

    public void require(Feature feature) {
        Plan plan = currentPlan();
        if (!plan.includes(feature)) throw new PlanFeatureException(feature, plan);
    }

    public boolean has(TenantRecord cafe, Feature feature) {
        return Plan.ofStored(cafe.subscriptionPlan()).includes(feature);
    }

    /** Active staff logins this cafe may have (owner included). */
    public int maxLogins() {
        return currentPlan().maxLogins();
    }

    /** The current cafe's plan as the screens see it; branches are the cafe's own number, which the platform owner may have set apart from the plan. */
    public PlanInfoDTO summary() {
        Optional<TenantRecord> cafe = currentCafe();
        Plan plan = cafe.map(c -> Plan.ofStored(c.subscriptionPlan())).orElse(Plan.ENTERPRISE);
        return new PlanInfoDTO(plan.name(), cafe.map(TenantRecord::maxOutlets).orElse(Plan.UNLIMITED), plan.maxLogins(),
                plan.features().stream().map(Feature::name).toList());
    }
}
