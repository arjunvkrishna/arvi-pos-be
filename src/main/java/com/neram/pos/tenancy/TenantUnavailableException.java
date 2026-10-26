package com.neram.pos.tenancy;

/** The cafe exists but cannot serve requests (unknown, suspended, still provisioning or failed). */
public class TenantUnavailableException extends RuntimeException {
    private final int httpStatus;

    public TenantUnavailableException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
