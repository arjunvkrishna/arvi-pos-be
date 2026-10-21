package com.neram.pos.entity;

public enum PrintJobStatus {
    PENDING,
    CLAIMED,
    SENT_TO_PRINTER,
    COMPLETED,
    RETRY_PENDING,
    FAILED,
    NEEDS_REVIEW
}
