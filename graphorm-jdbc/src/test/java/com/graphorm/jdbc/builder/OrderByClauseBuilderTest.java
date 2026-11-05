package com.graphorm.jdbc.builder;

import com.graphorm.core.query.NullsOrder;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderByClauseBuilder Multi-Column Sorting Unit Tests (TASK-5.3)")
class OrderByClauseBuilderTest {

    private SqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();
    }

    @Test
    @DisplayName("Should return empty string when clauses are null or empty")
    void shouldHandleEmptyClauses() {
        assertThat(OrderByClauseBuilder.buildOrderByClause((List<OrderByClause>) null, dialect)).isEmpty();
        assertThat(OrderByClauseBuilder.buildOrderByClause(List.of(), dialect)).isEmpty();
        assertThat(OrderByClauseBuilder.buildOrderByClause(dialect)).isEmpty();
        assertThat(OrderByClauseBuilder.build(List.of(), dialect)).isEmpty();
    }

    @Test
    @DisplayName("Should build single-column sorting for ASC and DESC")
    void shouldBuildSingleColumnSorting() {
        String asc = OrderByClauseBuilder.buildOrderByClause(List.of(OrderByClause.asc("name")), dialect);
        assertThat(asc).isEqualTo(" ORDER BY \"name\" ASC");

        String desc = OrderByClauseBuilder.buildOrderByClause(List.of(OrderByClause.desc("gpa")), dialect);
        assertThat(desc).isEqualTo(" ORDER BY \"gpa\" DESC");
    }

    @Test
    @DisplayName("Should build sorting with explicit NULLS FIRST and NULLS LAST")
    void shouldBuildNullsPositioning() {
        String nullsFirst = OrderByClauseBuilder.buildOrderByClause(
                List.of(OrderByClause.ascNullsFirst("gpa")),
                dialect
        );
        assertThat(nullsFirst).isEqualTo(" ORDER BY \"gpa\" ASC NULLS FIRST");

        String nullsLast = OrderByClauseBuilder.buildOrderByClause(
                List.of(OrderByClause.descNullsLast("gpa")),
                dialect
        );
        assertThat(nullsLast).isEqualTo(" ORDER BY \"gpa\" DESC NULLS LAST");
    }

    @Test
    @DisplayName("Should build multi-column sorting with mixed directions and nulls ordering")
    void shouldBuildMultiColumnSorting() {
        List<OrderByClause> clauses = List.of(
                OrderByClause.asc("department_id"),
                OrderByClause.descNullsLast("gpa"),
                OrderByClause.ascNullsFirst("name")
        );

        String result = OrderByClauseBuilder.buildOrderByClause(clauses, dialect);

        assertThat(result)
                .isEqualTo(" ORDER BY \"department_id\" ASC, \"gpa\" DESC NULLS LAST, \"name\" ASC NULLS FIRST");
    }

    @Test
    @DisplayName("Should support varargs overload and aliases")
    void shouldSupportVarargsOverloadAndAliases() {
        String varargsResult = OrderByClauseBuilder.buildOrderByClause(
                dialect,
                OrderByClause.asc("department_id"),
                OrderByClause.desc("id")
        );
        assertThat(varargsResult).isEqualTo(" ORDER BY \"department_id\" ASC, \"id\" DESC");

        String aliasResult = OrderByClauseBuilder.build(
                dialect,
                OrderByClause.asc("department_id"),
                OrderByClause.desc("id")
        );
        assertThat(aliasResult).isEqualTo(" ORDER BY \"department_id\" ASC, \"id\" DESC");
    }

    @Test
    @DisplayName("Should properly quote qualified column expressions")
    void shouldProperlyQuoteQualifiedExpressions() {
        String qualified = OrderByClauseBuilder.buildOrderByClause(
                List.of(OrderByClause.desc("s.created_at")),
                dialect
        );
        assertThat(qualified).isEqualTo(" ORDER BY \"s\".\"created_at\" DESC");
    }

    @Test
    @DisplayName("Should reject null dialect or null elements")
    void shouldRejectNulls() {
        assertThatThrownBy(() -> OrderByClauseBuilder.buildOrderByClause(List.of(OrderByClause.asc("name")), null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> OrderByClauseBuilder.buildOrderByClause(dialect, (OrderByClause) null))
                .isInstanceOf(NullPointerException.class);
    }
}
