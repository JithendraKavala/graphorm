package com.graphorm.core.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface (SPI) for GraphORM L2 Entity & View Result Cache.
 * Provides distributed and in-memory cache operations with time-to-live (TTL) expiration
 * and tag-based reverse index invalidation.
 */
public interface GraphOrmCacheManager {

    /**
     * Retrieves a cached value by its cache key, typed as requested.
     *
     * @param cacheKey unique cache key
     * @param type     expected result class
     * @param <T>      result type
     * @return an Optional containing the cached value if present, or empty
     */
    <T> Optional<T> get(String cacheKey, Class<T> type);

    /**
     * Stores a value in the cache with the given key, TTL expiration, and invalidation tags.
     *
     * @param cacheKey unique cache key
     * @param value    value to store
     * @param ttl      time-to-live duration, or null for default/indefinite
     * @param tags     set of invalidation tags (e.g. dependent entity names)
     * @param <T>      value type
     */
    <T> void put(String cacheKey, T value, Duration ttl, Set<String> tags);

    /**
     * Evicts all cached entries associated with ANY of the specified tags.
     * Guarantees read consistency across dependent views when an underlying entity is mutated.
     *
     * @param tags set of tags to evict
     */
    void evictByTags(Set<String> tags);

    /**
     * Clears all entries and tag indexes from the cache.
     */
    void clear();

    /**
     * Convenience method to evict all cache entries associated with a single tag.
     *
     * @param tag the tag to evict
     */
    default void evictByTag(String tag) {
        if (tag != null) {
            evictByTags(Set.of(tag));
        }
    }
}
