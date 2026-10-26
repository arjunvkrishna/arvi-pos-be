package com.neram.pos.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockUnitsTest {

    @Test
    @DisplayName("A pack size of 1 or less (or missing) means the item has no pack")
    void noPackWhenOneOrLess() {
        assertNull(StockUnits.normalizePackSize(null));
        assertNull(StockUnits.normalizePackSize(0.0));
        assertNull(StockUnits.normalizePackSize(1.0));
        assertNull(StockUnits.normalizePackSize(-5.0));
        assertNull(StockUnits.normalizePackSize(Double.NaN));
        assertEquals(12.0, StockUnits.normalizePackSize(12.0));
    }

    @Test
    @DisplayName("Absurd pack sizes are refused")
    void hugePackRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockUnits.normalizePackSize(1e9));
    }

    @Test
    @DisplayName("10 cases of 12 bottles is 120 bottles; 1.5 packs of 4 is 6 pieces")
    void packsToStockUnits() {
        assertEquals(120.0, StockUnits.toStockQuantity(10, true, 12.0));
        assertEquals(6.0, StockUnits.toStockQuantity(1.5, true, 4.0));
        assertEquals(5.0, StockUnits.toStockQuantity(5, false, 12.0));
    }

    @Test
    @DisplayName("Entering packs for an item without a pack is an error, not a silent 1:1")
    void packsWithoutPackRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockUnits.toStockQuantity(2, true, null));
        assertThrows(IllegalArgumentException.class, () -> StockUnits.toStockQuantity(2, true, 1.0));
    }

    @Test
    @DisplayName("Binary floating-point noise is rounded away")
    void roundingNoise() {
        assertEquals(0.3, StockUnits.round3(0.1 + 0.2));
        assertEquals(8.5, StockUnits.round3(34 / 4.0));
    }

    @Test
    @DisplayName("Pack names are trimmed and capped")
    void packNames() {
        assertNull(StockUnits.normalizePackName("   "));
        assertNull(StockUnits.normalizePackName(null));
        assertEquals("case", StockUnits.normalizePackName("  case "));
        assertEquals(50, StockUnits.normalizePackName("x".repeat(80)).length());
    }
}
