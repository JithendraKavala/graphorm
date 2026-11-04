package com.graphorm.core;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.metadata.ViewJoinMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.JoinType;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 2 Milestone Deliverable: ModelRegistry & Unified Query AST")
class Phase2MilestoneDeliverableTest {

    @Test
    @DisplayName("Milestone 2.4: Programmatically construct ModelRegistry and SelectQuery AST, asserting immutability")
    void testPhase2MilestoneDeliverable() {
        // 1. Construct Student model metadata
        ModelMetadata studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("name", String.class).nullable(false).build())
                .addField(FieldMetadata.builder("email", String.class).unique(true).build())
                .addField(FieldMetadata.builder("departmentId", Long.class).dbColumnName("department_id").build())
                .addPrimaryKeyField("id")
                .addUniqueConstraint("email")
                .addRelation(RelationMetadata.builder("department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Department")
                        .foreignKeyField("departmentId")
                        .referenceField("id")
                        .build())
                .build();

        // 2. Construct Department model metadata
        ModelMetadata departmentModel = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addPrimaryKeyField("id")
                .build();

        // 3. Construct StudentDashboard view model metadata (Strategy B: GraphORM-defined joined view)
        ViewModelMetadata studentDashboardView = ViewModelMetadata.builder("StudentDashboard")
                .sourceModel("Student", "s")
                .addField(FieldMetadata.builder("studentId", Long.class).dbColumnName("s.id").build())
                .addField(FieldMetadata.builder("studentName", String.class).dbColumnName("s.name").build())
                .addField(FieldMetadata.builder("departmentName", String.class).dbColumnName("d.name").build())
                .addField(FieldMetadata.builder("courseCount", Integer.class).aggregate("COUNT(e.id)").build())
                .addField(FieldMetadata.builder("gpa", Double.class).expression("AVG(e.grade_points)").build())
                .addJoin(ViewJoinMetadata.builder()
                        .joinType(JoinType.LEFT)
                        .targetModel("Department")
                        .alias("d")
                        .onCondition("s.departmentId == d.id")
                        .build())
                .addJoin(ViewJoinMetadata.builder()
                        .joinType(JoinType.LEFT)
                        .targetModel("Enrollment")
                        .alias("e")
                        .onCondition("s.id == e.studentId")
                        .build())
                .groupBy("s.id", "s.name", "d.name")
                .build();

        // 4. Register all in ModelRegistry
        ModelRegistry registry = ModelRegistry.builder()
                .addModel(studentModel)
                .addModel(departmentModel)
                .addViewModel(studentDashboardView)
                .build();

        // Assert registry contents and lookup integrity
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.getModel("Student")).isEqualTo(studentModel);
        assertThat(registry.getModel("Department")).isEqualTo(departmentModel);
        assertThat(registry.getViewModel("StudentDashboard")).isEqualTo(studentDashboardView);

        // Assert strict read-only semantics on the view
        assertThat(studentModel.isReadOnly()).isFalse();
        assertThat(studentDashboardView.isReadOnly()).isTrue();
        assertThatThrownBy(() -> QueryGuards.assertWritable(studentDashboardView, "INSERT"))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform INSERT on read-only view 'StudentDashboard'");

        assertThatThrownBy(() -> QueryGuards.createUpdate(studentDashboardView, Map.of("name", "Test"), null))
                .isInstanceOf(ReadOnlyViewException.class);

        // 5. Construct unified SelectQuery AST corresponding to the view definition
        SelectQuery selectQuery = SelectQuery.from(SourceTable.table("students"))
                .as("s")
                .column("s.id", "studentId")
                .column("s.name", "studentName")
                .column("d.name", "departmentName")
                .column("COUNT(e.id)", "courseCount")
                .column("AVG(e.grade_points)", "gpa")
                .hiddenColumn("s.department_id", "__hidden_dept_id")
                .join(JoinClause.left("departments", "d", WherePredicate.eq("s.department_id", "d.id")))
                .join(JoinClause.left("enrollments", "e", WherePredicate.eq("s.id", "e.student_id")))
                .where(WherePredicate.and(
                        WherePredicate.eq("s.active", true),
                        WherePredicate.gt("s.enrollment_year", 2020)
                ))
                .groupBy("s.id", "s.name", "d.name")
                .having(WherePredicate.gt("COUNT(e.id)", 0))
                .orderByDesc("gpa")
                .limit(25)
                .offset(0)
                .build();

        // Assert query structure
        assertThat(selectQuery.source().identifier()).isEqualTo("students");
        assertThat(selectQuery.tableAlias()).isEqualTo("s");
        assertThat(selectQuery.columns()).hasSize(6);
        assertThat(selectQuery.columns().stream().filter(ProjectedColumn::isHiddenDependency).count()).isEqualTo(1);
        assertThat(selectQuery.joins()).hasSize(2);
        assertThat(selectQuery.groupBy()).hasSize(3);
        assertThat(selectQuery.limit()).isEqualTo(25);
        assertThat(selectQuery.offset()).isZero();

        // Assert 100% mutation defense on query collections
        assertThatThrownBy(() -> selectQuery.columns().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> selectQuery.joins().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> selectQuery.groupBy().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> selectQuery.orderBy().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.getAllModels().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.getAllViewModels().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
