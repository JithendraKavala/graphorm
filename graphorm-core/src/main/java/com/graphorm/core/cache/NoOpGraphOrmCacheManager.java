package com.graphorm.core.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * No-operation implementation of {@link GraphOrmCacheManager} used when caching is disabled.
 */
public final class NoOpGraphOrmCacheManager implements GraphOrmCacheManager {

    public static final NoOpGraphOrmCacheManager INSTANCE = new NoOpGraphOrmCacheManager();

    @Override
    public <T> Optional<T> get(String cacheKey, Class<T> type) {
        return Optional.empty();
    }

    @Override
    public <T> void put(String cacheKey, T value, Duration ttl, Set<String> tags) {
        // No-op
    }

    @Override
    public void evictByTags(Set<String> tags) {
        // No-op
    }

    @Override
    public void clear() {
        // No-op
    }
}
