package com.graphorm.jdbc.builder;

import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.GroupByClause;
import com.graphorm.core.query.HavingPredicate;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.AggregateType;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("View Aggregations, Computed Fields & GROUP BY SQL Generation Tests (TASK-9.2)")
class ViewAggregationSqlTest {

    private SqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();
    }

    @Test
    @DisplayName("Should generate parameterized SQL with COUNT aggregate and GROUP BY")
    void shouldGenerateCountAndGroupBySql() {
        SelectQuery query = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.id", "studentId")
                .column("s.name", "studentName")
                .column("COUNT(e.id)", "courseCount")
                .join(JoinClause.left(
                        SourceTable.table("enrollments"),
                        "e",
                        WherePredicate.columnEq("s.id", "e.student_id")
                ))
                .groupBy("s.id", "s.name")
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", \"s\".\"name\" AS \"studentName\", COUNT(e.id) AS \"courseCount\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                        "GROUP BY \"s\".\"id\", \"s\".\"name\""
        );
        assertThat(boundSql.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should generate parameterized SQL with multiple aggregates (COUNT, SUM, AVG, MIN, MAX)")
    void shouldGenerateMultipleAggregatesSql() {
        SelectQuery query = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.id", "studentId")
                .column(dialect.buildAggregateSql(AggregateType.COUNT, "e.id"), "courseCount")
                .column(dialect.buildAggregateSql(AggregateType.SUM, "e.credits"), "totalCredits")
                .column(dialect.buildAggregateSql(AggregateType.AVG, "e.grade_points"), "avgGpa")
                .column(dialect.buildAggregateSql(AggregateType.MIN, "e.grade_points"), "minGrade")
                .column(dialect.buildAggregateSql(AggregateType.MAX, "e.grade_points"), "maxGrade")
                .join(JoinClause.left(
                        SourceTable.table("enrollments"),
                        "e",
                        WherePredicate.columnEq("s.id", "e.student_id")
                ))
                .groupBy("s.id")
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", COUNT(e.id) AS \"courseCount\", SUM(e.credits) AS \"totalCredits\", " +
                        "AVG(e.grade_points) AS \"avgGpa\", MIN(e.grade_points) AS \"minGrade\", MAX(e.grade_points) AS \"maxGrade\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                        "GROUP BY \"s\".\"id\""
        );
    }

    @Test
    @DisplayName("Should generate parameterized HAVING clause with bound parameters")
    void shouldGenerateHavingClauseSql() {
        SelectQuery query = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.id", "studentId")
                .column("COUNT(e.id)", "courseCount")
                .join(JoinClause.left(
                        SourceTable.table("enrollments"),
                        "e",
                        WherePredicate.columnEq("s.id", "e.student_id")
                ))
                .groupBy("s.id")
                .having(WherePredicate.gte("COUNT(e.id)", 3))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", COUNT(e.id) AS \"courseCount\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                        "GROUP BY \"s\".\"id\" " +
                        "HAVING COUNT(e.id) >= ?"
        );
        assertThat(boundSql.parameters()).containsExactly(3);
    }

    @Test
    @DisplayName("Should generate comprehensive SQL with WHERE, GROUP BY, HAVING, ORDER BY, LIMIT, and OFFSET")
    void shouldGenerateFullAnalyticalQuerySql() {
        SelectQuery query = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.id", "studentId")
                .column("s.name", "studentName")
                .column("d.name", "departmentName")
                .column("COUNT(e.id)", "courseCount")
                .column("AVG(e.grade_points)", "gpa")
                .join(JoinClause.left(
                        SourceTable.table("departments"),
                        "d",
                        WherePredicate.columnEq("s.department_id", "d.id")
                ))
                .join(JoinClause.left(
                        SourceTable.table("enrollments"),
                        "e",
                        WherePredicate.columnEq("s.id", "e.student_id")
                ))
                .where(WherePredicate.eq("d.name", "Computer Science"))
                .groupBy("s.id", "s.name", "d.name")
                .having(WherePredicate.and(
                        WherePredicate.gte("COUNT(e.id)", 2),
                        WherePredicate.gte("AVG(e.grade_points)", 3.5)
                ))
                .orderBy(OrderByClause.desc("COUNT(e.id)"))
                .limit(10)
                .offset(20)
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", \"s\".\"name\" AS \"studentName\", \"d\".\"name\" AS \"departmentName\", " +
                        "COUNT(e.id) AS \"courseCount\", AVG(e.grade_points) AS \"gpa\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\" " +
                        "LEFT JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                        "WHERE \"d\".\"name\" = ? " +
                        "GROUP BY \"s\".\"id\", \"s\".\"name\", \"d\".\"name\" " +
                        "HAVING (COUNT(e.id) >= ? AND AVG(e.grade_points) >= ?) " +
                        "ORDER BY COUNT(e.id) DESC " +
                        "LIMIT ? OFFSET ?"
        );
        assertThat(boundSql.parameters()).containsExactly(
                "Computer Science",
                2,
                3.5,
                10,
                20
        );
    }
}
