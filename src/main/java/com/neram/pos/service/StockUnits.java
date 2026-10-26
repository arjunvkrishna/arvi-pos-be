package com.neram.pos.service;

/**
 * Pack/stock unit arithmetic. Stock is always held in the item's stock unit
 * (bottle, packet, kg ...). A pack (case, carton) is just "N stock units".
 */
public final class StockUnits {

    public static final double MAX_PACK_SIZE = 100000;

    private StockUnits() {}

    /** A pack size only counts when it is greater than 1; otherwise the item has no pack. */
    public static Double normalizePackSize(Double packSize) {
        if (packSize == null || packSize.isNaN() || packSize.isInfinite() || packSize <= 1.0) return null;
        if (packSize > MAX_PACK_SIZE) {
            throw new IllegalArgumentException("Pack size is too large (maximum " + (long) MAX_PACK_SIZE + ").");
        }
        return round3(packSize);
    }

    public static String normalizePackName(String name) {
        if (name == null) return null;
        String n = name.trim();
        if (n.isEmpty()) return null;
        return n.length() > 50 ? n.substring(0, 50) : n;
    }

    /** Converts a quantity entered in packs or in stock units into stock units. */
    public static double toStockQuantity(double quantity, boolean inPacks, Double packSize) {
        if (!inPacks) return round3(quantity);
        Double size = normalizePackSize(packSize);
        if (size == null) {
            throw new IllegalArgumentException("This item has no pack size. Enter the quantity in its stock unit.");
        }
        return round3(quantity * size);
    }

    /** Rounds away binary noise such as 0.30000000000000004 while keeping 3 decimals. */
    public static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
