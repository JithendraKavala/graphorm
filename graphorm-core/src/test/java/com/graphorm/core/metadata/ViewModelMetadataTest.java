package com.graphorm.core.metadata;

import com.graphorm.core.query.JoinType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ViewModelMetadata Unit Tests")
class ViewModelMetadataTest {

    @Test
    @DisplayName("Strategy A: Should build database-backed view model with read-only invariant")
    void shouldBuildDatabaseBackedViewModel() {
        ViewModelMetadata dbView = ViewModelMetadata.builder("StudentSummary")
                .viewName("vw_student_summary")
                .databaseBacked(true)
                .materialized(true)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .build();

        assertThat(dbView.name()).isEqualTo("StudentSummary");
        assertThat(dbView.viewName()).isEqualTo("vw_student_summary");
        assertThat(dbView.entityType()).isEqualTo(EntityType.VIEW);
        assertThat(dbView.isReadOnly()).isTrue();
        assertThat(dbView.isDatabaseBacked()).isTrue();
        assertThat(dbView.isMaterialized()).isTrue();
        assertThat(dbView.isRawQuery()).isFalse();
        assertThat(dbView.fields()).hasSize(2);
        assertThat(dbView.hasField("id")).isTrue();
        assertThat(dbView.hasAuthRoles()).isFalse();
    }

    @Test
    @DisplayName("Strategy B: Should build GraphORM-defined joined view model with full mutation defense")
    void shouldBuildGraphOrmDefinedViewModel() {
        List<FieldMetadata> fields = new ArrayList<>(List.of(
                FieldMetadata.builder("studentId", Long.class).dbColumnName("s.id").build(),
                FieldMetadata.builder("departmentName", String.class).dbColumnName("d.name").build(),
                FieldMetadata.builder("courseCount", Integer.class).aggregate("count: e.id").build()
        ));

        List<ViewJoinMetadata> joins = new ArrayList<>(List.of(
                ViewJoinMetadata.builder()
                        .joinType(JoinType.LEFT)
                        .targetModel("Department")
                        .alias("d")
                        .onCondition("s.departmentId == d.id")
                        .build(),
                new ViewJoinMetadata(JoinType.INNER, "Enrollment", "e", "s.id == e.studentId")
        ));

        List<String> groupBy = new ArrayList<>(List.of("s.id", "s.name", "d.name"));

        ViewModelMetadata view = ViewModelMetadata.builder("StudentDashboard")
                .sourceModel("Student", "s")
                .fields(fields)
                .joins(joins)
                .groupBy(groupBy)
                .build();

        assertThat(view.name()).isEqualTo("StudentDashboard");
        assertThat(view.entityType()).isEqualTo(EntityType.VIEW);
        assertThat(view.isReadOnly()).isTrue();
        assertThat(view.sourceModelName()).isEqualTo("Student");
        assertThat(view.sourceAlias()).isEqualTo("s");
        assertThat(view.fields()).hasSize(3);
        assertThat(view.joinDefinitions()).hasSize(2);
        assertThat(view.groupByColumns()).containsExactly("s.id", "s.name", "d.name");
        assertThat(view.isVirtual()).isTrue();
        assertThat(view.isDatabaseBacked()).isFalse();

        // Mutate inputs to ensure defensive copying
        fields.add(FieldMetadata.builder("extra").build());
        joins.clear();
        groupBy.clear();

        assertThat(view.fields()).hasSize(3);
        assertThat(view.joinDefinitions()).hasSize(2);
        assertThat(view.groupByColumns()).hasSize(3);
    }

    @Test
    @DisplayName("Strategy C: Should build raw SQL query view model")
    void shouldBuildRawQueryViewModel() {
        String sql = "SELECT id, name FROM users UNION SELECT id, name FROM archived_users";
        ViewModelMetadata view = ViewModelMetadata.builder("CombinedUsers")
                .rawQuery(sql)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .build();

        assertThat(view.name()).isEqualTo("CombinedUsers");
        assertThat(view.isRawQuery()).isTrue();
        assertThat(view.rawQuery()).isEqualTo(sql);
        assertThat(view.isDatabaseBacked()).isFalse();
        assertThat(view.isReadOnly()).isTrue();
        assertThat(view.fields()).hasSize(2);
    }

    @Test
    @DisplayName("Should configure authRoles on view model")
    void shouldConfigureAuthRolesOnViewModel() {
        ViewModelMetadata view = ViewModelMetadata.builder("ExecutiveDashboard")
                .authRoles("EXEC", "DIRECTOR")
                .build();

        assertThat(view.hasAuthRoles()).isTrue();
        assertThat(view.authRoles()).containsExactly("EXEC", "DIRECTOR");

        ViewModelMetadata viewWithList = ViewModelMetadata.builder("FinanceReport")
                .authRoles(List.of("FINANCE"))
                .build();
        assertThat(viewWithList.hasAuthRoles()).isTrue();
        assertThat(viewWithList.authRoles()).containsExactly("FINANCE");
    }

    @Test
    @DisplayName("Should support backward-compatible constructors")
    void shouldSupportBackwardCompatibleConstructors() {
        ViewModelMetadata legacy9 = new ViewModelMetadata(
                "LegacyView",
                "legacy_v",
                List.of(),
                "Source",
                "s",
                List.of(),
                List.of(),
                false,
                false
        );
        assertThat(legacy9.isReadOnly()).isTrue();
        assertThat(legacy9.hasAuthRoles()).isFalse();
        assertThat(legacy9.authRoles()).isEmpty();

        ViewModelMetadata legacy10 = new ViewModelMetadata(
                "LegacyView10",
                "legacy_v10",
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                false,
                false,
                "SELECT 1"
        );
        assertThat(legacy10.isReadOnly()).isTrue();
        assertThat(legacy10.hasAuthRoles()).isFalse();
        assertThat(legacy10.authRoles()).isEmpty();
    }

    @Test
    @DisplayName("Should validate ViewJoinMetadata parameters")
    void shouldValidateViewJoinMetadata() {
        assertThatThrownBy(() -> new ViewJoinMetadata(null, "Model", "m", "cond"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ViewJoinMetadata(JoinType.LEFT, " ", "m", "cond"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewJoinMetadata(JoinType.LEFT, "Model", "", "cond"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewJoinMetadata(JoinType.LEFT, "Model", "m", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject invalid view model names")
    void shouldRejectInvalidName() {
        assertThatThrownBy(() -> ViewModelMetadata.builder(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ViewModelMetadata(" ", null, null, null, null, null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should satisfy equals, hashCode, and toString")
    void shouldSatisfyRecordContract() {
        ViewModelMetadata v1 = ViewModelMetadata.builder("V").build();
        ViewModelMetadata v2 = ViewModelMetadata.builder("V").build();

        assertThat(v1).isEqualTo(v2);
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
        assertThat(v1.toString()).contains("V", "viewName=v");
        assertThat(v1.isReadOnly()).isTrue();
    }
}
