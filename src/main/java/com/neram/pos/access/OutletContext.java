package com.neram.pos.access;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Which branch the current work is limited to.
 * <ul>
 *   <li>empty: not limited (background jobs, owners and main managers looking at the whole cafe);</li>
 *   <li>an id: only that outlet's rows (plus cafe-wide shared rows) are visible and writable.</li>
 * </ul>
 * Set per request by JwtAuthenticationFilter from the user's role and home outlet; branch-level users cannot widen it.
 */
public final class OutletContext {

    /** Matches no outlet: a branch-level user without an assigned outlet sees only cafe-wide shared rows. */
    public static final long NO_OUTLET = -1L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private static volatile Runnable onChange = () -> { };

    private OutletContext() {
    }

    /** Registered by OutletFilterSupport so the Hibernate session filter follows context changes. */
    static void setChangeListener(Runnable listener) {
        onChange = listener == null ? () -> { } : listener;
    }

    public static Optional<Long> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** For SpEL in native queries ({@code :#{T(...OutletContext).currentIdOrNull()}}). */
    public static Long currentIdOrNull() {
        return CURRENT.get();
    }

    public static void set(Long outletId) {
        if (outletId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outletId);
        }
        onChange.run();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runAs(Long outletId, Runnable task) {
        Long previous = CURRENT.get();
        set(outletId);
        try {
            task.run();
        } finally {
            set(previous);
        }
    }

    public static <T> T callAs(Long outletId, Supplier<T> task) {
        Long previous = CURRENT.get();
        set(outletId);
        try {
            return task.get();
        } finally {
            set(previous);
        }
    }

    /** Runs without branch limits, e.g. a cafe-wide report started by a background job. */
    public static <T> T callUnrestricted(Supplier<T> task) {
        return callAs(null, task);
    }
}
