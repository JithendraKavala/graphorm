package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RelationMetadata Unit Tests")
class RelationMetadataTest {

    @Test
    @DisplayName("Should build relation metadata and provide defensive copying")
    void shouldBuildRelationMetadataWithDefensiveCopying() {
        List<String> fkList = new ArrayList<>(List.of("departmentId"));
        List<String> refList = new ArrayList<>(List.of("id"));

        RelationMetadata relation = RelationMetadata.builder("department")
                .relationType(RelationType.MANY_TO_ONE)
                .sourceModel("Student")
                .targetModel("Department")
                .foreignKeyFields(fkList)
                .referenceFields(refList)
                .build();

        assertThat(relation.relationName()).isEqualTo("department");
        assertThat(relation.relationType()).isEqualTo(RelationType.MANY_TO_ONE);
        assertThat(relation.sourceModel()).isEqualTo("Student");
        assertThat(relation.targetModel()).isEqualTo("Department");
        assertThat(relation.foreignKeyFields()).containsExactly("departmentId");
        assertThat(relation.referenceFields()).containsExactly("id");
        assertThat(relation.joinTable()).isNull();

        // Mutate original source list - should not mutate record
        fkList.add("extraColumn");
        assertThat(relation.foreignKeyFields()).containsExactly("departmentId");

        // Mutating returned list must throw UnsupportedOperationException
        assertThatThrownBy(() -> relation.foreignKeyFields().add("maliciousField"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> relation.referenceFields().add("maliciousField"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should support many-to-many relationship with join table")
    void shouldSupportManyToManyRelation() {
        RelationMetadata relation = RelationMetadata.builder("courses")
                .relationType(RelationType.MANY_TO_MANY)
                .sourceModel("Student")
                .targetModel("Course")
                .foreignKeyField("studentId")
                .referenceField("courseId")
                .joinTable("student_courses")
                .build();

        assertThat(relation.relationType()).isEqualTo(RelationType.MANY_TO_MANY);
        assertThat(relation.joinTable()).isEqualTo("student_courses");
    }

    @Test
    @DisplayName("Should reject invalid or null parameters")
    void shouldRejectInvalidParameters() {
        assertThatThrownBy(() -> RelationMetadata.builder(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new RelationMetadata("", RelationType.ONE_TO_ONE, "S", "T", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RelationMetadata("rel", null, "S", "T", List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new RelationMetadata("rel", RelationType.ONE_TO_ONE, " ", "T", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RelationMetadata("rel", RelationType.ONE_TO_ONE, "S", " ", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should satisfy equals, hashCode, and toString")
    void shouldSatisfyRecordContract() {
        RelationMetadata r1 = RelationMetadata.builder("dept")
                .relationType(RelationType.MANY_TO_ONE)
                .sourceModel("Student")
                .targetModel("Department")
                .foreignKeyField("deptId")
                .referenceField("id")
                .build();

        RelationMetadata r2 = RelationMetadata.builder("dept")
                .relationType(RelationType.MANY_TO_ONE)
                .sourceModel("Student")
                .targetModel("Department")
                .foreignKeyField("deptId")
                .referenceField("id")
                .build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1.toString()).contains("dept", "MANY_TO_ONE", "Student", "Department");
    }
}
