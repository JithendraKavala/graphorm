package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.NullsOrder;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CRUD Query Builders Unit Tests")
class QueryBuildersTest {

    private SqlDialect dialect;
    private ModelMetadata studentModel;
    private ViewModelMetadata studentDashboardView;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();

        studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("email", String.class).unique(true).build())
                .addField(FieldMetadata.builder("gpa", Double.class).build())
                .build();

        studentDashboardView = ViewModelMetadata.builder("StudentDashboard")
                .addField(FieldMetadata.builder("studentId", Long.class).build())
                .addField(FieldMetadata.builder("fullName", String.class).build())
                .addField(FieldMetadata.builder("courseCount", Long.class).aggregate("COUNT").build())
                .build();
    }

    @Nested
    @DisplayName("SelectBuilder Tests")
    class SelectBuilderTests {

        @Test
        @DisplayName("Should build basic SELECT * FROM table query")
        void shouldBuildBasicSelect() {
            SelectQuery query = SelectQuery.from("students").build();
            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            assertThat(boundSql.sql()).isEqualTo("SELECT * FROM \"students\"");
            assertThat(boundSql.parameters()).isEmpty();
        }

        @Test
        @DisplayName("Should project specific columns with alias")
        void shouldProjectColumnsWithAlias() {
            SelectQuery query = SelectQuery.from("students")
                    .as("s")
                    .column("s.id", "id")
                    .column("s.name", "studentName")
                    .build();

            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            assertThat(boundSql.sql())
                    .isEqualTo("SELECT \"s\".\"id\", \"s\".\"name\" AS \"studentName\" FROM \"students\" AS \"s\"");
        }

        @Test
        @DisplayName("Should build complex query with JOIN, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT, OFFSET")
        void shouldBuildComplexSelectQuery() {
            SelectQuery query = SelectQuery.from("students")
                    .as("s")
                    .column("s.id")
                    .column("COUNT(e.id)", "courseCount")
                    .join(JoinClause.left("enrollments", "e", WherePredicate.raw("\"s\".\"id\" = \"e\".\"student_id\"")))
                    .where(WherePredicate.and(
                            WherePredicate.gte("s.gpa", 3.0),
                            WherePredicate.like("s.name", "A%")
                    ))
                    .groupBy("s.id")
                    .having(WherePredicate.gt("COUNT(e.id)", 1))
                    .orderBy(new OrderByClause("s.id", com.graphorm.core.query.OrderDirection.DESC, NullsOrder.LAST))
                    .limit(25)
                    .offset(50)
                    .build();

            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            assertThat(boundSql.sql())
                    .isEqualTo("SELECT \"s\".\"id\", COUNT(e.id) AS \"courseCount\" FROM \"students\" AS \"s\" " +
                            "LEFT JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                            "WHERE (\"s\".\"gpa\" >= ? AND \"s\".\"name\" LIKE ?) " +
                            "GROUP BY \"s\".\"id\" " +
                            "HAVING COUNT(e.id) > ? " +
                            "ORDER BY \"s\".\"id\" DESC NULLS LAST " +
                            "LIMIT ? OFFSET ?");

            assertThat(boundSql.parameters())
                    .containsExactly(3.0, "A%", 1, 25, 50);
        }

        @Test
        @DisplayName("Should support forEntity factory populating model columns")
        void shouldPopulateEntityColumns() {
            SelectQuery query = SelectBuilder.forEntity(studentModel).build();
            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            assertThat(boundSql.sql())
                    .isEqualTo("SELECT \"id\", \"name\", \"email\", \"gpa\" FROM \"students\"");
        }

        @Test
        @DisplayName("Should permit SELECT queries targeting read-only views")
        void shouldAllowSelectOnView() {
            SelectQuery query = SelectBuilder.forEntity(studentDashboardView).build();
            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            assertThat(boundSql.sql())
                    .isEqualTo("SELECT \"studentId\", \"fullName\", \"courseCount\" FROM \"studentdashboard\"");
        }

        @Test
        @DisplayName("Should support subquery in FROM clause")
        void shouldSupportSubquerySource() {
            SelectQuery subquery = SelectQuery.from("students")
                    .column("id")
                    .where(WherePredicate.gt("gpa", 3.5))
                    .build();

            SelectQuery outerQuery = SelectQuery.from(SourceTable.subquery(subquery, "top_students"))
                    .column("*")
                    .build();

            BoundSql boundSql = SelectBuilder.of(dialect).build(outerQuery);

            assertThat(boundSql.sql())
                    .isEqualTo("SELECT * FROM (SELECT \"id\" FROM \"students\" WHERE \"gpa\" > ?)");
            assertThat(boundSql.parameters()).containsExactly(3.5);
        }
    }

    @Nested
    @DisplayName("InsertBuilder Tests")
    class InsertBuilderTests {

        @Test
        @DisplayName("Should build INSERT query with bound parameters")
        void shouldBuildInsert() {
            BoundSql boundSql = InsertBuilder.of(dialect)
                    .into("students")
                    .value("name", "Bob")
                    .value("email", "bob@example.com")
                    .value("gpa", 3.8)
                    .build();

            assertThat(boundSql.sql())
                    .isEqualTo("INSERT INTO \"students\" (\"name\", \"email\", \"gpa\") VALUES (?, ?, ?)");
            assertThat(boundSql.parameters()).containsExactly("Bob", "bob@example.com", 3.8);
        }

        @Test
        @DisplayName("Should support RETURNING clause on INSERT")
        void shouldSupportReturningClause() {
            BoundSql boundSql = InsertBuilder.of(dialect)
                    .into("students")
                    .value("name", "Bob")
                    .returning("id", "created_at")
                    .build();

            assertThat(boundSql.sql())
                    .isEqualTo("INSERT INTO \"students\" (\"name\") VALUES (?) RETURNING \"id\", \"created_at\"");
            assertThat(boundSql.parameters()).containsExactly("Bob");
        }

        @Test
        @DisplayName("Should support RETURNING * on INSERT")
        void shouldSupportReturningAll() {
            BoundSql boundSql = InsertBuilder.of(dialect)
                    .into("students")
                    .value("name", "Bob")
                    .returningAll()
                    .build();

            assertThat(boundSql.sql())
                    .isEqualTo("INSERT INTO \"students\" (\"name\") VALUES (?) RETURNING *");
        }

        @Test
        @DisplayName("Should throw ReadOnlyViewException when targeting a view model in forEntity")
        void shouldRejectInsertOnViewModel() {
            assertThatThrownBy(() -> InsertBuilder.of(dialect).forEntity(studentDashboardView))
                    .isInstanceOf(ReadOnlyViewException.class)
                    .hasMessageContaining("StudentDashboard")
                    .hasMessageContaining("INSERT");
        }

        @Test
        @DisplayName("Should throw ReadOnlyViewException when into() receives a DatabaseView source")
        void shouldRejectInsertOnDatabaseViewSource() {
            assertThatThrownBy(() -> InsertBuilder.of(dialect).into(SourceTable.view("vw_students")))
                    .isInstanceOf(ReadOnlyViewException.class)
                    .hasMessageContaining("vw_students")
                    .hasMessageContaining("INSERT");
        }
    }

    @Nested
    @DisplayName("UpdateBuilder Tests")
    class UpdateBuilderTests {

        @Test
        @DisplayName("Should build UPDATE query with SET values and WHERE parameters")
        void shouldBuildUpdate() {
            BoundSql boundSql = UpdateBuilder.of(dialect)
                    .table("students")
                    .set("name", "Charlie Updated")
                    .set("gpa", 3.9)
                    .where(WherePredicate.and(
                            WherePredicate.eq("id", 42L),
                            WherePredicate.eq("status", "ACTIVE")
                    ))
                    .build();

            assertThat(boundSql.sql())
                    .isEqualTo("UPDATE \"students\" SET \"name\" = ?, \"gpa\" = ? WHERE (\"id\" = ? AND \"status\" = ?)");
            assertThat(boundSql.parameters()).containsExactly("Charlie Updated", 3.9, 42L, "ACTIVE");
        }

        @Test
        @DisplayName("Should support RETURNING clause on UPDATE")
        void shouldSupportReturningClauseOnUpdate() {
            BoundSql boundSql = UpdateBuilder.of(dialect)
                    .table("students")
                    .set("gpa", 4.0)
                    .where(WherePredicate.eq("id", 1L))
                    .returningAll()
                    .build();

            assertThat(boundSql.sql())
                    .isEqualTo("UPDATE \"students\" SET \"gpa\" = ? WHERE \"id\" = ? RETURNING *");
            assertThat(boundSql.parameters()).containsExactly(4.0, 1L);
        }

        @Test
        @DisplayName("Should throw ReadOnlyViewException when targeting a view model in forEntity")
        void shouldRejectUpdateOnViewModel() {
            assertThatThrownBy(() -> UpdateBuilder.of(dialect).forEntity(studentDashboardView))
                    .isInstanceOf(ReadOnlyViewException.class)
                    .hasMessageContaining("StudentDashboard")
                    .hasMessageContaining("UPDATE");
        }
    }

    @Nested
    @DisplayName("DeleteBuilder Tests")
    class DeleteBuilderTests {

        @Test
        @DisplayName("Should build DELETE query with WHERE parameters")
        void shouldBuildDelete() {
            BoundSql boundSql = DeleteBuilder.of(dialect)
                    .from("students")
                    .where(WherePredicate.eq("id", 99L))
                    .build();

            assertThat(boundSql.sql())
                    .isEqualTo("DELETE FROM \"students\" WHERE \"id\" = ?");
            assertThat(boundSql.parameters()).containsExactly(99L);
        }

        @Test
        @DisplayName("Should support RETURNING clause on DELETE")
        void shouldSupportReturningOnDelete() {
            BoundSql boundSql = DeleteBuilder.of(dialect)
                    .from("students")
                    .where(WherePredicate.eq("id", 99L))
                    .returning("id")
                    .build();

            assertThat(boundSql.sql())
                    .isEqualTo("DELETE FROM \"students\" WHERE \"id\" = ? RETURNING \"id\"");
            assertThat(boundSql.parameters()).containsExactly(99L);
        }

        @Test
        @DisplayName("Should throw ReadOnlyViewException when targeting a view model in forEntity")
        void shouldRejectDeleteOnViewModel() {
            assertThatThrownBy(() -> DeleteBuilder.of(dialect).forEntity(studentDashboardView))
                    .isInstanceOf(ReadOnlyViewException.class)
                    .hasMessageContaining("StudentDashboard")
                    .hasMessageContaining("DELETE");
        }
    }

    @Nested
    @DisplayName("WhereClauseBuilder Tests")
    class WhereClauseBuilderTests {

        @Test
        @DisplayName("Should render IN and NOT IN predicates")
        void shouldRenderInPredicates() {
            BoundSql inSql = WhereClauseBuilder.build(WherePredicate.in("status", "A", "B", "C"), dialect);
            assertThat(inSql.sql()).isEqualTo("\"status\" IN (?, ?, ?)");
            assertThat(inSql.parameters()).containsExactly("A", "B", "C");

            BoundSql notInSql = WhereClauseBuilder.build(WherePredicate.notIn("id", 1, 2), dialect);
            assertThat(notInSql.sql()).isEqualTo("\"id\" NOT IN (?, ?)");
            assertThat(notInSql.parameters()).containsExactly(1, 2);
        }

        @Test
        @DisplayName("Should render IS NULL and IS NOT NULL predicates")
        void shouldRenderIsNullPredicates() {
            BoundSql isNull = WhereClauseBuilder.build(WherePredicate.isNull("deleted_at"), dialect);
            assertThat(isNull.sql()).isEqualTo("\"deleted_at\" IS NULL");
            assertThat(isNull.parameters()).isEmpty();

            BoundSql isNotNull = WhereClauseBuilder.build(WherePredicate.isNotNull("email"), dialect);
            assertThat(isNotNull.sql()).isEqualTo("\"email\" IS NOT NULL");
            assertThat(isNotNull.parameters()).isEmpty();
        }

        @Test
        @DisplayName("Should render BETWEEN predicate")
        void shouldRenderBetweenPredicate() {
            BoundSql between = WhereClauseBuilder.build(WherePredicate.between("age", 18, 30), dialect);
            assertThat(between.sql()).isEqualTo("\"age\" BETWEEN ? AND ?");
            assertThat(between.parameters()).containsExactly(18, 30);
        }

        @Test
        @DisplayName("Should convert equals with null to IS NULL")
        void shouldConvertEqualsNullToIsNull() {
            BoundSql eqNull = WhereClauseBuilder.build(WherePredicate.eq("parent_id", null), dialect);
            assertThat(eqNull.sql()).isEqualTo("\"parent_id\" IS NULL");
            assertThat(eqNull.parameters()).isEmpty();

            BoundSql neqNull = WhereClauseBuilder.build(WherePredicate.neq("parent_id", null), dialect);
            assertThat(neqNull.sql()).isEqualTo("\"parent_id\" IS NOT NULL");
            assertThat(neqNull.parameters()).isEmpty();
        }

        @Test
        @DisplayName("Should render NOT predicate with negation")
        void shouldRenderNotPredicate() {
            BoundSql not = WhereClauseBuilder.build(WherePredicate.not(WherePredicate.eq("status", "BANNED")), dialect);
            assertThat(not.sql()).isEqualTo("NOT (\"status\" = ?)");
            assertThat(not.parameters()).containsExactly("BANNED");
        }
    }
}
