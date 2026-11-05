package com.graphorm.jdbc.view;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewJoinMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.JoinType;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ViewQueryPlanner Unit Tests (TASK-8.4)")
class ViewQueryPlannerTest {

    private ModelRegistry registry;
    private ViewModelMetadata dashboardView;
    private PostgreSqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();

        ModelMetadata studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("departmentId", Long.class).dbColumnName("department_id").build())
                .addField(FieldMetadata.builder("gpa", Double.class).build())
                .build();

        ModelMetadata deptModel = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .build();

        ModelMetadata enrollmentModel = ModelMetadata.builder("Enrollment")
                .tableName("enrollments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("studentId", Long.class).dbColumnName("student_id").build())
                .addField(FieldMetadata.builder("courseName", String.class).dbColumnName("course_name").build())
                .addField(FieldMetadata.builder("gradePoints", Double.class).dbColumnName("grade_points").build())
                .build();

        dashboardView = ViewModelMetadata.builder("StudentDashboard")
                .sourceModel("Student", "s")
                .addJoin(ViewJoinMetadata.builder()
                        .joinType(JoinType.LEFT)
                        .targetModel("Department")
                        .alias("d")
                        .onCondition("s.departmentId == d.id")
                        .build())
                .addJoin(ViewJoinMetadata.builder()
                        .joinType(JoinType.INNER)
                        .targetModel("Enrollment")
                        .alias("e")
                        .onCondition("s.id == e.studentId")
                        .build())
                .addField(FieldMetadata.builder("studentId", Long.class).dbColumnName("s.id").build())
                .addField(FieldMetadata.builder("studentName", String.class).dbColumnName("s.name").build())
                .addField(FieldMetadata.builder("departmentName", String.class).dbColumnName("d.name").build())
                .addField(FieldMetadata.builder("courseName", String.class).dbColumnName("e.course_name").build())
                .addField(FieldMetadata.builder("gradePoints", Double.class).dbColumnName("e.grade_points").build())
                .build();

        registry = ModelRegistry.builder()
                .addModel(studentModel)
                .addModel(deptModel)
                .addModel(enrollmentModel)
                .addViewModel(dashboardView)
                .build();
    }

    @Test
    @DisplayName("Should plan virtual view query with dynamic multi-table joins and field name resolution")
    void shouldPlanVirtualViewQuery() {
        SelectQuery query = ViewQueryPlanner.plan(dashboardView, registry).build();
        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", \"s\".\"name\" AS \"studentName\", \"d\".\"name\" AS \"departmentName\", " +
                        "\"e\".\"course_name\" AS \"courseName\", \"e\".\"grade_points\" AS \"gradePoints\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\" " +
                        "INNER JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\""
        );
        assertThat(boundSql.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should map pushdown filter predicates from view field aliases to source columns")
    void shouldMapPushdownFilter() {
        // Query using view field names: studentName and departmentName
        WherePredicate viewFilter = WherePredicate.and(
                WherePredicate.like("studentName", "A%"),
                WherePredicate.eq("departmentName", "Computer Science")
        );

        WherePredicate mappedFilter = ViewQueryPlanner.mapFilter(viewFilter, dashboardView);

        BoundSql boundSql = com.graphorm.jdbc.builder.WhereClauseBuilder.buildWhereClause(mappedFilter, dialect);
        assertThat(boundSql.sql()).isEqualTo(" WHERE (\"s\".\"name\" LIKE ? AND \"d\".\"name\" = ?)");
        assertThat(boundSql.parameters()).containsExactly("A%", "Computer Science");
    }

    @Test
    @DisplayName("Should map pushdown order-by clauses from view field aliases to source columns")
    void shouldMapPushdownOrderBy() {
        List<OrderByClause> viewOrders = List.of(
                OrderByClause.asc("studentName"),
                OrderByClause.desc("departmentName")
        );

        List<OrderByClause> mappedOrders = ViewQueryPlanner.mapOrderBy(viewOrders, dashboardView);

        assertThat(mappedOrders).hasSize(2);
        assertThat(mappedOrders.get(0).expression()).isEqualTo("s.name");
        assertThat(mappedOrders.get(0).direction()).isEqualTo(com.graphorm.core.query.OrderDirection.ASC);
        assertThat(mappedOrders.get(1).expression()).isEqualTo("d.name");
        assertThat(mappedOrders.get(1).direction()).isEqualTo(com.graphorm.core.query.OrderDirection.DESC);
    }

    @Test
    @DisplayName("Should build full query via fluent ViewQueryBuilder with mapped filters and orders")
    void shouldBuildQueryViaFluentBuilder() {
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry)
                .where(WherePredicate.and(
                        WherePredicate.gte("gradePoints", 3.5),
                        WherePredicate.eq("departmentName", "Mathematics")
                ))
                .orderByAsc("studentName")
                .limit(10)
                .offset(20)
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);

        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"id\" AS \"studentId\", \"s\".\"name\" AS \"studentName\", \"d\".\"name\" AS \"departmentName\", " +
                        "\"e\".\"course_name\" AS \"courseName\", \"e\".\"grade_points\" AS \"gradePoints\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\" " +
                        "INNER JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                        "WHERE (\"e\".\"grade_points\" >= ? AND \"d\".\"name\" = ?) " +
                        "ORDER BY \"s\".\"name\" ASC " +
                        "LIMIT ? OFFSET ?"
        );
        assertThat(boundSql.parameters()).containsExactly(3.5, "Mathematics", 10, 20);
    }

    @Test
    @DisplayName("Should integrate with SelectBuilder.forEntity(entity, registry)")
    void shouldIntegrateWithSelectBuilderForEntity() {
        SelectQuery query = SelectBuilder.forEntity(dashboardView, registry)
                .where(WherePredicate.eq("s.name", "Ada Lovelace"))
                .build();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);
        assertThat(boundSql.sql()).contains("FROM \"students\" AS \"s\" LEFT JOIN \"departments\" AS \"d\"");
        assertThat(boundSql.parameters()).containsExactly("Ada Lovelace");
    }
}
