package com.graphorm.core.cache;

import com.graphorm.core.metadata.CacheMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.core.util.DurationParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreCacheTest {

    @BeforeEach
    @AfterEach
    void cleanTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("DurationParser parses simple duration strings (ms, s, m, h, d)")
    void testDurationParserUnits() {
        assertThat(DurationParser.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(DurationParser.parse("60s")).isEqualTo(Duration.ofSeconds(60));
        assertThat(DurationParser.parse("5m")).isEqualTo(Duration.ofMinutes(5));
        assertThat(DurationParser.parse("2h")).isEqualTo(Duration.ofHours(2));
        assertThat(DurationParser.parse("1d")).isEqualTo(Duration.ofDays(1));
        assertThat(DurationParser.parse("PT30S")).isEqualTo(Duration.ofSeconds(30));

        assertThat(DurationParser.isValid("60s")).isTrue();
        assertThat(DurationParser.isValid("5m")).isTrue();
        assertThat(DurationParser.isValid("invalid")).isFalse();
        assertThat(DurationParser.isValid("")).isFalse();
        assertThat(DurationParser.isValid(null)).isFalse();

        assertThatThrownBy(() -> DurationParser.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CacheMetadata record invariants and factory methods")
    void testCacheMetadata() {
        CacheMetadata notCached = CacheMetadata.notCached();
        assertThat(notCached.isCached()).isFalse();
        assertThat(notCached.ttl()).isNull();
        assertThat(notCached.tags()).isEmpty();

        CacheMetadata cached = CacheMetadata.of(Duration.ofSeconds(60), Set.of("Student", "Enrollment"));
        assertThat(cached.isCached()).isTrue();
        assertThat(cached.ttl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(cached.tags()).containsExactlyInAnyOrder("Student", "Enrollment");
        assertThat(cached.hasTtl()).isTrue();
    }

    @Test
    @DisplayName("CaffeineGraphOrmCacheManager basic put, get, TTL and stats")
    void testCaffeineCacheManagerBasic() {
        CaffeineGraphOrmCacheManager manager = new CaffeineGraphOrmCacheManager(100);

        String key = "Student:id#1";
        Map<String, Object> data = Map.of("id", 1, "name", "Alice");

        assertThat(manager.get(key, Map.class)).isEmpty();

        manager.put(key, data, Duration.ofMinutes(5), Set.of("Student"));

        Optional<Map> cached = manager.get(key, Map.class);
        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(data);
        assertThat(manager.size()).isEqualTo(1);
        assertThat(manager.hitCount()).isEqualTo(1);
        assertThat(manager.missCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CaffeineGraphOrmCacheManager tag eviction invalidates all dependent keys")
    void testCaffeineCacheManagerTagEviction() {
        CaffeineGraphOrmCacheManager manager = new CaffeineGraphOrmCacheManager();

        manager.put("Student:id#1", Map.of("id", 1), Duration.ofMinutes(1), Set.of("Student"));
        manager.put("Student:id#2", Map.of("id", 2), Duration.ofMinutes(1), Set.of("Student"));
        manager.put("StudentDashboard:view#1", Map.of("summary", "pass"), Duration.ofMinutes(1), Set.of("Student", "Enrollment"));
        manager.put("Course:id#101", Map.of("id", 101), Duration.ofMinutes(1), Set.of("Course"));

        assertThat(manager.size()).isEqualTo(4);
        assertThat(manager.tagCount()).isEqualTo(3); // Student, Enrollment, Course

        // Mutating "Student" must evict all Student entries and StudentDashboard!
        manager.evictByTags(Set.of("Student"));

        assertThat(manager.get("Student:id#1", Map.class)).isEmpty();
        assertThat(manager.get("Student:id#2", Map.class)).isEmpty();
        assertThat(manager.get("StudentDashboard:view#1", Map.class)).isEmpty();

        // Course is unaffected
        assertThat(manager.get("Course:id#101", Map.class)).isPresent();
    }

    @Test
    @DisplayName("CacheKeyBuilder generates deterministic keys with tenant isolation")
    void testCacheKeyBuilderTenantIsolation() {
        // Without tenant
        String key1 = CacheKeyBuilder.forId("Student", 123);
        assertThat(key1).isEqualTo("Student:id#123");

        // With tenant alpha
        TenantContext.setTenantId("alpha");
        String keyAlpha = CacheKeyBuilder.forId("Student", 123);
        assertThat(keyAlpha).isEqualTo("Student:tenant#alpha:id#123");

        // With tenant beta
        TenantContext.setTenantId("beta");
        String keyBeta = CacheKeyBuilder.forId("Student", 123);
        assertThat(keyBeta).isEqualTo("Student:tenant#beta:id#123");

        // Cross-tenant verification
        assertThat(keyAlpha).isNotEqualTo(keyBeta);
        assertThat(keyAlpha).isNotEqualTo(key1);

        // Query key with tenant
        String qAlpha = CacheKeyBuilder.forQuery("StudentDashboard", "filter=active");
        TenantContext.setTenantId("gamma");
        String qGamma = CacheKeyBuilder.forQuery("StudentDashboard", "filter=active");
        assertThat(qAlpha).isNotEqualTo(qGamma);
        assertThat(qAlpha).contains("tenant#beta");
        assertThat(qGamma).contains("tenant#gamma");
    }
}
