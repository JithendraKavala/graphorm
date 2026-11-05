package com.graphorm.core.query.relation;

import com.graphorm.core.exception.CircularRelationException;
import com.graphorm.core.exception.EntityNotFoundException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.WherePredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RelationPlanner Unit Tests (TASK-6.1)")
class RelationPlannerTest {

    private ModelRegistry modelRegistry;
    private RelationPlanner planner;

    @BeforeEach
    void setUp() {
        // Build Department model
        ModelMetadata department = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("code", String.class).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("students")
                        .relationType(RelationType.ONE_TO_MANY)
                        .sourceModel("Department")
                        .targetModel("Student")
                        .build())
                .build();

        // Build Student model
        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("departmentId", Long.class).dbColumnName("department_id").nullable(true).build())
                .addField(FieldMetadata.builder("profileId", Long.class).dbColumnName("profile_id").nullable(true).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Department")
                        .foreignKeyField("departmentId")
                        .referenceField("id")
                        .build())
                .addRelation(RelationMetadata.builder("profile")
                        .relationType(RelationType.ONE_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Profile")
                        .foreignKeyField("profileId")
                        .referenceField("id")
                        .build())
                .build();

        // Build Profile model
        ModelMetadata profile = ModelMetadata.builder("Profile")
                .tableName("student_profiles")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("bio", String.class).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("student")
                        .relationType(RelationType.ONE_TO_ONE)
                        .sourceModel("Profile")
                        .targetModel("Student")
                        .build())
                .build();

        modelRegistry = ModelRegistry.builder()
                .addModel(department)
                .addModel(student)
                .addModel(profile)
                .build();

        planner = RelationPlanner.of(modelRegistry);
    }

    @Test
    @DisplayName("Should plan Many-to-One relation query (Student -> Department)")
    void shouldPlanManyToOneRelation() {
        List<Object> deptIds = List.of(1L, 2L, 1L); // contains duplicates
        RelationPlan plan = planner.plan("Student", "department", deptIds);

        assertThat(plan.sourceModelName()).isEqualTo("Student");
        assertThat(plan.targetModelName()).isEqualTo("Department");
        assertThat(plan.targetTableName()).isEqualTo("departments");
        assertThat(plan.sourceJoinField()).isEqualTo("departmentId");
        assertThat(plan.targetJoinField()).isEqualTo("id");
        assertThat(plan.targetJoinColumn()).isEqualTo("id");
        assertThat(plan.isToOne()).isTrue();
        assertThat(plan.isToMany()).isFalse();
        assertThat(plan.keys()).containsExactly(1L, 2L); // deduplicated

        // Inspect SelectQuery AST
        assertThat(plan.selectQuery().source().identifier()).isEqualTo("departments");
        assertThat(plan.selectQuery().columns()).extracting(ProjectedColumn::alias)
                .containsExactly("id", "name", "code");
        assertThat(plan.selectQuery().where()).isInstanceOf(WherePredicate.InPredicate.class);

        WherePredicate.InPredicate inPred = (WherePredicate.InPredicate) plan.selectQuery().where();
        assertThat(inPred.column()).isEqualTo("id");
        assertThat(inPred.values()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Should plan One-to-Many relation query (Department -> students)")
    void shouldPlanOneToManyRelation() {
        List<Object> deptIds = List.of(10L, 20L);
        RelationPlan plan = planner.plan("Department", "students", deptIds);

        assertThat(plan.sourceModelName()).isEqualTo("Department");
        assertThat(plan.targetModelName()).isEqualTo("Student");
        assertThat(plan.targetTableName()).isEqualTo("students");
        assertThat(plan.sourceJoinField()).isEqualTo("id");
        assertThat(plan.targetJoinField()).isEqualTo("departmentId");
        assertThat(plan.targetJoinColumn()).isEqualTo("department_id");
        assertThat(plan.isToOne()).isFalse();
        assertThat(plan.isToMany()).isTrue();
        assertThat(plan.keys()).containsExactly(10L, 20L);

        // Inspect SelectQuery AST
        assertThat(plan.selectQuery().source().identifier()).isEqualTo("students");
        assertThat(plan.selectQuery().columns()).extracting(ProjectedColumn::alias)
                .containsExactly("id", "name", "departmentId", "profileId");
    }

    @Test
    @DisplayName("Should plan One-to-One relation query (Student -> Profile)")
    void shouldPlanOneToOneRelation() {
        List<Object> profileIds = List.of(100L);
        RelationPlan plan = planner.plan("Student", "profile", profileIds);

        assertThat(plan.sourceModelName()).isEqualTo("Student");
        assertThat(plan.targetModelName()).isEqualTo("Profile");
        assertThat(plan.sourceJoinField()).isEqualTo("profileId");
        assertThat(plan.targetJoinField()).isEqualTo("id");
        assertThat(plan.targetJoinColumn()).isEqualTo("id");
        assertThat(plan.isToOne()).isTrue();
        assertThat(plan.isToMany()).isFalse();
        assertThat(plan.keys()).containsExactly(100L);
    }

    @Test
    @DisplayName("Should plan One-to-One inverse relation query (Profile -> Student)")
    void shouldPlanOneToOneInverseRelation() {
        List<Object> profileIds = List.of(5L);
        RelationPlan plan = planner.plan("Profile", "student", profileIds);

        assertThat(plan.sourceModelName()).isEqualTo("Profile");
        assertThat(plan.targetModelName()).isEqualTo("Student");
        assertThat(plan.sourceJoinField()).isEqualTo("id");
        assertThat(plan.targetJoinField()).isEqualTo("profileId");
        assertThat(plan.targetJoinColumn()).isEqualTo("profile_id");
        assertThat(plan.isToOne()).isTrue();
    }

    @Test
    @DisplayName("Should handle empty and null keys gracefully")
    void shouldHandleEmptyAndNullKeys() {
        RelationPlan planEmpty = planner.plan("Student", "department", List.of());
        assertThat(planEmpty.keys()).isEmpty();

        RelationPlan planNulls = planner.plan("Student", "department", java.util.Arrays.asList(null, null));
        assertThat(planNulls.keys()).isEmpty();
    }

    @Test
    @DisplayName("Should detect circular relation loop when visiting existing active model")
    void shouldDetectCircularLoop() {
        Set<String> activePath = new LinkedHashSet<>(List.of("Student", "Department"));

        assertThatThrownBy(() -> planner.plan(
                modelRegistry.getModel("Department"),
                modelRegistry.getModel("Department").getRelation("students"),
                List.of(1L),
                activePath
        ))
                .isInstanceOf(CircularRelationException.class)
                .hasMessageContaining("Circular relation loop detected")
                .hasMessageContaining("Student -> Department -> Student");
    }

    @Test
    @DisplayName("Should detect circular relation loop during path planning")
    void shouldDetectCircularLoopDuringPathPlanning() {
        // Path: Student -> department -> students -> department (cycle!)
        List<String> cyclicPath = List.of("department", "students", "department");

        assertThatThrownBy(() -> planner.planPath("Student", cyclicPath, List.of(1L)))
                .isInstanceOf(CircularRelationException.class)
                .hasMessageContaining("Circular relation loop detected");
    }

    @Test
    @DisplayName("Should plan valid multi-hop path without cycles")
    void shouldPlanValidPath() {
        // Path: Student -> department
        List<String> validPath = List.of("department");
        List<RelationPlan> plans = planner.planPath("Student", validPath, List.of(1L));

        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().targetModelName()).isEqualTo("Department");
    }

    @Test
    @DisplayName("Should fail when unknown relation or unknown model is queried")
    void shouldThrowOnInvalidLookups() {
        assertThatThrownBy(() -> planner.plan("UnknownModel", "dept", List.of(1)))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> planner.plan("Student", "unknownRelation", List.of(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Relation 'unknownRelation' not found");
    }
}
