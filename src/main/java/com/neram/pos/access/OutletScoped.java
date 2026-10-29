package com.neram.pos.access;

/**
 * An entity that belongs to one branch (outlet). A null outlet means "shared by the whole cafe".
 * Rows are filtered by outlet for branch-level users (see OutletFilterSupport) and guarded on load and save.
 */
public interface OutletScoped {
    Long getOutletId();

    void setOutletId(Long outletId);

    /**
     * True for a record that works in every branch even though a home branch is stored on it, such as an owner's login.
     * Such a record is never hidden from, nor refused in, another branch.
     */
    default boolean staysInEveryBranch() {
        return false;
    }
}
