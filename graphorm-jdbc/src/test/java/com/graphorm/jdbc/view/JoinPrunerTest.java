package com.graphorm.jdbc.view;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.GroupByClause;
import com.graphorm.core.query.HavingPredicate;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Join Pruning Optimization Tests (TASK-9.3)")
class JoinPrunerTest {

    private PostgreSqlDialect dialect;
    private ModelRegistry registry;
    private ViewModelMetadata dashboardView;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();

        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                model Department {
                    id   Int    @id
                    name String
                    @@map("departments")
                }

                model Student {
                    id           Int    @id
                    name         String
                    departmentId Int?   @map("department_id")
                    @@map("students")
                }

                model Enrollment {
                    id          Int   @id
                    studentId   Int   @map("student_id")
                    credits     Int
                    gradePoints Float @map("grade_points")
                    @@map("enrollments")
                }

                view StudentDashboard {
                    studentId      Int     @map("s.id")
                    studentName    String  @map("s.name")
                    departmentName String? @map("d.name")
                    courseCount    Int     @aggregate(count: "e.id")
                    gpa            Float?  @expression("AVG(e.grade_points)")

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                    @@groupBy(["s.id", "s.name", "d.name"])
                }
                """;

        registry = SchemaToMetadataTransformer.transform(schema);
        dashboardView = registry.getViewModel("StudentDashboard");
    }

    @Test
    @DisplayName("Should prune unselected join and omit GROUP BY when no aggregates are requested")
    void shouldPruneUnselectedJoinAndOmitGroupBy() {
        // Only studentName and departmentName requested -> Enrollment join and GROUP BY must be pruned
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry, dialect)
                .select("studentName", "departmentName")
                .build();

        assertThat(query.joins()).hasSize(1);
        assertThat(query.joins().get(0).alias()).isEqualTo("d");
        assertThat(query.groupBy()).isEmpty();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);
        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"name\" AS \"studentName\", \"d\".\"name\" AS \"departmentName\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\""
        );
        assertThat(boundSql.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should retain join if referenced in WHERE predicate even when unselected")
    void shouldRetainJoinReferencedInWhere() {
        // Only studentName requested, but WHERE filters by d.name -> Department join retained, Enrollment pruned
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry, dialect)
                .select("studentName")
                .where(WherePredicate.eq("departmentName", "Computer Science"))
                .build();

        assertThat(query.joins()).hasSize(1);
        assertThat(query.joins().get(0).alias()).isEqualTo("d");
        assertThat(query.groupBy()).isEmpty();

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);
        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"name\" AS \"studentName\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\" " +
                        "WHERE \"d\".\"name\" = ?"
        );
        assertThat(boundSql.parameters()).containsExactly("Computer Science");
    }

    @Test
    @DisplayName("Should retain join if referenced in ORDER BY clause even when unselected")
    void shouldRetainJoinReferencedInOrderBy() {
        // Only studentName requested, but ORDER BY d.name -> Department join retained
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry, dialect)
                .select("studentName")
                .orderByAsc("departmentName")
                .build();

        assertThat(query.joins()).hasSize(1);
        assertThat(query.joins().get(0).alias()).isEqualTo("d");

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);
        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"name\" AS \"studentName\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"departments\" AS \"d\" ON \"s\".\"department_id\" = \"d\".\"id\" " +
                        "ORDER BY \"d\".\"name\" ASC"
        );
    }

    @Test
    @DisplayName("Should retain aggregate join and adjust GROUP BY when aggregate field is selected")
    void shouldRetainAggregateJoinAndAdjustGroupBy() {
        // studentName and courseCount requested -> Department join pruned, Enrollment retained
        // GROUP BY should keep s.id and s.name, but prune d.name
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry, dialect)
                .select("studentName", "courseCount")
                .build();

        assertThat(query.joins()).hasSize(1);
        assertThat(query.joins().get(0).alias()).isEqualTo("e");
        assertThat(query.groupBy()).hasSize(2);
        assertThat(query.groupBy().stream().map(GroupByClause::expression).toList())
                .containsExactly("s.id", "s.name");

        BoundSql boundSql = SelectBuilder.of(dialect).build(query);
        assertThat(boundSql.sql()).isEqualTo(
                "SELECT \"s\".\"name\" AS \"studentName\", COUNT(e.id) AS \"courseCount\" " +
                        "FROM \"students\" AS \"s\" " +
                        "LEFT JOIN \"enrollments\" AS \"e\" ON \"s\".\"id\" = \"e\".\"student_id\" " +
                        "GROUP BY \"s\".\"id\", \"s\".\"name\""
        );
    }

    @Test
    @DisplayName("Should retain transitive join dependencies in ON condition")
    void shouldRetainTransitiveJoinDependencies() {
        // Join B is needed by Join C's ON condition
        JoinClause joinB = JoinClause.left(SourceTable.table("table_b"), "b", WherePredicate.columnEq("s.id", "b.student_id"));
        JoinClause joinC = JoinClause.left(SourceTable.table("table_c"), "c", WherePredicate.columnEq("b.id", "c.b_id"));

        List<ProjectedColumn> columns = List.of(ProjectedColumn.of("c.value", "val"));

        JoinPruner.PruningResult result = JoinPruner.prune(
                "s",
                List.of(joinB, joinC),
                columns,
                null,
                null,
                List.of(),
                List.of()
        );

        assertThat(result.joins()).hasSize(2);
        assertThat(result.joins()).extracting(JoinClause::alias).containsExactly("b", "c");
        assertThat(result.prunedAliases()).isEmpty();
    }
}
