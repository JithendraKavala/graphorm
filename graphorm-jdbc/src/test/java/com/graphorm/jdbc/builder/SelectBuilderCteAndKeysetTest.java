package com.graphorm.jdbc.builder;

import com.graphorm.core.pagination.CursorCodec;
import com.graphorm.core.query.CommonTableExpression;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SelectBuilder CTE and Keyset Cursor Pagination Tests")
class SelectBuilderCteAndKeysetTest {

    private SqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();
    }

    @Test
    @DisplayName("Builds SELECT query with CTE (WITH clause)")
    void testSelectWithCte() {
        SelectQuery cteQuery = SelectQuery.from("orders")
                .columns("customer_id", "total")
                .where(WherePredicate.gt("total", 100))
                .build();

        SelectQuery mainQuery = SelectQuery.from("high_spenders")
                .with("high_spenders", cteQuery)
                .columns("customer_id")
                .build();

        BoundSql boundSql = dialect.buildSelectSql(mainQuery);

        assertThat(boundSql.sql()).startsWith("WITH \"high_spenders\" AS (SELECT \"customer_id\", \"total\" FROM \"orders\" WHERE \"total\" > ?)");
        assertThat(boundSql.sql()).contains("SELECT \"customer_id\" FROM \"high_spenders\"");
        assertThat(boundSql.parameters()).containsExactly(100);
    }

    @Test
    @DisplayName("Builds SELECT query with keyset cursor pagination (after cursor)")
    void testKeysetCursorPaginationAfter() {
        String cursor = CursorCodec.encode(List.of("2026-01-01T00:00:00Z", 10L));

        SelectQuery query = SelectQuery.from("audit_logs")
                .columns("id", "action", "timestamp")
                .orderBy(OrderByClause.asc("timestamp"))
                .after(cursor)
                .first(20)
                .build();

        BoundSql boundSql = dialect.buildSelectSql(query);

        assertThat(boundSql.sql()).contains("(\"timestamp\", \"id\") > (?, ?)");
        assertThat(boundSql.sql()).contains("ORDER BY \"timestamp\" ASC, \"id\" ASC");
        assertThat(boundSql.sql()).contains("LIMIT ?");
        assertThat(boundSql.parameters()).contains("2026-01-01T00:00:00Z", 10L, 20);
    }

    @Test
    @DisplayName("Builds SELECT query with keyset cursor pagination (before cursor)")
    void testKeysetCursorPaginationBefore() {
        String cursor = CursorCodec.encode(List.of("2026-01-01T00:00:00Z", 10L));

        SelectQuery query = SelectQuery.from("audit_logs")
                .columns("id", "action", "timestamp")
                .orderBy(OrderByClause.asc("timestamp"))
                .before(cursor)
                .last(10)
                .build();

        BoundSql boundSql = dialect.buildSelectSql(query);

        assertThat(boundSql.sql()).contains("(\"timestamp\", \"id\") < (?, ?)");
        assertThat(boundSql.sql()).contains("LIMIT ?");
    }
}
