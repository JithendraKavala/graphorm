package com.graphorm.core.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-local management utility for the current tenant identifier.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> PROGRAMMATIC_OVERRIDE = new ThreadLocal<>();
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
     * Resolves the active programmatic tenant override, if any.
     * Programmatic overrides are set explicitly in server code via {@link #runAsTenant},
     * {@link #supplyAsTenant}, or {@link #setProgrammaticTenantId}.
     *
     * @return an Optional containing the active programmatic tenant override, or empty
     */
    public static Optional<String> getProgrammaticTenantId() {
        return Optional.ofNullable(PROGRAMMATIC_OVERRIDE.get());
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
     * Sets an explicit programmatic tenant override on the active thread.
     *
     * @param tenantId the tenant ID, or null to clear
     */
    public static void setProgrammaticTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            String trimmed = tenantId.trim();
            PROGRAMMATIC_OVERRIDE.set(trimmed);
            CURRENT_TENANT.set(trimmed);
        } else {
            PROGRAMMATIC_OVERRIDE.remove();
            CURRENT_TENANT.remove();
        }
    }

    /**
     * Clears any active programmatic tenant override from the active thread.
     */
    public static void clearProgrammaticTenantId() {
        PROGRAMMATIC_OVERRIDE.remove();
    }

    /**
     * Clears the current tenant identifier from the active thread.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
        PROGRAMMATIC_OVERRIDE.remove();
    }

    /**
     * Functional interface representing a scoped tenant context that restores the previous context on close.
     */
    @FunctionalInterface
    public interface TenantScope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Opens a trusted programmatic tenant scope on the active thread, binding both the active tenant
     * and the programmatic override. Restores previous context when closed.
     *
     * @param tenantId the tenant ID to bind (must not be null)
     * @return a {@link TenantScope} that restores previous context upon closing
     */
    public static TenantScope openProgrammaticScope(String tenantId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        String prevProgrammatic = PROGRAMMATIC_OVERRIDE.get();
        String prevTenant = CURRENT_TENANT.get();
        String trimmed = tenantId.trim();
        PROGRAMMATIC_OVERRIDE.set(trimmed);
        CURRENT_TENANT.set(trimmed);
        return () -> {
            if (prevProgrammatic != null) {
                PROGRAMMATIC_OVERRIDE.set(prevProgrammatic);
            } else {
                PROGRAMMATIC_OVERRIDE.remove();
            }
            if (prevTenant != null) {
                CURRENT_TENANT.set(prevTenant);
            } else {
                CURRENT_TENANT.remove();
            }
        };
    }

    /**
     * Opens an HTTP header tenant scope on the active thread.
     * <p>
     * <b>Security Invariant:</b> Never overwrites an active trusted programmatic override with an HTTP header.
     * If an explicit programmatic override is already active on the thread, this scope is a no-op and leaves
     * the programmatic override intact. Otherwise, binds the header tenant to {@code CURRENT_TENANT}
     * and restores previous context upon closing.
     *
     * @param tenantId the candidate tenant identifier
     * @return a {@link TenantScope} that restores previous context upon closing
     */
    public static TenantScope openHeaderScope(String tenantId) {
        String prevProgrammatic = PROGRAMMATIC_OVERRIDE.get();
        if (prevProgrammatic != null) {
            // An active trusted programmatic override is present: NEVER overwrite it with an HTTP header
            return () -> {};
        }
        String prevTenant = CURRENT_TENANT.get();
        if (tenantId != null && !tenantId.isBlank()) {
            CURRENT_TENANT.set(tenantId.trim());
        }
        return () -> {
            if (prevTenant != null) {
                CURRENT_TENANT.set(prevTenant);
            } else {
                CURRENT_TENANT.remove();
            }
            if (prevProgrammatic != null) {
                PROGRAMMATIC_OVERRIDE.set(prevProgrammatic);
            } else {
                PROGRAMMATIC_OVERRIDE.remove();
            }
        };
    }

    /**
     * Executes the given action bound to the specified tenant ID as a trusted programmatic override,
     * guaranteeing restoration of previous context on completion.
     *
     * @param tenantId the tenant ID to bind
     * @param action   the action to execute
     */
    public static void runAsTenant(String tenantId, Runnable action) {
        Objects.requireNonNull(action, "Action must not be null");
        try (TenantScope ignored = openProgrammaticScope(tenantId)) {
            action.run();
        }
    }

    /**
     * Evaluates the given supplier bound to the specified tenant ID as a trusted programmatic override,
     * guaranteeing restoration of previous context on completion.
     *
     * @param tenantId the tenant ID to bind
     * @param supplier the supplier to evaluate
     * @param <T>      result type
     * @return the value returned by the supplier
     */
    public static <T> T supplyAsTenant(String tenantId, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "Supplier must not be null");
        try (TenantScope ignored = openProgrammaticScope(tenantId)) {
            return supplier.get();
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
