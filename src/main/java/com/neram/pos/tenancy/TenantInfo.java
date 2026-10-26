package com.neram.pos.tenancy;

/** Identity of the cafe (tenant) the current work belongs to. */
public record TenantInfo(long id, String code) {
}
