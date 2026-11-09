package com.graphorm.core.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-local management utility for the current tenant identifier.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static volatile TenantContextProvider provider = new DefaultTenantContextProvider();

    private TenantContext() {}

    /**
     * Resolves the current tenant identifier from the ThreadLocal storage.
     *
     * @return an {@link Optional} containing the active tenant ID, or empty
     */
    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Resolves the current tenant identifier from the configured provider or ThreadLocal storage.
     *
     * @return an Optional containing the active tenant ID, or empty
     */
    public static Optional<String> getCurrentTenantId() {
        return provider != null ? provider.getCurrentTenantId() : getTenantId();
    }

    /**
     * Sets the tenant identifier on the active thread.
     *
     * @param tenantId the tenant ID, or null to clear
     */
    public static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            CURRENT_TENANT.set(tenantId.trim());
        } else {
            CURRENT_TENANT.remove();
        }
    }

    /**
     * Clears the current tenant identifier from the active thread.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Executes the given action bound to the specified tenant ID,
     * guaranteeing restoration of previous context on completion.
     *
     * @param tenantId the tenant ID to bind
     * @param action   the action to execute
     */
    public static void runAsTenant(String tenantId, Runnable action) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(action, "Action must not be null");
        String previous = CURRENT_TENANT.get();
        setTenantId(tenantId);
        try {
            action.run();
        } finally {
            CURRENT_TENANT.set(previous);
        }
    }

    /**
     * Evaluates the given supplier bound to the specified tenant ID,
     * guaranteeing restoration of previous context on completion.
     *
     * @param tenantId the tenant ID to bind
     * @param supplier the supplier to evaluate
     * @param <T>      result type
     * @return the value returned by the supplier
     */
    public static <T> T supplyAsTenant(String tenantId, Supplier<T> supplier) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(supplier, "Supplier must not be null");
        String previous = CURRENT_TENANT.get();
        setTenantId(tenantId);
        try {
            return supplier.get();
        } finally {
            CURRENT_TENANT.set(previous);
        }
    }

    /**
     * Returns the global or configured {@link TenantContextProvider}.
     *
     * @return the active provider
     */
    public static TenantContextProvider getProvider() {
        return provider;
    }

    /**
     * Sets the global or configured {@link TenantContextProvider}.
     *
     * @param newProvider the provider to use, or null to revert to default
     */
    public static void setProvider(TenantContextProvider newProvider) {
        provider = newProvider != null ? newProvider : new DefaultTenantContextProvider();
    }

    /**
     * Checks if tenant scoping is currently bypassed for the active thread.
     */
    public static boolean isBypassed() {
        return TenantBypassContext.isBypassed();
    }

    /**
     * Executes an action with tenant scoping bypassed.
     */
    public static void runWithBypass(Runnable action) {
        TenantBypassContext.runWithBypass(action);
    }

    /**
     * Supplies a result with tenant scoping bypassed.
     */
    public static <T> T supplyWithBypass(Supplier<T> supplier) {
        return TenantBypassContext.supplyWithBypass(supplier);
    }
}
