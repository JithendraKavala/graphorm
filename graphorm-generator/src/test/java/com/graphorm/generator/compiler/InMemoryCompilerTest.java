package com.graphorm.generator.compiler;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.generator.engine.CodeGeneratorEngine;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-12.6: In-Memory JavaCompiler Test Harness (Milestone 6)")
class InMemoryCompilerTest {

    private static final String SCHEMA = """
            datasource db {
              provider = "postgresql"
              url      = "postgresql://localhost:5432/testdb"
            }

            generator client {
              provider = "graphorm-client-java"
            }

            enum Role {
              USER
              ADMIN
            }

            type Address {
              street  String
              city    String
              zipCode String
            }

            model Department {
              id       Int       @id @default(autoincrement())
              name     String    @map("dept_name")
              students Student[]
              @@map("departments")
            }

            model Student {
              id           Int        @id @default(autoincrement())
              name         String
              email        String     @unique
              departmentId Int        @map("department_id")
              department   Department @relation(fields: [departmentId], references: [id])
              @@map("students")
            }

            view StudentDashboard {
              studentId      Int     @map("student_id")
              studentName    String  @map("student_name")
              departmentName String  @map("dept_name")
              courseCount    Int     @aggregate(COUNT, "c.id")
              gpa            Float   @expression("ROUND(AVG(e.grade), 2)")

              @@from("Student", as: "s")
              @@leftJoin("Department", as: "d", on: "s.department_id = d.id")
              @@groupBy(["s.id", "s.name", "d.dept_name"])
            }
            """;

    @Test
    @DisplayName("Should generate complete Java sources from schema and compile cleanly with 0 errors and 0 warnings")
    void shouldGenerateAndCompileCleanlyFromSchema(@TempDir Path tempDir) throws Exception {
        ModelRegistry registry = SchemaToMetadataTransformer.transform(SCHEMA);
        assertThat(registry.getAllModels()).hasSize(2);
        assertThat(registry.getAllViewModels()).hasSize(1);
        assertThat(registry.getAllEnums()).hasSize(1);
        assertThat(registry.getAllCompositeTypes()).hasSize(1);

        GeneratorConfig config = GeneratorConfig.builder()
                .basePackage("com.graphorm.generated.test")
                .outputDirectory(tempDir)
                .build();

        CodeGeneratorEngine engine = CodeGeneratorEngine.create(config);
        List<GeneratedSourceFile> files = engine.generateAll(registry);

        // Expected files:
        // 1 Enum (Role)
        // 1 Composite (Address)
        // 2 Models * 6 (Student, Department: record, createInput, updateInput, filter, metadata, client) = 12
        // 1 View * 4 (StudentDashboard: record, filter, metadata, client) = 4
        // 1 Root Client (GraphOrmClient)
        // Total = 1 + 1 + 12 + 4 + 1 = 19 files
        assertThat(files).hasSize(19);

        // In-memory compilation via javax.tools.JavaCompiler (JSR-199)
        var compilationResult = InMemoryCompiler.compile(files);
        compilationResult.assertCleanCompilation();

        ClassLoader cl = compilationResult.classLoader();

        // 1. Verify Enum (Role)
        Class<?> roleClass = cl.loadClass("com.graphorm.generated.test.model.Role");
        assertThat(roleClass.isEnum()).isTrue();
        assertThat(roleClass.getEnumConstants()).hasSize(2);

        // 2. Verify Composite Type (Address)
        Class<?> addressClass = cl.loadClass("com.graphorm.generated.test.model.Address");
        assertThat(addressClass.isRecord()).isTrue();

        // 3. Verify Model Record (Student) via Builder
        Class<?> studentClass = cl.loadClass("com.graphorm.generated.test.model.Student");
        assertThat(studentClass.isRecord()).isTrue();
        Method builderMethod = studentClass.getMethod("builder");
        Object builder = builderMethod.invoke(null);
        builder.getClass().getMethod("id", Integer.class).invoke(builder, 42);
        builder.getClass().getMethod("name", String.class).invoke(builder, "Alice");
        builder.getClass().getMethod("email", String.class).invoke(builder, "alice@example.com");
        builder.getClass().getMethod("departmentId", Integer.class).invoke(builder, 10);
        Object student = builder.getClass().getMethod("build").invoke(builder);

        assertThat(studentClass.getMethod("id").invoke(student)).isEqualTo(42);
        assertThat(studentClass.getMethod("name").invoke(student)).isEqualTo("Alice");
        assertThat(studentClass.getMethod("email").invoke(student)).isEqualTo("alice@example.com");
        assertThat(studentClass.getMethod("departmentId").invoke(student)).isEqualTo(10);

        // 4. Verify Wither method (withName)
        Object modifiedStudent = studentClass.getMethod("withName", String.class).invoke(student, "Alice Cooper");
        assertThat(studentClass.getMethod("name").invoke(modifiedStudent)).isEqualTo("Alice Cooper");
        assertThat(studentClass.getMethod("id").invoke(modifiedStudent)).isEqualTo(42);

        // 5. Verify CreateInput (StudentCreateInput)
        Class<?> createInputClass = cl.loadClass("com.graphorm.generated.test.input.StudentCreateInput");
        assertThat(createInputClass.isRecord()).isTrue();
        Object createBuilder = createInputClass.getMethod("builder").invoke(null);
        createBuilder.getClass().getMethod("name", String.class).invoke(createBuilder, "Bob");
        createBuilder.getClass().getMethod("email", String.class).invoke(createBuilder, "bob@example.com");
        createBuilder.getClass().getMethod("departmentId", Integer.class).invoke(createBuilder, 10);
        Object createInput = createBuilder.getClass().getMethod("build").invoke(createBuilder);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) createInputClass.getMethod("toMap").invoke(createInput);
        assertThat(map).containsEntry("name", "Bob");
        assertThat(map).containsEntry("email", "bob@example.com");
        assertThat(map).containsEntry("department_id", 10);

        // 6. Verify Read-Only View Record (StudentDashboard)
        Class<?> dashboardClass = cl.loadClass("com.graphorm.generated.test.model.StudentDashboard");
        assertThat(dashboardClass.isRecord()).isTrue();
        assertThat(dashboardClass.getRecordComponents()).hasSize(5);

        // 7. Verify Filter Criteria Builder (StudentDashboardFilter)
        Class<?> filterClass = cl.loadClass("com.graphorm.generated.test.filter.StudentDashboardFilter");
        Object filterBuilder = filterClass.getMethod("builder").invoke(null);
        Object courseCountCriteria = filterBuilder.getClass().getMethod("courseCount").invoke(filterBuilder);
        courseCountCriteria.getClass().getMethod("gte", Comparable.class).invoke(courseCountCriteria, 3);
        Object predicate = filterBuilder.getClass().getMethod("build").invoke(filterBuilder);
        assertThat(predicate).isNotNull();

        // 8. Test writing all files to disk
        List<Path> writtenPaths = engine.writeAll(registry, tempDir);
        assertThat(writtenPaths).hasSize(19);
        for (Path p : writtenPaths) {
            assertThat(Files.exists(p)).isTrue();
            assertThat(Files.size(p)).isGreaterThan(0);
        }
    }
}
