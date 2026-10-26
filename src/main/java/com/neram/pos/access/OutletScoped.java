package com.neram.pos.access;

/**
 * An entity that belongs to one branch (outlet). A null outlet means "shared by the whole cafe".
 * Rows are filtered by outlet for branch-level users (see OutletFilterSupport) and guarded on load and save.
 */
public interface OutletScoped {
    Long getOutletId();

    void setOutletId(Long outletId);
}
