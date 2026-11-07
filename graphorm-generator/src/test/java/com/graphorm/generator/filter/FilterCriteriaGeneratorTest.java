package com.graphorm.generator.filter;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-12.4: FilterCriteriaGenerator Unit Tests")
class FilterCriteriaGeneratorTest {

    @Test
    @DisplayName("Should generate type-safe filter criteria and criteria builder for regular model")
    void shouldGenerateFilterForRegularModel() throws Exception {
        ModelMetadata model = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id").javaType(Integer.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name").javaType(String.class).build())
                .addField(FieldMetadata.builder("departmentId").dbColumnName("department_id").javaType(Integer.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(model).build();
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.filter");

        GeneratedSourceFile filterFile = FilterCriteriaGenerator.generate(model, registry, config);

        assertThat(filterFile.className()).isEqualTo("StudentFilter");
        assertThat(filterFile.content()).contains("public class StudentFilter");
        assertThat(filterFile.content()).contains("public IntFieldFilter departmentId()");
        assertThat(filterFile.content()).contains("return new IntFieldFilter(\"department_id\")");
        assertThat(filterFile.content()).contains("public StringFieldFilter name()");
        assertThat(filterFile.content()).contains("public static final class Builder implements FilterBuilder<Builder>");

        // Verify compilation cleanly
        var result = InMemoryCompiler.compile(List.of(filterFile));
        result.assertCleanCompilation();

        Class<?> filterClass = result.loadClass("com.graphorm.test.filter.filter.StudentFilter");
        Object filterInstance = filterClass.getField("INSTANCE").get(null);

        // Invoke departmentId().eq(10) via reflection
        Method deptMethod = filterClass.getMethod("departmentId");
        Object deptFilter = deptMethod.invoke(filterInstance);
        Method eqMethod = deptFilter.getClass().getMethod("eq", Object.class);
        WherePredicate pred = (WherePredicate) eqMethod.invoke(deptFilter, 10);

        assertThat(pred).isNotNull();
        assertThat(pred).isInstanceOf(WherePredicate.BinaryPredicate.class);
        WherePredicate.BinaryPredicate bp = (WherePredicate.BinaryPredicate) pred;
        assertThat(bp.column()).isEqualTo("department_id");
        assertThat(bp.value()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should generate type-safe filter criteria for view model with aggregations")
    void shouldGenerateFilterForViewModel() throws Exception {
        ViewModelMetadata viewModel = ViewModelMetadata.builder("StudentDashboard")
                .viewName("student_dashboard")
                .addField(FieldMetadata.builder("studentId").javaType(Integer.class).build())
                .addField(FieldMetadata.builder("courseCount").javaType(Integer.class).aggregate("COUNT").build())
                .addField(FieldMetadata.builder("gpa").javaType(Double.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addViewModel(viewModel).build();
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.test.filter");

        GeneratedSourceFile filterFile = FilterCriteriaGenerator.generate(viewModel, registry, config);

        assertThat(filterFile.className()).isEqualTo("StudentDashboardFilter");
        assertThat(filterFile.content()).contains("public IntFieldFilter courseCount()");
        assertThat(filterFile.content()).contains("public DoubleFieldFilter gpa()");

        var result = InMemoryCompiler.compile(List.of(filterFile));
        result.assertCleanCompilation();
    }
}
