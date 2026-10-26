package com.neram.pos.access;

import com.neram.pos.entity.Bill;
import com.neram.pos.entity.DiningTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutletScopeListenerTest {

    private final OutletScopeListener listener = new OutletScopeListener();

    @AfterEach
    void clear() {
        OutletContext.clear();
    }

    private static Bill billOf(Long outlet) {
        Bill b = new Bill();
        b.setOutletId(outlet);
        return b;
    }

    @Test
    void newRowsOfABranchUserBelongToThatBranch() {
        OutletContext.set(2L);
        Bill bill = billOf(null);
        listener.stampAndCheckNew(bill);
        assertEquals(2L, bill.getOutletId());
    }

    @Test
    void aBranchUserCannotCreateOrChangeARowOfAnotherBranch() {
        OutletContext.set(2L);
        assertThrows(OutletAccessDeniedException.class, () -> listener.stampAndCheckNew(billOf(1L)));
        assertThrows(OutletAccessDeniedException.class, () -> listener.checkUpdated(billOf(1L)));
    }

    @Test
    void loadingAnotherBranchsRowByIdIsRefused() {
        OutletContext.set(2L);
        assertThrows(OutletAccessDeniedException.class, () -> listener.checkLoaded(billOf(1L)));
        assertDoesNotThrow(() -> listener.checkLoaded(billOf(2L)));
        assertDoesNotThrow(() -> listener.checkLoaded(billOf(null)), "cafe-wide shared rows are open to every branch");
    }

    @Test
    void ownersAndBackgroundJobsAreNotLimited() {
        Bill other = billOf(5L);
        assertDoesNotThrow(() -> listener.checkLoaded(other));
        assertDoesNotThrow(() -> listener.stampAndCheckNew(other));
        assertEquals(5L, other.getOutletId());
        Bill unscoped = billOf(null);
        listener.stampAndCheckNew(unscoped);
        assertNull(unscoped.getOutletId(), "no branch selected: the row stays cafe-wide");
    }

    @Test
    void aUserWithoutABranchSeesNoBranchRows() {
        OutletContext.set(OutletContext.NO_OUTLET);
        assertThrows(OutletAccessDeniedException.class, () -> listener.checkLoaded(billOf(1L)));
        DiningTable t = new DiningTable();
        listener.stampAndCheckNew(t);
        assertNull(t.getOutletId(), "never stamped with the placeholder id");
    }

    @Test
    void entitiesThatAreNotBranchOwnedAreIgnored() {
        OutletContext.set(2L);
        assertDoesNotThrow(() -> listener.checkLoaded(new Object()));
    }

    @Test
    void runAsRestoresThePreviousLimit() {
        OutletContext.set(1L);
        OutletContext.runAs(2L, () -> assertEquals(2L, OutletContext.currentIdOrNull()));
        assertEquals(1L, OutletContext.currentIdOrNull());
        OutletContext.callUnrestricted(() -> {
            assertNull(OutletContext.currentIdOrNull());
            return null;
        });
        assertEquals(1L, OutletContext.currentIdOrNull());
    }
}
