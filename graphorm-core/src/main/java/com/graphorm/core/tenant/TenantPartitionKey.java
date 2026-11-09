package com.graphorm.core.tenant;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key pairing an active tenant identifier with an entity join key or primary key.
 * Used by relationship loaders and GraphQL DataLoaders to guarantee tenant partition isolation.
 */
public record TenantPartitionKey(String tenantId, Object key) implements Serializable {

    public TenantPartitionKey {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(key, "Key must not be null");
    }

    public static TenantPartitionKey of(String tenantId, Object key) {
        return new TenantPartitionKey(tenantId, key);
    }
}
