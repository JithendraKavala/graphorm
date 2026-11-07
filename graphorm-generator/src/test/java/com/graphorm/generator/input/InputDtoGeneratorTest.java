package com.graphorm.generator.input;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-12.3: InputDtoGenerator Unit Tests")
class InputDtoGeneratorTest {

    @Test
    @DisplayName("Should generate CreateInput and UpdateInput strictly for regular models")
    void shouldGenerateInputsForRegularModel() throws Exception {
        ModelMetadata model = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id").javaType(Integer.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("name").javaType(String.class).build())
                .addField(FieldMetadata.builder("email").javaType(String.class).build())
                .addField(FieldMetadata.builder("departmentId").dbColumnName("department_id").javaType(Integer.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(model).build();
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.input");

        GeneratedSourceFile createFile = InputDtoGenerator.generateCreateInput(model, registry, config);
        GeneratedSourceFile updateFile = InputDtoGenerator.generateUpdateInput(model, registry, config);

        // CreateInput: excludes autoincrement primary key 'id'
        assertThat(createFile.className()).isEqualTo("StudentCreateInput");
        assertThat(createFile.content()).contains("public record StudentCreateInput(");
        assertThat(createFile.content()).doesNotContain("Integer id");
        assertThat(createFile.content()).contains("String name,");
        assertThat(createFile.content()).contains("String email,");
        assertThat(createFile.content()).contains("Integer departmentId");
        assertThat(createFile.content()).contains("public Map<String, Object> toMap()");
        assertThat(createFile.content()).contains("map.put(\"department_id\", this.departmentId)");

        // UpdateInput: excludes primary key 'id'
        assertThat(updateFile.className()).isEqualTo("StudentUpdateInput");
        assertThat(updateFile.content()).contains("public record StudentUpdateInput(");
        assertThat(updateFile.content()).doesNotContain("Integer id");
        assertThat(updateFile.content()).contains("String name,");
        assertThat(updateFile.content()).contains("String email,");
        assertThat(updateFile.content()).contains("Integer departmentId");

        // Verify compilation cleanly
        var result = InMemoryCompiler.compile(List.of(createFile, updateFile));
        result.assertCleanCompilation();

        Class<?> createClass = result.loadClass("com.graphorm.test.input.input.StudentCreateInput");
        assertThat(createClass.isRecord()).isTrue();
    }

    @Test
    @DisplayName("STRICT READ-ONLY INVARIANT: Attempting to generate CreateInput or UpdateInput for view model must throw ReadOnlyViewException")
    void shouldStrictlyRejectInputsForViewModels() {
        ViewModelMetadata viewModel = ViewModelMetadata.builder("StudentDashboard")
                .viewName("student_dashboard")
                .sourceModel("Student", "s")
                .databaseBacked(false)
                .addField(FieldMetadata.builder("studentId").javaType(Integer.class).build())
                .addField(FieldMetadata.builder("gpa").javaType(Double.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addViewModel(viewModel).build();
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.input");

        assertThatThrownBy(() -> InputDtoGenerator.generateCreateInput(viewModel, registry, config))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot generate CreateInput for read-only view model: StudentDashboard");

        assertThatThrownBy(() -> InputDtoGenerator.generateUpdateInput(viewModel, registry, config))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot generate UpdateInput for read-only view model: StudentDashboard");
    }
}
