package com.graphorm.generator.model;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-12.1: ModelRecordGenerator Unit Tests")
class ModelRecordGeneratorTest {

    @Test
    @DisplayName("Should generate immutable Java 21 model record with component accessors, builder, and with-methods")
    void shouldGenerateModelRecord() throws Exception {
        ModelMetadata model = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id").javaType(Integer.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("name").javaType(String.class).build())
                .addField(FieldMetadata.builder("email").javaType(String.class).unique(true).build())
                .addField(FieldMetadata.builder("departmentId").dbColumnName("department_id").javaType(Integer.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(model).build();
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.model");

        GeneratedSourceFile file = ModelRecordGenerator.generate(model, registry, config);

        assertThat(file.className()).isEqualTo("Student");
        assertThat(file.packageName()).isEqualTo("com.graphorm.test.model.model");
        assertThat(file.content()).contains("public record Student(");
        assertThat(file.content()).contains("Integer id,");
        assertThat(file.content()).contains("String name,");
        assertThat(file.content()).contains("String email,");
        assertThat(file.content()).contains("Integer departmentId");
        assertThat(file.content()).contains("public static final String MODEL_NAME = \"Student\";");
        assertThat(file.content()).contains("public static final String TABLE_NAME = \"students\";");
        assertThat(file.content()).contains("public static Builder builder()");
        assertThat(file.content()).contains("public Student withName(String name)");
        assertThat(file.content()).contains("public Student withEmail(String email)");
        assertThat(file.content()).contains("public Student withDepartmentId(Integer departmentId)");
        assertThat(file.content()).contains("public static final class Builder");

        // Verify compilation cleanly
        var result = InMemoryCompiler.compile(List.of(file));
        result.assertCleanCompilation();

        Class<?> studentClass = result.loadClass("com.graphorm.test.model.model.Student");
        assertThat(studentClass.isRecord()).isTrue();
        assertThat(studentClass.getRecordComponents()).hasSize(4);
    }

    @Test
    @DisplayName("Should generate byte-for-byte identical output for repeated generation runs (Determinism)")
    void shouldGenerateDeterministically() {
        ModelMetadata model = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id").javaType(Integer.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name").javaType(String.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(model).build();
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.model");

        GeneratedSourceFile file1 = ModelRecordGenerator.generate(model, registry, config);
        GeneratedSourceFile file2 = ModelRecordGenerator.generate(model, registry, config);

        assertThat(file1.content()).isEqualTo(file2.content());
    }
}
