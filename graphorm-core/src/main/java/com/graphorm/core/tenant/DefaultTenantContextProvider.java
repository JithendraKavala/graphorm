package com.graphorm.core.tenant;

import java.util.Optional;

/**
 * Default implementation of {@link TenantContextProvider} backed by {@link TenantContext}.
 */
public class DefaultTenantContextProvider implements TenantContextProvider {

    @Override
    public Optional<String> getCurrentTenantId() {
        return TenantContext.getTenantId();
    }
}
