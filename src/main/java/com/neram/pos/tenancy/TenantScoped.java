package com.neram.pos.tenancy;

/** An entity whose rows carry the id of the cafe (restaurant) they belong to. */
public interface TenantScoped {
    Long getTenantId();

    void setTenantId(Long tenantId);
}
