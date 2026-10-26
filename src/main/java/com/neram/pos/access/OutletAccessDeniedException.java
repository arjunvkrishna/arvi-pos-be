package com.neram.pos.access;

import org.springframework.security.access.AccessDeniedException;

/** A branch-level user touched a record that belongs to another branch. */
public class OutletAccessDeniedException extends AccessDeniedException {
    public OutletAccessDeniedException() {
        super("Access denied: this record belongs to another branch");
    }
}
