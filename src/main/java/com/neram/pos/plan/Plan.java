package com.neram.pos.plan;

import java.util.Arrays;
import java.util.List;

/**
 * What a cafe's subscription gives it. A plan sets the default number of branches and the number of staff logins, and
 * (through {@link Feature#minPlan()}) which modules are switched on. Each plan includes everything the plan below it has.
 */
public enum Plan {
    STARTER(1, 5),
    PRO(5, 25),
    ENTERPRISE(Plan.UNLIMITED, Plan.UNLIMITED);

    /** Shown as "Unlimited". A real number so it can sit in the same integer column as an ordinary limit. */
    public static final int UNLIMITED = 999;

    private final int defaultBranches;
    private final int maxLogins;

    Plan(int defaultBranches, int maxLogins) {
        this.defaultBranches = defaultBranches;
        this.maxLogins = maxLogins;
    }

    /** Branches a new cafe on this plan may open. A cafe can be given a different number by the platform owner. */
    public int defaultBranches() {
        return defaultBranches;
    }

    /** Active staff logins (owner included) this plan allows. */
    public int maxLogins() {
        return maxLogins;
    }

    public boolean includes(Feature feature) {
        return feature.minPlan().ordinal() <= ordinal();
    }

    public List<Feature> features() {
        return Arrays.stream(Feature.values()).filter(this::includes).toList();
    }

    /** For input from the platform owner: anything but the three plans is refused. */
    public static Plan parse(String name) {
        if (name != null) {
            for (Plan p : values()) {
                if (p.name().equalsIgnoreCase(name.trim())) return p;
            }
        }
        throw new IllegalArgumentException("Plan must be STARTER, PRO or ENTERPRISE.");
    }

    /** For a value already stored: an unrecognised old value must not lock a cafe out, so it counts as PRO. */
    public static Plan ofStored(String name) {
        try {
            return parse(name);
        } catch (IllegalArgumentException e) {
            return PRO;
        }
    }
}
