package com.graphorm.generator.client;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.CodeGeneratorEngine;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-12.5: ClientApiGenerator Unit Tests")
class ClientApiGeneratorTest {

    @Test
    @DisplayName("Should generate model client with full CRUD and view client with strictly read-only methods")
    void shouldGenerateClientApi() throws Exception {
        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id").javaType(Integer.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("name").javaType(String.class).build())
                .addField(FieldMetadata.builder("departmentId").dbColumnName("department_id").javaType(Integer.class).build())
                .build();

        ViewModelMetadata dashboard = ViewModelMetadata.builder("StudentDashboard")
                .viewName("student_dashboard")
                .sourceModel("Student", "s")
                .addField(FieldMetadata.builder("studentId").javaType(Integer.class).build())
                .addField(FieldMetadata.builder("courseCount").javaType(Integer.class).aggregate("COUNT").build())
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(student)
                .addViewModel(dashboard)
                .build();

        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.client");
        CodeGeneratorEngine engine = CodeGeneratorEngine.create(config);

        List<GeneratedSourceFile> files = engine.generateAll(registry);

        // Verify compilation cleanly in memory with zero errors and zero warnings
        var result = InMemoryCompiler.compile(files);
        result.assertCleanCompilation();

        Class<?> studentClientClass = result.loadClass("com.graphorm.test.client.client.StudentClient");
        Class<?> dashboardClientClass = result.loadClass("com.graphorm.test.client.client.StudentDashboardClient");
        Class<?> graphOrmClientClass = result.loadClass("com.graphorm.test.client.client.GraphOrmClient");

        // Assert StudentClient implements ModelAccessor
        assertThat(ModelAccessor.class.isAssignableFrom(studentClientClass)).isTrue();
        assertThat(studentClientClass.getMethod("create", result.loadClass("com.graphorm.test.client.input.StudentCreateInput"))).isNotNull();
        assertThat(studentClientClass.getMethod("update", Object.class, result.loadClass("com.graphorm.test.client.input.StudentUpdateInput"))).isNotNull();
        assertThat(studentClientClass.getMethod("delete", Object.class)).isNotNull();

        // Assert StudentDashboardClient implements ONLY ReadOnlyModelAccessor
        assertThat(ReadOnlyModelAccessor.class.isAssignableFrom(dashboardClientClass)).isTrue();
        assertThat(ModelAccessor.class.isAssignableFrom(dashboardClientClass)).isFalse();

        // Strictly verify zero mutation methods on view client
        for (Method m : dashboardClientClass.getDeclaredMethods()) {
            assertThat(m.getName()).doesNotContain("create");
            assertThat(m.getName()).doesNotContain("update");
            assertThat(m.getName()).doesNotContain("delete");
        }

        // Assert GraphOrmClient has accessors: student(), studentDashboard()
        assertThat(graphOrmClientClass.getMethod("student")).isNotNull();
        assertThat(graphOrmClientClass.getMethod("studentDashboard")).isNotNull();
    }
}
