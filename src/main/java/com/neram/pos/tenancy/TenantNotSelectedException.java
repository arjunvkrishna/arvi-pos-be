package com.neram.pos.tenancy;

/**
 * Thrown when tenant data is touched while no cafe is selected for the current thread. Tenant data access fails
 * closed instead of falling back to a default database, so one cafe can never read another cafe's rows.
 */
public class TenantNotSelectedException extends RuntimeException {
    public TenantNotSelectedException(String message) {
        super(message);
    }
}
