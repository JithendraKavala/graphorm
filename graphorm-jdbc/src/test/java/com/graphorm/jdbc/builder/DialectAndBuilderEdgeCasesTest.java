package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.MySqlDialect;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Dialect and Query Builder Edge Cases Tests")
class DialectAndBuilderEdgeCasesTest {

    private final PostgreSqlDialect pgDialect = new PostgreSqlDialect();
    private final MySqlDialect mySqlDialect = new MySqlDialect();

    @Test
    @DisplayName("WhereClauseBuilder should reject LIKE on RowComparisonPredicate")
    void shouldRejectLikeOnRowComparison() {
        assertThatThrownBy(() -> WhereClauseBuilder.build(
                WherePredicate.rowComparison(List.of("col1", "col2"), ComparisonOperator.LIKE, List.of("a%", "b%")),
                pgDialect
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Row constructor comparison does not support LIKE");
    }

    @Test
    @DisplayName("WhereClauseBuilder should render valid row comparison with bound parameters")
    void shouldRenderRowComparison() {
        BoundSql boundSql = WhereClauseBuilder.build(
                WherePredicate.rowComparison(List.of("id", "created_at"), ComparisonOperator.GREATER_THAN, List.of(10, "2024-01-01")),
                pgDialect
        );
        assertThat(boundSql.sql()).isEqualTo("(\"id\", \"created_at\") > (?, ?)");
        assertThat(boundSql.parameters()).containsExactly(10, "2024-01-01");
    }

    @Test
    @DisplayName("MySqlDialect should throw UnsupportedOperationException when updateWhere is present")
    void shouldRejectUpdateWhereOnMySqlUpsert() {
        UpsertQuery query = UpsertQuery.into("counters")
                .value("name", "page_views")
                .value("count", 1)
                .onConflict("name")
                .doUpdateSet("count", 2)
                .doUpdateWhere(WherePredicate.gt("count", 0))
                .build();

        assertThatThrownBy(() -> mySqlDialect.buildUpsertSql(query))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("MySQL does not support conditional WHERE clauses in ON DUPLICATE KEY UPDATE");
    }

    @Test
    @DisplayName("MySqlDialect should provide fallback ON DUPLICATE KEY UPDATE when update columns are empty")
    void shouldFallbackWhenUpdateColumnsEmpty() {
        UpsertQuery query = UpsertQuery.into("tags")
                .value("name", "java")
                .onConflict("name")
                .doUpdate()
                .build();

        BoundSql boundSql = mySqlDialect.buildUpsertSql(query);
        assertThat(boundSql.sql()).contains("ON DUPLICATE KEY UPDATE `name` = `name`");
    }

    @Test
    @DisplayName("UpdateBuilder build() should be idempotent and not mutate builder state on repeated calls")
    void updateBuilderBuildIsIdempotent() {
        UpdateBuilder builder = UpdateBuilder.of(pgDialect)
                .table("products")
                .set("name", "New Laptop")
                .where(WherePredicate.eq("id", 42L))
                .optimisticLock("version", 3);

        BoundSql first = builder.build();
        BoundSql second = builder.build();

        assertThat(first.sql()).isEqualTo(second.sql());
        assertThat(first.parameters()).isEqualTo(second.parameters());
    }

    @Test
    @DisplayName("DeleteBuilder supports optimisticLock and build() is idempotent")
    void deleteBuilderSupportsOptimisticLock() {
        DeleteBuilder builder = DeleteBuilder.of(pgDialect)
                .from("products")
                .where(WherePredicate.eq("id", 42L))
                .optimisticLock("version", 3);

        BoundSql first = builder.build();
        BoundSql second = builder.build();

        assertThat(first.sql()).isEqualTo("DELETE FROM \"products\" WHERE (\"id\" = ? AND \"version\" = ?)");
        assertThat(first.parameters()).containsExactly(42L, 3);
        assertThat(second.sql()).isEqualTo(first.sql());
        assertThat(second.parameters()).isEqualTo(first.parameters());

        builder.verifyOptimisticLock(1);
        assertThatThrownBy(() -> builder.verifyOptimisticLock(0))
                .isInstanceOf(OptimisticLockException.class);
    }
}
