package com.graphorm.core.metadata;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Common contract for all top-level schema entity metadata: regular models,
 * view models, and composite types.
 */
public interface EntityMetadata {

    /**
     * Returns the declared name of this entity in the schema.
     *
     * @return the entity name
     */
    String name();

    /**
     * Returns the architectural entity primitive type.
     *
     * @return the entity type (MODEL, VIEW, or COMPOSITE_TYPE)
     */
    EntityType entityType();

    /**
     * Indicates whether this entity is strictly read-only.
     * View models must always return {@code true}.
     *
     * @return true if read-only, false if write operations are permitted
     */
    boolean isReadOnly();

    /**
     * Returns an unmodifiable list of all fields declared on this entity.
     *
     * @return immutable list of field metadata
     */
    List<FieldMetadata> fields();

    /**
     * Returns the list of authorization roles required to access this entity, if declared.
     *
     * @return immutable list of auth roles, or empty list if unrestricted
     */
    default List<String> authRoles() {
        return List.of();
    }

    /**
     * Indicates whether this entity has role-based authorization requirements declared.
     *
     * @return true if entity has required auth roles
     */
    default boolean hasAuthRoles() {
        return authRoles() != null && !authRoles().isEmpty();
    }

    /**
     * Returns the exposure configuration for this entity in GraphQL APIs.
     *
     * @return the exposure metadata
     */
    default ExposureMetadata exposure() {
        return isReadOnly() ? ExposureMetadata.queriesOnly() : ExposureMetadata.all();
    }

    /**
     * Returns whether root queries for this entity are exposed in GraphQL.
     */
    default boolean exposeQueries() {
        return exposure().queries();
    }

    /**
     * Returns whether mutations for this entity are exposed in GraphQL.
     */
    default boolean exposeMutations() {
        return exposure().mutations();
    }

    /**
     * Indicates whether this entity is multi-tenant and requires tenant partition isolation.
     *
     * @return true if multi-tenant
     */
    default boolean isMultiTenant() {
        return false;
    }

    /**
     * Returns the physical database column name representing the tenant isolation key.
     *
     * @return the tenant column name, or null if not multi-tenant
     */
    default String tenantColumn() {
        return null;
    }

    /**
     * Looks up the tenant isolation field on this entity, if declared.
     *
     * @return an {@link Optional} containing the tenant field metadata if present
     */
    default Optional<FieldMetadata> tenantField() {
        return fields().stream().filter(FieldMetadata::isTenantId).findFirst();
    }

    /**
     * Returns the caching metadata configuration declared for this entity.
     *
     * @return the cache metadata (defaults to not cached)
     */
    default CacheMetadata cacheMetadata() {
        return CacheMetadata.NOT_CACHED;
    }

    /**
     * Indicates whether query results for this entity should be cached.
     *
     * @return true if caching is enabled
     */
    default boolean isCached() {
        return cacheMetadata() != null && cacheMetadata().isCached();
    }

    /**
     * Returns the cache TTL duration for this entity, or null if indefinite or not cached.
     *
     * @return the cache TTL duration
     */
    default Duration cacheTtl() {
        return cacheMetadata() != null ? cacheMetadata().ttl() : null;
    }

    /**
     * Returns the set of invalidation tags declared for this entity's cache.
     *
     * @return set of cache tags
     */
    default Set<String> cacheTags() {
        return cacheMetadata() != null ? cacheMetadata().tags() : Set.of();
    }

    /**
     * Returns the real-time notification metadata configuration declared for this entity.
     *
     * @return the notification metadata (defaults to disabled)
     */
    default NotifyMetadata notifyMetadata() {
        return NotifyMetadata.DISABLED;
    }

    /**
     * Indicates whether real-time change notifications are enabled for this entity.
     *
     * @return true if notifications are enabled
     */
    default boolean isNotifyEnabled() {
        return notifyMetadata() != null && notifyMetadata().isNotifyEnabled();
    }

    /**
     * Looks up a field by its logical name.
     *
     * @param fieldName the name of the field to find
     * @return an {@link Optional} containing the field metadata if found, or empty
     */
    default Optional<FieldMetadata> findField(String fieldName) {
        if (fieldName == null) {
            return Optional.empty();
        }
        return fields().stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst();
    }

    /**
     * Retrieves a field by its logical name, or throws {@link IllegalArgumentException} if not found.
     *
     * @param fieldName the name of the field to retrieve
     * @return the field metadata
     * @throws IllegalArgumentException if no field with the given name exists
     */
    default FieldMetadata getField(String fieldName) {
        return findField(fieldName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Field '" + fieldName + "' not found on entity '" + name() + "'"));
    }

    /**
     * Checks if a field with the specified name exists on this entity.
     *
     * @param fieldName the field name to check
     * @return true if the field exists, false otherwise
     */
    default boolean hasField(String fieldName) {
        return findField(fieldName).isPresent();
    }
}
