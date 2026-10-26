package com.neram.pos.service;

/** Pure rules for budget against actual spending. */
public final class BudgetMath {
    private BudgetMath() {}

    public static double r2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Share of the budget already used; null without a budget. */
    public static Double usedPercent(Double budget, double actual) {
        if (budget == null || budget <= 0) return null;
        return Math.round(actual / budget * 1000.0) / 10.0;
    }

    /** Where the month will end at the current pace. Past months are final; a month that has not started projects to what was spent. */
    public static double projected(double actual, int daysElapsed, int daysInMonth) {
        if (daysElapsed <= 0 || daysElapsed >= daysInMonth) return r2(actual);
        return r2(actual / daysElapsed * daysInMonth);
    }

    /** NONE (no budget), OVER (already above it), WATCH (80% used or heading above it), else OK. */
    public static String status(Double budget, double actual, double projected) {
        if (budget == null || budget <= 0) return "NONE";
        if (actual > budget + 0.004) return "OVER";
        if (projected > budget + 0.004 || actual >= budget * 0.8) return "WATCH";
        return "OK";
    }
}
