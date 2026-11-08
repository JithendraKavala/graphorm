package com.graphorm.core.query.template;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SqlFragment Escape Hatch Template Unit Tests")
class SqlFragmentTest {

    private ModelRegistry registry;

    @BeforeEach
    void setUp() {
        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).dbColumnName("full_name").build())
                .addField(FieldMetadata.builder("age", Integer.class).build())
                .build();

        registry = ModelRegistry.builder()
                .addModel(student)
                .build();
    }

    @Test
    @DisplayName("Should compile template resolving table and column placeholders and extracting named parameters")
    void testCompileTemplate() {
        String template = "SELECT {col:Student.name}, {col:Student.age} FROM {table:Student} WHERE {col:Student.age} > :minAge";
        Map<String, Object> params = Map.of("minAge", 18);

        SqlFragment fragment = SqlFragment.compile(template, registry, params);

        assertThat(fragment.sql()).isEqualTo("SELECT \"full_name\", \"age\" FROM \"students\" WHERE \"age\" > ?");
        assertThat(fragment.parameters()).containsExactly(18);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on unknown model")
    void testUnknownModel() {
        String template = "SELECT * FROM {table:UnknownModel}";
        assertThatThrownBy(() -> SqlFragment.compile(template, registry, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown entity 'UnknownModel'");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on unknown column")
    void testUnknownColumn() {
        String template = "SELECT {col:Student.unknownField} FROM {table:Student}";
        assertThatThrownBy(() -> SqlFragment.compile(template, registry, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown field 'unknownField'");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when named parameter is missing")
    void testMissingParameter() {
        String template = "SELECT * FROM {table:Student} WHERE id = :studentId";
        assertThatThrownBy(() -> SqlFragment.compile(template, registry, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing value for named parameter ':studentId'");
    }
}
