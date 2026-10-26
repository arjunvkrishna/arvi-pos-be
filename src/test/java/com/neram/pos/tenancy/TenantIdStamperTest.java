package com.neram.pos.tenancy;

import com.neram.pos.entity.Bill;
import com.neram.pos.entity.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantIdStamperTest {

    private final TenantIdStamper stamper = new TenantIdStamper();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void newRowsRecordTheCafeTheyWereCreatedFor() {
        TenantContext.set(new TenantInfo(42, "bluedoor"));
        Category category = new Category();
        Bill bill = new Bill();

        stamper.stamp(category);
        stamper.stamp(bill);

        assertEquals(42L, category.getTenantId());
        assertEquals(42L, bill.getTenantId());
    }

    @Test
    void aStaleOrForgedTenantIdOnTheEntityIsOverwrittenWithTheRealCafe() {
        TenantContext.set(new TenantInfo(42, "bluedoor"));
        Category category = new Category();
        category.setTenantId(1L);

        stamper.stamp(category);

        assertEquals(42L, category.getTenantId());
    }

    @Test
    void entitiesWithoutATenantColumnAreIgnored() {
        TenantContext.set(new TenantInfo(42, "bluedoor"));
        assertDoesNotThrow(() -> stamper.stamp(new Object()));
    }

    @Test
    void withoutACafeNothingIsChanged() {
        Category category = new Category();
        stamper.stamp(category);
        assertNull(category.getTenantId());
    }
}
