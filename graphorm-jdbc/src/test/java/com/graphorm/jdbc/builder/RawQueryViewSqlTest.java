package com.graphorm.jdbc.builder;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Raw SQL Query View (Strategy C: @@query) SQL Generation Tests")
class RawQueryViewSqlTest {

    private SqlDialect dialect;
    private ViewModelMetadata unionView;
    private ViewModelMetadata cteView;
    private ModelRegistry registry;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();

        unionView = ViewModelMetadata.builder("UserUnion")
                .sourceModel(null, "u")
                .rawQuery("SELECT id, name, 'student' AS role FROM student UNION ALL SELECT id, name, 'instructor' AS role FROM instructor")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).dbColumnName("id").build())
                .addField(FieldMetadata.builder("name", String.class).dbColumnName("name").build())
                .addField(FieldMetadata.builder("role", String.class).dbColumnName("role").build())
                .build();

        cteView = ViewModelMetadata.builder("ActiveOrderSummary")
                .rawQuery("""
                    WITH active_orders AS (
                        SELECT customer_id, SUM(amount) AS total
                        FROM orders
                        WHERE status = 'ACTIVE'
                        GROUP BY customer_id
                    )
                    SELECT customer_id AS id, total
                    FROM active_orders
                    """)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).dbColumnName("id").build())
                .addField(FieldMetadata.builder("total", Double.class).dbColumnName("total").build())
                .build();

        registry = ModelRegistry.builder()
                .addViewModel(unionView)
                .addViewModel(cteView)
                .build();
    }

    @Test
    @DisplayName("Should wrap raw SQL UNION query in derived subquery with alias")
    void shouldGenerateSubquerySourceForUnion() {
        SelectQuery query = ViewQueryPlanner.plan(unionView, registry, dialect).build();
        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"u\".\"id\", \"u\".\"name\", \"u\".\"role\" " +
                        "FROM (SELECT id, name, 'student' AS role FROM student UNION ALL SELECT id, name, 'instructor' AS role FROM instructor) AS \"u\""
        );
        assertThat(boundSql.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should support pushdown filtering on raw query view with parameterized bound SQL")
    void shouldGenerateSubqueryWithPushdownFilter() {
        SelectQuery query = ViewQueryPlanner.plan(unionView, registry, dialect)
                .where(WherePredicate.and(
                        WherePredicate.eq("role", "student"),
                        WherePredicate.eq("name", "Ada Lovelace")
                ))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"u\".\"id\", \"u\".\"name\", \"u\".\"role\" " +
                        "FROM (SELECT id, name, 'student' AS role FROM student UNION ALL SELECT id, name, 'instructor' AS role FROM instructor) AS \"u\" " +
                        "WHERE (\"u\".\"role\" = ? AND \"u\".\"name\" = ?)"
        );
        assertThat(boundSql.parameters()).containsExactly("student", "Ada Lovelace");
    }

    @Test
    @DisplayName("Should support pushdown ordering and pagination on raw query view")
    void shouldGenerateSubqueryWithPushdownOrderByAndPagination() {
        SelectQuery query = ViewQueryPlanner.plan(unionView, registry, dialect)
                .orderByAsc("name")
                .limit(20)
                .offset(10)
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"u\".\"id\", \"u\".\"name\", \"u\".\"role\" " +
                        "FROM (SELECT id, name, 'student' AS role FROM student UNION ALL SELECT id, name, 'instructor' AS role FROM instructor) AS \"u\" " +
                        "ORDER BY \"u\".\"name\" ASC LIMIT ? OFFSET ?"
        );
        assertThat(boundSql.parameters()).containsExactly(20, 10);
    }

    @Test
    @DisplayName("Should support selective field projection on raw query view")
    void shouldGenerateSubqueryWithFieldProjection() {
        SelectQuery query = ViewQueryPlanner.plan(unionView, registry, Set.of("name", "role"), dialect).build();
        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"u\".\"name\", \"u\".\"role\" " +
                        "FROM (SELECT id, name, 'student' AS role FROM student UNION ALL SELECT id, name, 'instructor' AS role FROM instructor) AS \"u\""
        );
        assertThat(boundSql.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should generate valid SQL for CTE (WITH clause) raw query view")
    void shouldGenerateSubqueryWithCte() {
        SelectQuery query = ViewQueryPlanner.plan(cteView, registry, dialect)
                .where(WherePredicate.gt("total", 100.0))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).startsWith("SELECT \"activeordersummary\".\"id\", \"activeordersummary\".\"total\" FROM (WITH active_orders AS");
        assertThat(boundSql.sql()).contains("AS \"activeordersummary\" WHERE \"activeordersummary\".\"total\" > ?");
        assertThat(boundSql.parameters()).containsExactly(100.0);
    }

    @Test
    @DisplayName("SelectBuilder.forEntity should construct SelectQuery from raw query view")
    void shouldConstructQueryViaSelectBuilderForEntity() {
        SelectQuery.Builder builder = SelectBuilder.forEntity(unionView, registry);
        SelectQuery query = builder.where(WherePredicate.eq("id", 42L)).build();
        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).contains("FROM (SELECT id, name, 'student' AS role FROM student UNION ALL SELECT id, name, 'instructor' AS role FROM instructor) AS \"u\"");
        assertThat(boundSql.sql()).contains("WHERE \"id\" = ?");
        assertThat(boundSql.parameters()).containsExactly(42L);
    }
}
