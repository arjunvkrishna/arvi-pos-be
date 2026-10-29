package com.neram.pos.plan;

/** The cafe's plan does not include the module the request belongs to. Answered as 403 with a code the screens can recognise. */
public class PlanFeatureException extends RuntimeException {

    private final Feature feature;
    private final Plan currentPlan;

    public PlanFeatureException(Feature feature, Plan currentPlan) {
        super(feature.label() + " is not included in your " + currentPlan + " plan. Ask your administrator to move to the "
                + feature.minPlan() + " plan.");
        this.feature = feature;
        this.currentPlan = currentPlan;
    }

    public Feature feature() {
        return feature;
    }

    public Plan currentPlan() {
        return currentPlan;
    }
}
