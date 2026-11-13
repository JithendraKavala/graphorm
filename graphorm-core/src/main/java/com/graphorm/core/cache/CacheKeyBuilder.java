package com.graphorm.core.cache;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.tenant.TenantContext;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Builds deterministic, collision-resistant cache keys for L2 entity and view caching,
 * with mandatory multi-tenant partition isolation.
 */
public final class CacheKeyBuilder {

    private CacheKeyBuilder() {}

    /**
     * Builds a cache key for a unique entity lookup by ID using the effective tenant context.
     *
     * @param entityName the entity name
     * @param id         the primary key identifier
     * @return cache key formatted as {@code <entityName>:[tenant#<tenantId>:]id#<id>}
     */
    public static String forId(String entityName, Object id) {
        return forId(entityName, TenantContext.getCurrentTenantId().orElse(null), id);
    }

    /**
     * Builds a cache key for a unique entity lookup with an explicit tenant ID.
     *
     * @param entityName the entity name
     * @param tenantId   the tenant partition ID (optional)
     * @param id         the primary key identifier
     * @return cache key formatted as {@code <entityName>:[tenant#<tenantId>:]id#<id>}
     */
    public static String forId(String entityName, String tenantId, Object id) {
        Objects.requireNonNull(entityName, "entityName must not be null");
        Objects.requireNonNull(id, "id must not be null");

        StringBuilder sb = new StringBuilder(entityName);
        if (tenantId != null && !tenantId.isBlank()) {
            sb.append(":tenant#").append(tenantId.trim());
        }
        sb.append(":id#").append(id);
        return sb.toString();
    }

    /**
     * Alias for {@link #forId(String, String, Object)}.
     */
    public static String forEntityId(String entityName, String tenantId, Object id) {
        return forId(entityName, tenantId, id);
    }

    /**
     * Builds a deterministic cache key for a unique entity lookup with an explicit tenant ID,
     * entity ID, and canonical requested projection columns.
     *
     * @param entityName       the entity name
     * @param tenantId         the tenant partition ID (optional)
     * @param id               the primary key identifier
     * @param projectedColumns collection of requested columns/fields (optional)
     * @return cache key formatted as {@code <entityName>:[tenant#<tenantId>:]id#<id>[:proj#<hash>]}
     */
    public static String forEntityId(String entityName, String tenantId, Object id, Collection<String> projectedColumns) {
        Objects.requireNonNull(entityName, "entityName must not be null");
        Objects.requireNonNull(id, "id must not be null");

        StringBuilder sb = new StringBuilder(entityName);
        if (tenantId != null && !tenantId.isBlank()) {
            sb.append(":tenant#").append(tenantId.trim());
        }
        sb.append(":id#").append(id);
        if (projectedColumns != null && !projectedColumns.isEmpty()) {
            List<String> sorted = new ArrayList<>(projectedColumns);
            Collections.sort(sorted);
            sb.append(":proj#").append(hashIfLong(String.join(",", sorted)));
        }
        return sb.toString();
    }

    /**
     * Builds a cache key for a query with an arbitrary filter, ordering, and selection payload
     * using the effective tenant context.
     *
     * @param entityName the entity name
     * @param payload    the query canonical representation (e.g. where/order/limit)
     * @return cache key
     */
    public static String forQuery(String entityName, String payload) {
        return forQuery(entityName, TenantContext.getCurrentTenantId().orElse(null), payload);
    }

    /**
     * Builds a cache key for a query with an explicit tenant ID and query payload.
     *
     * @param entityName the entity name
     * @param tenantId   the tenant partition ID (optional)
     * @param payload    the query canonical representation
     * @return cache key
     */
    public static String forQuery(String entityName, String tenantId, String payload) {
        Objects.requireNonNull(entityName, "entityName must not be null");
        String safePayload = payload != null ? payload : "all";

        StringBuilder sb = new StringBuilder(entityName);
        if (tenantId != null && !tenantId.isBlank()) {
            sb.append(":tenant#").append(tenantId.trim());
        }
        sb.append(":query#").append(hashIfLong(safePayload));
        return sb.toString();
    }

    /**
     * Deterministically hashes a {@link SelectQuery} AST into a stable hex representation.
     *
     * @param query the query AST
     * @return query hash
     */
    public static String hashQuery(SelectQuery query) {
        if (query == null) {
            return "all";
        }
        return hashIfLong(query.toString());
    }

    private static String hashIfLong(String text) {
        if (text.length() <= 128) {
            return text;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
