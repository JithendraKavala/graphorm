package com.graphorm.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory L2 Entity & View Result Cache implementation of {@link GraphOrmCacheManager}
 * backed by Caffeine cache and a thread-safe tag-based reverse index.
 * <p>
 * Supports:
 * <ul>
 *     <li>Per-entry dynamic TTL expiration via {@link Expiry}</li>
 *     <li>Tag-based reverse index mapping entity tags (e.g. "Student", "Enrollment") to cache keys</li>
 *     <li>Instant O(1) eviction of dependent view cache entries upon model mutations</li>
 *     <li>Zero memory leak reverse index pruning via Caffeine eviction listener</li>
 * </ul>
 */
public class CaffeineGraphOrmCacheManager implements GraphOrmCacheManager {

    private static final Logger log = LoggerFactory.getLogger(CaffeineGraphOrmCacheManager.class);
    public static final int DEFAULT_MAXIMUM_SIZE = 10_000;

    record CacheEntry(Object value, Duration ttl, Set<String> tags) {}

    private final Cache<String, CacheEntry> cache;
    private final ConcurrentHashMap<String, Set<String>> tagToKeys = new ConcurrentHashMap<>();

    public CaffeineGraphOrmCacheManager() {
        this(DEFAULT_MAXIMUM_SIZE);
    }

    public CaffeineGraphOrmCacheManager(long maximumSize) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize > 0 ? maximumSize : DEFAULT_MAXIMUM_SIZE)
                .recordStats()
                .expireAfter(new Expiry<String, CacheEntry>() {
                    @Override
                    public long expireAfterCreate(String key, CacheEntry entry, long currentTime) {
                        return entry.ttl() != null ? entry.ttl().toNanos() : Long.MAX_VALUE;
                    }

                    @Override
                    public long expireAfterUpdate(String key, CacheEntry entry, long currentTime, long currentDuration) {
                        return entry.ttl() != null ? entry.ttl().toNanos() : currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String key, CacheEntry entry, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .evictionListener((String key, CacheEntry entry, RemovalCause cause) -> {
                    if (entry != null && entry.tags() != null) {
                        for (String tag : entry.tags()) {
                            Set<String> set = tagToKeys.get(tag);
                            if (set != null) {
                                set.remove(key);
                                if (set.isEmpty()) {
                                    tagToKeys.remove(tag, Collections.emptySet());
                                }
                            }
                        }
                    }
                })
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheKey, Class<T> type) {
        Objects.requireNonNull(cacheKey, "cacheKey must not be null");
        Objects.requireNonNull(type, "type must not be null");

        CacheEntry entry = cache.getIfPresent(cacheKey);
        if (entry == null || entry.value() == null) {
            return Optional.empty();
        }
        Object val = entry.value();
        if (type.isInstance(val)) {
            return Optional.of((T) val);
        }
        // Fallback cast or empty if incompatible type
        try {
            return Optional.of(type.cast(val));
        } catch (ClassCastException e) {
            log.warn("Cached value for key '{}' was of type {} which cannot be cast to {}",
                    cacheKey, val.getClass().getName(), type.getName());
            return Optional.empty();
        }
    }

    @Override
    public <T> void put(String cacheKey, T value, Duration ttl, Set<String> tags) {
        Objects.requireNonNull(cacheKey, "cacheKey must not be null");
        if (value == null) {
            return;
        }
        Set<String> safeTags = tags == null ? Set.of() : Set.copyOf(tags);
        CacheEntry entry = new CacheEntry(value, ttl, safeTags);
        cache.put(cacheKey, entry);

        for (String tag : safeTags) {
            tagToKeys.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet()).add(cacheKey);
        }
        log.debug("Cached key '{}' with ttl={} tags={}", cacheKey, ttl, safeTags);
    }

    @Override
    public void evictByTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            Set<String> keys = tagToKeys.remove(tag);
            if (keys != null && !keys.isEmpty()) {
                log.debug("Evicting {} cache keys for tag '{}'", keys.size(), tag);
                cache.invalidateAll(keys);
            }
        }
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        tagToKeys.clear();
        log.debug("GraphORM cache cleared");
    }

    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    public int tagCount() {
        return tagToKeys.size();
    }

    public Set<String> getKeysForTag(String tag) {
        Set<String> keys = tagToKeys.get(tag);
        return keys == null ? Set.of() : Set.copyOf(keys);
    }

    public long hitCount() {
        return cache.stats().hitCount();
    }

    public long missCount() {
        return cache.stats().missCount();
    }

    public double hitRate() {
        return cache.stats().hitRate();
    }
}
