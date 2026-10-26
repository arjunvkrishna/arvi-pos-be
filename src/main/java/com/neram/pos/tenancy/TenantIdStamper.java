package com.neram.pos.tenancy;

import jakarta.persistence.PrePersist;

/**
 * Stamps every new row of a {@link TenantScoped} entity with the id of the cafe doing the work, so each row records
 * which restaurant it belongs to without every service having to remember to set it. Registered for all entities in
 * META-INF/orm.xml. The cafe always comes from the request context, never from client input.
 */
public class TenantIdStamper {

    @PrePersist
    public void stamp(Object entity) {
        if (entity instanceof TenantScoped scoped) {
            TenantContext.currentId().ifPresent(scoped::setTenantId);
        }
    }
}
