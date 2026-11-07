package com.graphorm.generator.model;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-12.2: ViewModelRecordGenerator Unit Tests")
class ViewModelRecordGeneratorTest {

    @Test
    @DisplayName("Should generate read-only view model projection record with zero mutation or setter methods")
    void shouldGenerateReadOnlyViewModelRecord() throws Exception {
        ViewModelMetadata viewModel = ViewModelMetadata.builder("StudentDashboard")
                .viewName("student_dashboard")
                .sourceModel("Student", "s")
                .databaseBacked(false)
                .addField(FieldMetadata.builder("studentId").javaType(Integer.class).build())
                .addField(FieldMetadata.builder("studentName").javaType(String.class).build())
                .addField(FieldMetadata.builder("departmentName").javaType(String.class).build())
                .addField(FieldMetadata.builder("courseCount").javaType(Integer.class).aggregate("COUNT").build())
                .addField(FieldMetadata.builder("gpa").javaType(Double.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addViewModel(viewModel).build();
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.view");

        GeneratedSourceFile file = ViewModelRecordGenerator.generate(viewModel, registry, config);

        assertThat(file.className()).isEqualTo("StudentDashboard");
        assertThat(file.content()).contains("public record StudentDashboard(");
        assertThat(file.content()).contains("Integer studentId,");
        assertThat(file.content()).contains("String studentName,");
        assertThat(file.content()).contains("String departmentName,");
        assertThat(file.content()).contains("Integer courseCount,");
        assertThat(file.content()).contains("Double gpa");
        assertThat(file.content()).contains("public static final String VIEW_NAME = \"StudentDashboard\";");
        assertThat(file.content()).contains("public static final boolean IS_DATABASE_BACKED = false;");

        // STRICT READ-ONLY INVARIANT ASSERTIONS:
        // Zero with-methods, zero setters, zero mutation methods
        assertThat(file.content()).doesNotContain("withStudentId");
        assertThat(file.content()).doesNotContain("withCourseCount");
        assertThat(file.content()).doesNotContain("setStudentId");
        assertThat(file.content()).doesNotContain("Builder");

        // Compile cleanly
        var result = InMemoryCompiler.compile(List.of(file));
        result.assertCleanCompilation();

        Class<?> recordClass = result.loadClass("com.graphorm.test.view.model.StudentDashboard");
        assertThat(recordClass.isRecord()).isTrue();
        assertThat(recordClass.getRecordComponents()).hasSize(5);

        // Verify reflection methods: only record components, equals, hashCode, toString
        for (Method m : recordClass.getDeclaredMethods()) {
            assertThat(m.getName()).doesNotStartWith("set");
            assertThat(m.getName()).doesNotStartWith("with");
        }
    }
}
