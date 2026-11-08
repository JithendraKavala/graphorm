package com.graphorm.jdbc.builder;

import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.ConflictAction;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UpsertBuilder and BatchInsertBuilder Tests")
class UpsertAndBatchBuilderTest {

    private SqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();
    }

    @Test
    @DisplayName("Builds ON CONFLICT DO NOTHING upsert SQL")
    void testUpsertDoNothing() {
        UpsertQuery query = UpsertQuery.into("users")
                .value("email", "test@example.com")
                .value("name", "Test User")
                .onConflict("email")
                .doNothing()
                .build();

        BoundSql boundSql = dialect.buildUpsertSql(query);

        assertThat(boundSql.sql()).isEqualTo("INSERT INTO \"users\" (\"email\", \"name\") VALUES (?, ?) ON CONFLICT (\"email\") DO NOTHING");
        assertThat(boundSql.parameters()).containsExactly("test@example.com", "Test User");
    }

    @Test
    @DisplayName("Builds ON CONFLICT DO UPDATE SET upsert SQL with WHERE and RETURNING")
    void testUpsertDoUpdateWithWhereAndReturning() {
        UpsertQuery query = UpsertQuery.into("counters")
                .value("name", "page_views")
                .value("count", 1)
                .onConflict("name")
                .doUpdateSet("count", 2)
                .doUpdateWhere(WherePredicate.gt("count", 0))
                .returning("id", "count")
                .build();

        BoundSql boundSql = dialect.buildUpsertSql(query);

        assertThat(boundSql.sql()).isEqualTo(
                "INSERT INTO \"counters\" (\"name\", \"count\") VALUES (?, ?) " +
                "ON CONFLICT (\"name\") DO UPDATE SET \"count\" = ? WHERE \"count\" > ? RETURNING \"id\", \"count\""
        );
        assertThat(boundSql.parameters()).containsExactly("page_views", 1, 2, 0);
    }

    @Test
    @DisplayName("Builds multi-row batch insert SQL")
    void testBatchInsertSql() {
        BatchInsertQuery query = BatchInsertQuery.into("students")
                .columns("name", "age", "department_id")
                .addRow("Alice", 21, 10)
                .addRow("Bob", 22, 10)
                .addRow("Charlie", 20, 20)
                .returning("id", "name")
                .build();

        BoundSql boundSql = dialect.buildBatchInsertSql(query);

        assertThat(boundSql.sql()).isEqualTo(
                "INSERT INTO \"students\" (\"name\", \"age\", \"department_id\") " +
                "VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?) RETURNING \"id\", \"name\""
        );
        assertThat(boundSql.parameters()).containsExactly(
                "Alice", 21, 10,
                "Bob", 22, 10,
                "Charlie", 20, 20
        );
    }

    @Test
    @DisplayName("Builds batch insert from maps with RETURNING *")
    void testBatchInsertFromMaps() {
        BatchInsertQuery query = BatchInsertQuery.into("tags")
                .fromMaps(List.of(
                        Map.of("name", "java", "color", "red"),
                        Map.of("name", "sql", "color", "blue")
                ))
                .returningAll()
                .build();

        BoundSql boundSql = dialect.buildBatchInsertSql(query);

        assertThat(boundSql.sql()).contains("INSERT INTO \"tags\"");
        assertThat(boundSql.sql()).contains("VALUES (?, ?), (?, ?)");
        assertThat(boundSql.sql()).endsWith("RETURNING *");
        assertThat(boundSql.parameters()).hasSize(4);
    }
}
