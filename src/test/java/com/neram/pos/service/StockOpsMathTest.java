package com.neram.pos.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockOpsMathTest {

    @Test
    void reorderReasonCoversOutBelowMinAndRunningOutSoon() {
        assertEquals("OUT", StockOpsMath.reorderReason(0, 5, null, 7));
        assertEquals("OUT", StockOpsMath.reorderReason(-3, 5, 2.0, 7));
        assertEquals("BELOW_MIN", StockOpsMath.reorderReason(5, 5, null, 7));
        assertEquals("RUNS_OUT_SOON", StockOpsMath.reorderReason(20, 5, 4.0, 7));   // 5 days left
        assertNull(StockOpsMath.reorderReason(40, 5, 4.0, 7));                      // 10 days left
        assertNull(StockOpsMath.reorderReason(40, 5, null, 7));
        assertNull(StockOpsMath.reorderReason(40, 5, 0.0, 7));
    }

    @Test
    void daysLeftNeedsRecentUse() {
        assertEquals(5.0, StockOpsMath.daysLeft(20, 4.0));
        assertNull(StockOpsMath.daysLeft(20, null));
        assertNull(StockOpsMath.daysLeft(20, 0.0));
        assertEquals(0.0, StockOpsMath.daysLeft(-4, 2.0));
    }

    @Test
    void suggestedQuantityReachesTwiceTheMinimumOrAWeekOfUse() {
        assertEquals(10.0, StockOpsMath.suggestedUnits(0, 5, null, null, 7));          // 2 x min
        assertEquals(28.0, StockOpsMath.suggestedUnits(0, 5, 4.0, null, 7));           // a week of use beats 2 x min
        assertEquals(8.0, StockOpsMath.suggestedUnits(2, 5, null, null, 7));
        assertEquals(0.0, StockOpsMath.suggestedUnits(50, 5, 1.0, null, 7));
    }

    @Test
    void packedItemsAreRoundedUpToWholePacksAndNegativeStockIsCovered() {
        assertEquals(12.0, StockOpsMath.suggestedUnits(2, 5, null, 12.0, 7));          // need 8 -> 1 case
        assertEquals(24.0, StockOpsMath.suggestedUnits(0, 5, 3.0, 12.0, 7));           // need 21 -> 2 cases
        assertEquals(36.0, StockOpsMath.suggestedUnits(-10, 5, 3.0, 12.0, 7));         // 31 -> 3 cases: the missing 10 is replaced too
        assertEquals(12.0, StockOpsMath.suggestedUnits(0, 6, null, 12.0, 7));          // exactly one case, not two
    }

    @Test
    void costChangeAndMarginFigures() {
        assertEquals(25.0, StockOpsMath.changePercent(20, 25));
        assertEquals(-10.0, StockOpsMath.changePercent(20, 18));
        assertNull(StockOpsMath.changePercent(0, 18));
        assertEquals(60.0, StockOpsMath.marginPercent(100, 40));
        assertEquals(-20.0, StockOpsMath.marginPercent(100, 120));
        assertNull(StockOpsMath.marginPercent(0, 5));
    }

    @Test
    void purchaseLineCostIsPerStockUnitNotPerPack() {
        assertEquals(20.0, StockOpsMath.unitCost(240, 12.0, 240));    // a case of 12 at 240 = 20 per bottle
        assertEquals(50.0, StockOpsMath.unitCost(0, null, 50));        // no total/stock quantity: the typed price
        assertEquals(3.5, StockOpsMath.unitCost(35, 10.0, 35));
    }

    @Test
    void countVarianceIsCountedMinusBook() {
        assertEquals(-2.0, StockOpsMath.variance(10, 8.0));
        assertEquals(3.0, StockOpsMath.variance(10, 13.0));
        assertEquals(0.0, StockOpsMath.variance(10, 10.0));
        assertNull(StockOpsMath.variance(10, null));
    }
}
