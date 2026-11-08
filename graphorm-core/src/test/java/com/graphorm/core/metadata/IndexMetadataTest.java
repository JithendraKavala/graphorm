package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IndexMetadata, CheckConstraintMetadata & ReferentialAction Unit Tests")
class IndexMetadataTest {

    @Test
    @DisplayName("Should create IndexMetadata via builder with defaults")
    void shouldCreateIndexMetadataViaBuilder() {
        IndexMetadata index = IndexMetadata.builder("idx_student_email")
                .addField("email")
                .unique(true)
                .type(IndexType.BTREE)
                .build();

        assertThat(index.name()).isEqualTo("idx_student_email");
        assertThat(index.fields()).containsExactly("email");
        assertThat(index.isUnique()).isTrue();
        assertThat(index.type()).isEqualTo(IndexType.BTREE);
    }

    @Test
    @DisplayName("Should validate IndexMetadata arguments")
    void shouldValidateIndexMetadataArguments() {
        assertThatThrownBy(() -> new IndexMetadata("", List.of("id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Index name must not be blank");

        assertThatThrownBy(() -> new IndexMetadata("idx_test", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Index must specify at least one field");
    }

    @Test
    @DisplayName("Should parse and convert IndexType correctly")
    void shouldParseIndexType() {
        assertThat(IndexType.from("btree")).isEqualTo(IndexType.BTREE);
        assertThat(IndexType.from("HASH")).isEqualTo(IndexType.HASH);
        assertThat(IndexType.from("gin")).isEqualTo(IndexType.GIN);
        assertThat(IndexType.from(null)).isEqualTo(IndexType.BTREE);
        assertThat(IndexType.BTREE.toSql()).isEqualTo("BTREE");
    }

    @Test
    @DisplayName("Should parse and format ReferentialAction correctly")
    void shouldParseReferentialAction() {
        assertThat(ReferentialAction.from("Cascade")).isEqualTo(ReferentialAction.CASCADE);
        assertThat(ReferentialAction.from("RESTRICT")).isEqualTo(ReferentialAction.RESTRICT);
        assertThat(ReferentialAction.from("SetNull")).isEqualTo(ReferentialAction.SET_NULL);
        assertThat(ReferentialAction.from("SET_DEFAULT")).isEqualTo(ReferentialAction.SET_DEFAULT);
        assertThat(ReferentialAction.from("NoAction")).isEqualTo(ReferentialAction.NO_ACTION);
        assertThat(ReferentialAction.from(null)).isEqualTo(ReferentialAction.NO_ACTION);
        assertThat(ReferentialAction.CASCADE.toSql()).isEqualTo("CASCADE");
        assertThat(ReferentialAction.SET_NULL.toSql()).isEqualTo("SET NULL");
    }

    @Test
    @DisplayName("Should validate CheckConstraintMetadata")
    void shouldValidateCheckConstraint() {
        CheckConstraintMetadata chk = new CheckConstraintMetadata("chk_age", "age >= 18");
        assertThat(chk.name()).isEqualTo("chk_age");
        assertThat(chk.expression()).isEqualTo("age >= 18");

        assertThatThrownBy(() -> new CheckConstraintMetadata("", "x > 0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CheckConstraintMetadata("chk_x", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should support RelationMetadata referential actions")
    void shouldSupportRelationMetadataReferentialActions() {
        RelationMetadata rel = RelationMetadata.builder("department")
                .relationType(RelationType.MANY_TO_ONE)
                .sourceModel("Student")
                .targetModel("Department")
                .foreignKeyField("departmentId")
                .referenceField("id")
                .onDelete(ReferentialAction.CASCADE)
                .onUpdate(ReferentialAction.RESTRICT)
                .build();

        assertThat(rel.onDelete()).isEqualTo(ReferentialAction.CASCADE);
        assertThat(rel.onUpdate()).isEqualTo(ReferentialAction.RESTRICT);
    }

    @Test
    @DisplayName("Should support ModelMetadata indexes, check constraints and schema namespace")
    void shouldSupportModelMetadataDdlExtensions() {
        IndexMetadata idx = IndexMetadata.builder("idx_student_name")
                .addField("firstName")
                .addField("lastName")
                .build();

        CheckConstraintMetadata chk = new CheckConstraintMetadata("chk_gpa", "gpa >= 0.0 AND gpa <= 4.0");

        ModelMetadata model = ModelMetadata.builder("Student")
                .tableName("students")
                .schemaName("university")
                .addIndex(idx)
                .addCheckConstraint(chk)
                .build();

        assertThat(model.schemaName()).isEqualTo("university");
        assertThat(model.indexes()).hasSize(1);
        assertThat(model.findIndex("idx_student_name")).isPresent();
        assertThat(model.checkConstraints()).hasSize(1);
        assertThat(model.findCheckConstraint("chk_gpa")).isPresent();
    }
}
