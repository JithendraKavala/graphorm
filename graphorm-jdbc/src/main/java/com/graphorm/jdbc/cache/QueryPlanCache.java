package com.graphorm.jdbc.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * High-performance L1 AST & BoundSql Query Plan Cache backed by Caffeine.
 * Pre-compiles parameterized SQL templates and column projections indexed by
 * {@code (EntityName, SelectionSetFingerprint, FilterShape, Dialect)}.
 * <p>
 * Eliminates AST reflection, selection-set analysis, dialect identifier quoting,
 * and SQL string concatenation overhead on repetitive queries.
 */
public class QueryPlanCache {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanCache.class);
    public static final int DEFAULT_MAX_SIZE = 1000;

    private static final QueryPlanCache DEFAULT_INSTANCE = new QueryPlanCache(DEFAULT_MAX_SIZE);

    private final Cache<QueryPlanKey, QueryPlan> cache;
    private final long maxSize;

    public QueryPlanCache() {
        this(DEFAULT_MAX_SIZE);
    }

    public QueryPlanCache(long maxSize) {
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.cache = Caffeine.newBuilder()
                .maximumSize(this.maxSize)
                .recordStats()
                .build();
    }

    public static QueryPlanCache getDefault() {
        return DEFAULT_INSTANCE;
    }

    public static QueryPlanCache create(long maxSize) {
        return new QueryPlanCache(maxSize);
    }

    public Optional<QueryPlan> find(QueryPlanKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public QueryPlan get(QueryPlanKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return cache.getIfPresent(key);
    }

    public void put(QueryPlanKey key, QueryPlan plan) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        cache.put(key, plan);
        log.debug("Stored QueryPlan for entity '{}', fingerprint '{}', filterShape '{}'",
                key.entityName(), key.selectionSetFingerprint(), key.filterShape());
    }

    public QueryPlan computeIfAbsent(QueryPlanKey key, Function<QueryPlanKey, QueryPlan> mappingFunction) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(mappingFunction, "mappingFunction must not be null");
        return cache.get(key, mappingFunction);
    }

    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
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

    public void clear() {
        cache.invalidateAll();
        log.debug("QueryPlanCache cleared");
    }

    public long maxSize() {
        return maxSize;
    }

    public void cleanUp() {
        cache.cleanUp();
    }

    public java.util.concurrent.ConcurrentMap<QueryPlanKey, QueryPlan> asMap() {
        return cache.asMap();
    }
}
