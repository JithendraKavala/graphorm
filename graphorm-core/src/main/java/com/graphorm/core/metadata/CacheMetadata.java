package com.graphorm.core.metadata;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable cache metadata declared via {@code @cached} or {@code @@cached} on models and view models.
 * Controls time-to-live (TTL) and dependency cache invalidation tags.
 *
 * @param isCached whether caching is enabled for this entity
 * @param ttl      optional time-to-live duration for cached query results
 * @param tags     set of invalidation tags (e.g. dependent entity names)
 */
public record CacheMetadata(
        boolean isCached,
        Duration ttl,
        Set<String> tags
) {

    public static final CacheMetadata NOT_CACHED = new CacheMetadata(false, null, Set.of());

    public CacheMetadata {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static CacheMetadata notCached() {
        return NOT_CACHED;
    }

    public static CacheMetadata of(Duration ttl, Set<String> tags) {
        return new CacheMetadata(true, ttl, tags);
    }

    public boolean hasTtl() {
        return ttl != null;
    }
}
