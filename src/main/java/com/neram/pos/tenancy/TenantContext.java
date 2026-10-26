package com.neram.pos.tenancy;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Holds the cafe the current thread is working for. Request threads get it from the JWT (see
 * JwtAuthenticationFilter); background jobs set it explicitly with {@link #runAs}. The routing datasource reads it
 * to pick the cafe's own database.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantInfo> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<TenantInfo> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Optional<Long> currentId() {
        TenantInfo info = CURRENT.get();
        return info == null ? Optional.empty() : Optional.of(info.id());
    }

    public static TenantInfo require() {
        TenantInfo info = CURRENT.get();
        if (info == null) {
            throw new TenantNotSelectedException("No cafe selected for this request");
        }
        return info;
    }

    public static long requireId() {
        return require().id();
    }

    public static void set(TenantInfo info) {
        CURRENT.set(info);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs the task as the given cafe and restores whatever was selected before. */
    public static void runAs(TenantInfo info, Runnable task) {
        TenantInfo previous = CURRENT.get();
        CURRENT.set(info);
        try {
            task.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAs(TenantInfo info, Supplier<T> task) {
        TenantInfo previous = CURRENT.get();
        CURRENT.set(info);
        try {
            return task.get();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAsChecked(TenantInfo info, Callable<T> task) throws Exception {
        TenantInfo previous = CURRENT.get();
        CURRENT.set(info);
        try {
            return task.call();
        } finally {
            restore(previous);
        }
    }

    /** Wraps a task so it runs for the cafe selected right now, even on another thread. */
    public static Runnable bind(Runnable task) {
        TenantInfo captured = CURRENT.get();
        if (captured == null) {
            return task;
        }
        return () -> runAs(captured, task);
    }

    private static void restore(TenantInfo previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
