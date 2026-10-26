package com.neram.pos.service;

/** Pure rules for reorder suggestions, cost changes, dish margins and count differences (no database, easy to test). */
public final class StockOpsMath {
    private StockOpsMath() {}

    public static final int USAGE_DAYS = 14;
    public static final int COVER_DAYS = 7;

    public static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    /** Why an item is on the reorder list, or null when it is fine. */
    public static String reorderReason(double qty, double min, Double avgDaily, int coverDays) {
        if (qty <= 0) return "OUT";
        if (qty <= min) return "BELOW_MIN";
        if (avgDaily != null && avgDaily > 0 && qty / avgDaily < coverDays) return "RUNS_OUT_SOON";
        return null;
    }

    /** Days the stock lasts at the recent pace; null when nothing was used lately. */
    public static Double daysLeft(double qty, Double avgDaily) {
        if (avgDaily == null || avgDaily <= 0) return null;
        return round(Math.max(0, qty) / avgDaily, 1);
    }

    /**
     * Stock units to order: enough to reach twice the minimum, or a week of use, whichever is more; rounded up to whole packs
     * when the item is bought in packs. Never negative; below-zero stock must be covered too.
     */
    public static double suggestedUnits(double qty, double min, Double avgDaily, Double packSize, int coverDays) {
        double target = Math.max(min * 2, (avgDaily != null ? avgDaily : 0) * coverDays);
        double need = Math.max(0, target - qty);
        if (need <= 0) return 0;
        if (packSize != null && packSize > 1) return round(Math.ceil(need / packSize - 1e-9) * packSize, 3);
        return round(Math.ceil(need - 1e-9), 3);
    }

    /** Percentage change from previous to latest, null when there is no usable previous figure. */
    public static Double changePercent(double previous, double latest) {
        if (previous <= 0) return null;
        return round((latest - previous) / previous * 100, 1);
    }

    /** Profit as a share of the selling price; null for a dish with no price. */
    public static Double marginPercent(double price, double cost) {
        if (price <= 0) return null;
        return round((price - cost) / price * 100, 1);
    }

    /** What a purchase line paid per stock unit (price paid for the line divided by what went into stock). */
    public static double unitCost(double lineTotal, Double stockQuantity, double unitPrice) {
        if (stockQuantity != null && stockQuantity > 0 && lineTotal > 0) return round(lineTotal / stockQuantity, 4);
        return unitPrice;
    }

    /** Counted minus book; null while the item was not counted. */
    public static Double variance(double book, Double counted) {
        return counted == null ? null : round(counted - book, 3);
    }
}
