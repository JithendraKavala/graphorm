package com.graphorm.jdbc.cache;

import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueryPlanCache Unit Tests (L1 AST & BoundSql Cache)")
class QueryPlanCacheTest {

    @Test
    @DisplayName("Identical query shapes with different parameter values reuse cached query plan")
    void testPlanCacheHitWithDifferentParameters() {
        QueryPlanCache cache = QueryPlanCache.create(100);
        PostgreSqlDialect dialect = new PostgreSqlDialect();

        SelectQuery query1 = SelectQuery.from("users")
                .column("id")
                .column("name")
                .column("age")
                .where(new WherePredicate.BinaryPredicate("age", ComparisonOperator.EQUALS, 20))
                .build();

        BoundSql boundSql1 = SelectBuilder.of(dialect, cache).build(query1);

        assertThat(boundSql1.sql()).isEqualTo("SELECT \"id\", \"name\", \"age\" FROM \"users\" WHERE \"age\" = ?");
        assertThat(boundSql1.parameters()).containsExactly(20);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.hitCount()).isEqualTo(0);

        SelectQuery query2 = SelectQuery.from("users")
                .column("id")
                .column("name")
                .column("age")
                .where(new WherePredicate.BinaryPredicate("age", ComparisonOperator.EQUALS, 35))
                .build();

        BoundSql boundSql2 = SelectBuilder.of(dialect, cache).build(query2);

        assertThat(boundSql2.sql()).isEqualTo("SELECT \"id\", \"name\", \"age\" FROM \"users\" WHERE \"age\" = ?");
        assertThat(boundSql2.parameters()).containsExactly(35);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.hitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Zero parameter leak in QueryPlan cache template or key")
    void testZeroParameterLeak() {
        QueryPlanCache cache = QueryPlanCache.create(100);
        PostgreSqlDialect dialect = new PostgreSqlDialect();

        String secretToken = "super-secret-token-12345";
        SelectQuery query = SelectQuery.from("users")
                .column("id")
                .where(new WherePredicate.BinaryPredicate("token", ComparisonOperator.EQUALS, secretToken))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect, cache).build(query);
        assertThat(boundSql.parameters()).containsExactly(secretToken);

        QueryPlan plan = cache.asMap().values().iterator().next();
        assertThat(plan.sqlTemplate()).doesNotContain(secretToken);
        assertThat(plan.sqlTemplate()).contains("?");

        QueryPlanKey key = cache.asMap().keySet().iterator().next();
        assertThat(key.filterShape()).doesNotContain(secretToken);
        assertThat(key.filterShape()).isEqualTo("WHERE[token:EQUALS]");
    }

    @Test
    @DisplayName("Different filter shapes produce distinct cache entries")
    void testDifferentFilterShapes() {
        QueryPlanCache cache = QueryPlanCache.create(100);
        PostgreSqlDialect dialect = new PostgreSqlDialect();

        SelectQuery query1 = SelectQuery.from("users")
                .column("id")
                .where(new WherePredicate.BinaryPredicate("age", ComparisonOperator.EQUALS, 25))
                .build();

        SelectQuery query2 = SelectQuery.from("users")
                .column("id")
                .where(WherePredicate.and(
                        new WherePredicate.BinaryPredicate("age", ComparisonOperator.GREATER_THAN, 18),
                        new WherePredicate.BinaryPredicate("active", ComparisonOperator.EQUALS, true)
                ))
                .build();

        SelectBuilder.of(dialect, cache).build(query1);
        SelectBuilder.of(dialect, cache).build(query2);

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.missCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("LRU eviction respects maximum cache size")
    void testLruEviction() {
        QueryPlanCache cache = QueryPlanCache.create(2);
        PostgreSqlDialect dialect = new PostgreSqlDialect();

        SelectQuery q1 = SelectQuery.from("t1").column("id").build();
        SelectQuery q2 = SelectQuery.from("t2").column("id").build();
        SelectQuery q3 = SelectQuery.from("t3").column("id").build();

        SelectBuilder.of(dialect, cache).build(q1);
        SelectBuilder.of(dialect, cache).build(q2);
        SelectBuilder.of(dialect, cache).build(q3);

        cache.cleanUp();
        assertThat(cache.size()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Compound and row comparison predicates correctly extract runtime parameters")
    void testCompoundPredicateParameterExtraction() {
        QueryPlanCache cache = QueryPlanCache.create(50);
        PostgreSqlDialect dialect = new PostgreSqlDialect();

        SelectQuery query = SelectQuery.from("users")
                .column("id")
                .column("email")
                .where(WherePredicate.and(
                        WherePredicate.in("dept_id", List.of(1, 2, 3)),
                        WherePredicate.or(
                                new WherePredicate.BinaryPredicate("status", ComparisonOperator.EQUALS, "ACTIVE"),
                                new WherePredicate.BinaryPredicate("status", ComparisonOperator.EQUALS, "PENDING")
                        )
                ))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect, cache).build(query);
        assertThat(boundSql.parameters()).containsExactly(1, 2, 3, "ACTIVE", "PENDING");

        // Second run with different parameters
        SelectQuery query2 = SelectQuery.from("users")
                .column("id")
                .column("email")
                .where(WherePredicate.and(
                        WherePredicate.in("dept_id", List.of(4, 5, 6)),
                        WherePredicate.or(
                                new WherePredicate.BinaryPredicate("status", ComparisonOperator.EQUALS, "SUSPENDED"),
                                new WherePredicate.BinaryPredicate("status", ComparisonOperator.EQUALS, "CLOSED")
                        )
                ))
                .build();

        BoundSql boundSql2 = SelectBuilder.of(dialect, cache).build(query2);
        assertThat(boundSql2.parameters()).containsExactly(4, 5, 6, "SUSPENDED", "CLOSED");
        assertThat(cache.hitCount()).isEqualTo(1);
    }
}
