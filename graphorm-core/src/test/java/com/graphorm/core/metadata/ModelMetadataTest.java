package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModelMetadata Unit Tests")
class ModelMetadataTest {

    @Test
    @DisplayName("Should build ModelMetadata with 100% mutation defense and correct defaults")
    void shouldBuildModelMetadataWithMutationDefense() {
        List<FieldMetadata> fieldList = new ArrayList<>();
        fieldList.add(FieldMetadata.builder("id", Long.class).primaryKey(true).build());
        fieldList.add(FieldMetadata.builder("name", String.class).build());

        List<String> pkList = new ArrayList<>(List.of("id"));
        List<List<String>> uqList = new ArrayList<>();
        uqList.add(new ArrayList<>(List.of("name")));

        RelationMetadata sampleRelation = RelationMetadata.builder("department")
                .relationType(RelationType.MANY_TO_ONE)
                .sourceModel("Student")
                .targetModel("Department")
                .foreignKeyField("departmentId")
                .referenceField("id")
                .build();
        List<RelationMetadata> relList = new ArrayList<>(List.of(sampleRelation));

        ModelMetadata model = ModelMetadata.builder("Student")
                .tableName("students")
                .fields(fieldList)
                .primaryKeyFields(pkList)
                .uniqueConstraints(uqList)
                .relations(relList)
                .build();

        assertThat(model.name()).isEqualTo("Student");
        assertThat(model.tableName()).isEqualTo("students");
        assertThat(model.entityType()).isEqualTo(EntityType.MODEL);
        assertThat(model.isReadOnly()).isFalse();
        assertThat(model.authRoles()).isEmpty();
        assertThat(model.hasAuthRoles()).isFalse();
        assertThat(model.fields()).hasSize(2);
        assertThat(model.primaryKeyFields()).containsExactly("id");
        assertThat(model.isPrimaryKey("id")).isTrue();
        assertThat(model.isPrimaryKey("name")).isFalse();
        assertThat(model.isPrimaryKey(null)).isFalse();

        // Check field lookup
        assertThat(model.hasField("name")).isTrue();
        assertThat(model.hasField("nonExistent")).isFalse();
        assertThat(model.getField("name").javaType()).isEqualTo(String.class);

        // Check relation lookup
        assertThat(model.hasRelation("department")).isTrue();
        assertThat(model.hasRelation("courses")).isFalse();
        assertThat(model.getRelation("department").targetModel()).isEqualTo("Department");

        // Verify mutation defense against external list modification
        fieldList.add(FieldMetadata.builder("hacked", String.class).build());
        pkList.add("hacked");
        uqList.get(0).add("hacked");
        relList.clear();

        assertThat(model.fields()).hasSize(2);
        assertThat(model.primaryKeyFields()).containsExactly("id");
        assertThat(model.uniqueConstraints().get(0)).containsExactly("name");
        assertThat(model.relations()).hasSize(1);

        // Verify mutation defense against returned list modification
        assertThatThrownBy(() -> model.fields().add(FieldMetadata.builder("bad").build()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.primaryKeyFields().add("bad"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.uniqueConstraints().add(List.of("bad")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.uniqueConstraints().get(0).add("bad"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.relations().add(sampleRelation))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should throw descriptive exception for missing field or relation")
    void shouldThrowForMissingFieldOrRelation() {
        ModelMetadata model = ModelMetadata.builder("Course").build();

        assertThatThrownBy(() -> model.getField("missingField"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field 'missingField' not found on entity 'Course'");

        assertThatThrownBy(() -> model.getRelation("missingRel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Relation 'missingRel' not found on model 'Course'");
    }

    @Test
    @DisplayName("Should reject blank or null model name")
    void shouldRejectInvalidName() {
        assertThatThrownBy(() -> ModelMetadata.builder(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ModelMetadata("  ", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Model name must not be blank");
    }

    @Test
    @DisplayName("Should default tableName to lowercase name when null or empty")
    void shouldDefaultTableName() {
        ModelMetadata model = ModelMetadata.builder("Department").build();
        assertThat(model.tableName()).isEqualTo("department");
    }

    @Test
    @DisplayName("Should configure read-only physical model and authRoles")
    void shouldConfigureReadOnlyAndAuthRoles() {
        ModelMetadata readOnlyModel = ModelMetadata.builder("AuditLog")
                .readOnly(true)
                .authRoles("ADMIN", "AUDITOR")
                .build();

        assertThat(readOnlyModel.isReadOnly()).isTrue();
        assertThat(readOnlyModel.hasAuthRoles()).isTrue();
        assertThat(readOnlyModel.authRoles()).containsExactly("ADMIN", "AUDITOR");

        ModelMetadata modelWithList = ModelMetadata.builder("SecureConfig")
                .isReadOnly(false)
                .authRoles(List.of("SUPERADMIN"))
                .build();
        assertThat(modelWithList.isReadOnly()).isFalse();
        assertThat(modelWithList.hasAuthRoles()).isTrue();
        assertThat(modelWithList.authRoles()).containsExactly("SUPERADMIN");
    }

    @Test
    @DisplayName("Should support backward-compatible constructors")
    void shouldSupportBackwardCompatibleConstructors() {
        ModelMetadata legacy6 = new ModelMetadata(
                "Legacy",
                "legacy_table",
                List.of(),
                List.of("id"),
                List.of(),
                List.of()
        );
        assertThat(legacy6.isReadOnly()).isFalse();
        assertThat(legacy6.hasAuthRoles()).isFalse();
        assertThat(legacy6.authRoles()).isEmpty();

        ModelMetadata legacy7 = new ModelMetadata(
                "Legacy7",
                "legacy7_table",
                List.of(),
                List.of("id"),
                List.of(),
                List.of(),
                true
        );
        assertThat(legacy7.isReadOnly()).isTrue();
        assertThat(legacy7.hasAuthRoles()).isFalse();
        assertThat(legacy7.authRoles()).isEmpty();
    }

    @Test
    @DisplayName("Should satisfy equals, hashCode, and toString")
    void shouldSatisfyRecordContract() {
        ModelMetadata m1 = ModelMetadata.builder("User").build();
        ModelMetadata m2 = ModelMetadata.builder("User").build();

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        assertThat(m1.toString()).contains("User", "user");
    }
}
