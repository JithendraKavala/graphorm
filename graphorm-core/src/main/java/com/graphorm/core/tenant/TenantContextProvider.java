package com.graphorm.core.tenant;

import java.util.Optional;

/**
 * Service Provider Interface (SPI) for resolving the active tenant identifier.
 */
@FunctionalInterface
public interface TenantContextProvider {

    /**
     * Resolves the current tenant identifier, if set.
     *
     * @return an {@link Optional} containing the active tenant ID, or empty if no tenant is bound
     */
    Optional<String> getCurrentTenantId();
}
