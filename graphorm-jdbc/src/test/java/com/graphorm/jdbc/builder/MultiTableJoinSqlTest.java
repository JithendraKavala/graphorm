package com.graphorm.jdbc.builder;

import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Multi-Table Join SQL Generation Tests in PostgreSQL Dialect (TASK-8.3)")
class MultiTableJoinSqlTest {

    private SqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();
    }

    @Test
    @DisplayName("Should generate parameterized SQL for 2-table LEFT JOIN with ColumnComparisonPredicate")
    void shouldGenerateTwoTableLeftJoinSql() {
        SelectQuery query = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.id", "studentId")
                .column("s.name", "studentName")
                .column("d.name", "departmentName")
                .join(JoinClause.left(
                        SourceTable.table("departments"),
                        "d",
                        WherePredicate.columnEq("s.department_id", "d.id")
                ))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", \"s\".\"name\" AS \"studentName\", \"d\".\"name\" AS \"departmentName\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\""
        );
        assertThat(boundSql.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should generate parameterized SQL for 3-table virtual joined view (LEFT JOIN + INNER JOIN)")
    void shouldGenerateThreeTableJoinedSql() {
        SelectQuery query = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.id", "studentId")
                .column("s.name", "studentName")
                .column("d.name", "departmentName")
                .column("e.grade_points", "gradePoints")
                .join(JoinClause.left(
                        SourceTable.table("departments"),
                        "d",
                        WherePredicate.columnEq("s.department_id", "d.id")
                ))
                .join(JoinClause.inner(
                        SourceTable.table("enrollments"),
                        "e",
                        WherePredicate.columnEq("s.id", "e.student_id")
                ))
                .where(WherePredicate.gte("s.gpa", 3.0))
                .orderBy(OrderByClause.asc("s.name"))
                .limit(20)
                .offset(40)
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", \"s\".\"name\" AS \"studentName\", \"d\".\"name\" AS \"departmentName\", \"e\".\"grade_points\" AS \"gradePoints\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\" " +
                        "INNER JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                        "WHERE \"s\".\"gpa\" >= ? " +
                        "ORDER BY \"s\".\"name\" ASC " +
                        "LIMIT ? OFFSET ?"
        );
        assertThat(boundSql.parameters()).containsExactly(3.0, 20, 40);
    }

    @Test
    @DisplayName("Should extract parameters from compound join conditions")
    void shouldExtractParametersFromJoinConditions() {
        // Join condition: s.id == e.student_id AND e.status == 'ACTIVE'
        WherePredicate joinCond = WherePredicate.and(
                WherePredicate.columnEq("s.id", "e.student_id"),
                WherePredicate.eq("e.status", "ACTIVE")
        );

        SelectQuery query = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.name", "studentName")
                .join(JoinClause.inner(SourceTable.table("enrollments"), "e", joinCond))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"name\" AS \"studentName\" " +
                        "FROM \"students\" AS \"s\" " +
                        "INNER JOIN \"enrollments\" AS \"e\" ON (\"s\".\"id\" = \"e\".\"student_id\" AND \"e\".\"status\" = ?)"
        );
        assertThat(boundSql.parameters()).containsExactly("ACTIVE");
    }

    @Test
    @DisplayName("Should construct JoinClause using string condition factory")
    void shouldConstructJoinClauseFromStringCondition() {
        JoinClause leftJoin = JoinClause.left("departments", "d", "s.dept_id == d.id");
        assertThat(leftJoin.joinType()).isEqualTo(com.graphorm.core.query.JoinType.LEFT);
        assertThat(leftJoin.alias()).isEqualTo("d");
        assertThat(leftJoin.condition()).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);

        JoinClause innerJoin = JoinClause.inner("enrollments", "e", "s.id == e.student_id");
        assertThat(innerJoin.joinType()).isEqualTo(com.graphorm.core.query.JoinType.INNER);
        assertThat(innerJoin.alias()).isEqualTo("e");
        assertThat(innerJoin.condition()).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);
    }
}
