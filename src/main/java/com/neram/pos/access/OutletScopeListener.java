package com.neram.pos.access;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * JPA listener for every entity (META-INF/orm.xml). The Hibernate filter hides other branches' rows from queries;
 * this closes the remaining gaps: loads by id, and writes that name another branch.
 */
public class OutletScopeListener {

    @PostLoad
    public void checkLoaded(Object entity) {
        if (entity instanceof OutletScoped scoped && !scoped.staysInEveryBranch()) {
            check(scoped.getOutletId());
        }
    }

    @PrePersist
    public void stampAndCheckNew(Object entity) {
        if (entity instanceof OutletScoped scoped && !scoped.staysInEveryBranch()) {
            Long limit = OutletContext.current().orElse(null);
            if (scoped.getOutletId() == null && limit != null && limit != OutletContext.NO_OUTLET) {
                scoped.setOutletId(limit);
            }
            check(scoped.getOutletId());
        }
    }

    @PreUpdate
    public void checkUpdated(Object entity) {
        if (entity instanceof OutletScoped scoped && !scoped.staysInEveryBranch()) {
            check(scoped.getOutletId());
        }
    }

    private static void check(Long recordOutlet) {
        Long limit = OutletContext.current().orElse(null);
        if (limit != null && recordOutlet != null && !recordOutlet.equals(limit)) {
            throw new OutletAccessDeniedException();
        }
    }
}
