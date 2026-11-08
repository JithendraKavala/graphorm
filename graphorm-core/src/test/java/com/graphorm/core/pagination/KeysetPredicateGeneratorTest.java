package com.graphorm.core.pagination;

import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.core.query.WherePredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KeysetPredicateGenerator Unit Tests")
class KeysetPredicateGeneratorTest {

    @Test
    @DisplayName("Should ensure tie breaker column is added when absent")
    void testEnsureTieBreaker() {
        List<OrderByClause> input = List.of(OrderByClause.desc("created_at"));
        List<OrderByClause> result = KeysetPredicateGenerator.ensureTieBreaker(input, "id");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).expression()).isEqualTo("created_at");
        assertThat(result.get(0).direction()).isEqualTo(OrderDirection.DESC);
        assertThat(result.get(1).expression()).isEqualTo("id");
        assertThat(result.get(1).direction()).isEqualTo(OrderDirection.ASC);
    }

    @Test
    @DisplayName("Should not duplicate tie breaker column if already present")
    void testEnsureTieBreakerAlreadyPresent() {
        List<OrderByClause> input = List.of(OrderByClause.asc("id"));
        List<OrderByClause> result = KeysetPredicateGenerator.ensureTieBreaker(input, "id");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).expression()).isEqualTo("id");
    }

    @Test
    @DisplayName("Should generate RowComparisonPredicate for uniform ASC columns with forward pagination")
    void testUniformAscForward() {
        List<OrderByClause> orderBy = List.of(
                OrderByClause.asc("created_at"),
                OrderByClause.asc("id")
        );
        List<Object> cursorValues = List.of("2024-01-01", 10L);

        WherePredicate predicate = KeysetPredicateGenerator.generatePredicate(orderBy, cursorValues, true);
        assertThat(predicate).isInstanceOf(WherePredicate.RowComparisonPredicate.class);

        WherePredicate.RowComparisonPredicate rowComp = (WherePredicate.RowComparisonPredicate) predicate;
        assertThat(rowComp.columns()).containsExactly("created_at", "id");
        assertThat(rowComp.operator()).isEqualTo(ComparisonOperator.GREATER_THAN);
        assertThat(rowComp.values()).containsExactly("2024-01-01", 10L);
    }

    @Test
    @DisplayName("Should generate RowComparisonPredicate for uniform DESC columns with forward pagination")
    void testUniformDescForward() {
        List<OrderByClause> orderBy = List.of(
                OrderByClause.desc("created_at"),
                OrderByClause.desc("id")
        );
        List<Object> cursorValues = List.of("2024-01-01", 10L);

        WherePredicate predicate = KeysetPredicateGenerator.generatePredicate(orderBy, cursorValues, true);
        assertThat(predicate).isInstanceOf(WherePredicate.RowComparisonPredicate.class);

        WherePredicate.RowComparisonPredicate rowComp = (WherePredicate.RowComparisonPredicate) predicate;
        assertThat(rowComp.operator()).isEqualTo(ComparisonOperator.LESS_THAN);
    }

    @Test
    @DisplayName("Should generate composite boolean predicate for mixed directions")
    void testMixedDirections() {
        List<OrderByClause> orderBy = List.of(
                OrderByClause.desc("score"),
                OrderByClause.asc("id")
        );
        List<Object> cursorValues = List.of(95.0, 10L);

        WherePredicate predicate = KeysetPredicateGenerator.generatePredicate(orderBy, cursorValues, true);
        // Mixed direction produces (score < 95.0 OR (score = 95.0 AND id > 10))
        assertThat(predicate).isInstanceOf(WherePredicate.OrPredicate.class);
    }
}
