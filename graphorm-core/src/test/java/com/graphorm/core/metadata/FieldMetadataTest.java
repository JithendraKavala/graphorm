package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldMetadata Unit Tests")
class FieldMetadataTest {

    @Test
    @DisplayName("Should create FieldMetadata with default values")
    void shouldCreateFieldMetadataWithDefaults() {
        FieldMetadata field = new FieldMetadata(
                "email",
                String.class,
                "user_email",
                false,
                false,
                true,
                false,
                null
        );

        assertThat(field.name()).isEqualTo("email");
        assertThat(field.javaType()).isEqualTo(String.class);
        assertThat(field.typeName()).isEqualTo("String");
        assertThat(field.dbColumnName()).isEqualTo("user_email");
        assertThat(field.isPrimaryKey()).isFalse();
        assertThat(field.isNullable()).isFalse();
        assertThat(field.isUnique()).isTrue();
        assertThat(field.isGenerated()).isFalse();
        assertThat(field.defaultValue()).isNull();
        assertThat(field.isAggregate()).isFalse();
        assertThat(field.aggregateFunction()).isNull();
        assertThat(field.expression()).isNull();
        assertThat(field.isRelation()).isFalse();
        assertThat(field.relationName()).isNull();
    }

    @Test
    @DisplayName("Should default dbColumnName to name when null or blank")
    void shouldDefaultDbColumnNameToName() {
        FieldMetadata field1 = FieldMetadata.builder("username").build();
        assertThat(field1.dbColumnName()).isEqualTo("username");

        FieldMetadata field2 = new FieldMetadata("id", Long.class, "", true, false, true, true, "autoincrement()");
        assertThat(field2.dbColumnName()).isEqualTo("id");
    }

    @Test
    @DisplayName("Should reject null or blank field name")
    void shouldRejectNullOrBlankFieldName() {
        assertThatThrownBy(() -> FieldMetadata.builder(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new FieldMetadata("", String.class, "col", false, true, false, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name must not be blank");
    }

    @Test
    @DisplayName("Should build aggregate and expression fields")
    void shouldBuildAggregateAndExpressionFields() {
        FieldMetadata aggField = FieldMetadata.builder("totalCredits")
                .javaType(Integer.class)
                .aggregate("SUM")
                .build();

        assertThat(aggField.isAggregate()).isTrue();
        assertThat(aggField.aggregateFunction()).isEqualTo("SUM");

        FieldMetadata exprField = FieldMetadata.builder("gpa")
                .javaType(Double.class)
                .expression("AVG(grade_points)")
                .build();

        assertThat(exprField.expression()).isEqualTo("AVG(grade_points)");
    }

    @Test
    @DisplayName("Should build relational navigation field")
    void shouldBuildRelationalField() {
        FieldMetadata relField = FieldMetadata.builder("department")
                .relation("StudentDepartment")
                .build();

        assertThat(relField.isRelation()).isTrue();
        assertThat(relField.relationName()).isEqualTo("StudentDepartment");
    }

    @Test
    @DisplayName("Should satisfy equals, hashCode, and toString")
    void shouldSatisfyRecordContract() {
        FieldMetadata f1 = FieldMetadata.builder("id", Long.class).primaryKey(true).build();
        FieldMetadata f2 = FieldMetadata.builder("id", Long.class).primaryKey(true).build();

        assertThat(f1).isEqualTo(f2);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
        assertThat(f1.toString()).contains("id", "Long");
    }
}
