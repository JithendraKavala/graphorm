package com.graphorm.core.tenant;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * ThreadLocal-backed bypass context for super-tenant or administrative operations
 * that legitimately need to bypass tenant scoping.
 */
public final class TenantBypassContext {

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantBypassContext() {}

    /**
     * Checks whether tenant enforcement is currently bypassed for the active thread.
     *
     * @return true if bypassed, false otherwise
     */
    public static boolean isBypassed() {
        return Boolean.TRUE.equals(BYPASS.get());
    }

    /**
     * Explicitly sets the bypass state for the current thread.
     *
     * @param bypassed true to bypass tenant scoping
     */
    public static void setBypassed(boolean bypassed) {
        if (bypassed) {
            BYPASS.set(Boolean.TRUE);
        } else {
            BYPASS.remove();
        }
    }

    /**
     * Clears any active bypass state on the current thread.
     */
    public static void clear() {
        BYPASS.remove();
    }

    /**
     * Executes the given action with tenant scoping bypassed, restoring previous bypass state afterward.
     *
     * @param action the action to execute
     */
    public static void runWithBypass(Runnable action) {
        Objects.requireNonNull(action, "Action must not be null");
        boolean previous = isBypassed();
        BYPASS.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            if (previous) {
                BYPASS.set(Boolean.TRUE);
            } else {
                BYPASS.remove();
            }
        }
    }

    /**
     * Supplies a result from the given supplier with tenant scoping bypassed,
     * restoring previous bypass state afterward.
     *
     * @param supplier the supplier to invoke
     * @param <T>      result type
     * @return the result of the supplier
     */
    public static <T> T supplyWithBypass(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "Supplier must not be null");
        boolean previous = isBypassed();
        BYPASS.set(Boolean.TRUE);
        try {
            return supplier.get();
        } finally {
            if (previous) {
                BYPASS.set(Boolean.TRUE);
            } else {
                BYPASS.remove();
            }
        }
    }
}
