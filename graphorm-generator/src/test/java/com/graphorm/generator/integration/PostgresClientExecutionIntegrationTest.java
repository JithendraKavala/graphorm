package com.graphorm.generator.integration;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.CodeGeneratorEngine;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Milestone 6: PostgresClientExecutionIntegrationTest")
class PostgresClientExecutionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresClientExecutionIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static ModelRegistry registry;
    private static InMemoryCompiler.CompilationResult compilationResult;
    private static Object clientInstance;
    private static ClassLoader classLoader;

    private static final String SCHEMA = """
            datasource db {
              provider = "postgresql"
              url      = "postgresql://localhost:5432/testdb"
            }

            model Department {
              id       Int       @id @default(autoincrement())
              name     String    @map("dept_name")
              students Student[]
              @@map("departments_m6")
            }

            model Course {
              id          Int          @id @default(autoincrement())
              title       String
              enrollments Enrollment[]
              @@map("courses_m6")
            }

            model Student {
              id           Int          @id @default(autoincrement())
              name         String
              email        String       @unique
              departmentId Int          @map("department_id")
              department   Department   @relation(fields: [departmentId], references: [id])
              enrollments  Enrollment[]
              @@map("students_m6")
            }

            model Enrollment {
              id        Int     @id @default(autoincrement())
              studentId Int     @map("student_id")
              courseId  Int     @map("course_id")
              grade     Float
              student   Student @relation(fields: [studentId], references: [id])
              course    Course  @relation(fields: [courseId], references: [id])
              @@map("enrollments_m6")
            }

            view StudentDashboard {
              studentId      Int     @map("s.id")
              studentName    String  @map("s.name")
              departmentName String  @map("d.dept_name")
              courseCount    Int     @aggregate(COUNT, "c.id")
              gpa            Float   @expression("ROUND(AVG(e.grade)::numeric, 2)")

              @@from("Student", as: "s")
              @@leftJoin("Department", as: "d", on: "s.department_id = d.id")
              @@leftJoin("Enrollment", as: "e", on: "s.id = e.student_id")
              @@leftJoin("Course", as: "c", on: "e.course_id = c.id")
              @@groupBy(["s.id", "s.name", "d.dept_name"])
            }
            """;

    @BeforeAll
    static void setUp() throws Exception {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL container for Milestone 6...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_m6")
                    .withUsername("test_m6")
                    .withPassword("test_m6");
            container.start();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(container.getJdbcUrl());
            ds.setUser(container.getUsername());
            ds.setPassword(container.getPassword());
            dataSource = ds;
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for Milestone 6...");
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerNames(new String[]{"localhost"});
            ds.setPortNumbers(new int[]{5432});
            ds.setUser("postgres");
            ds.setPassword("");
            ds.setDatabaseName("postgres");
            dataSource = ds;
        }

        // Create tables and seed data
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                DROP TABLE IF EXISTS enrollments_m6 CASCADE;
                DROP TABLE IF EXISTS students_m6 CASCADE;
                DROP TABLE IF EXISTS courses_m6 CASCADE;
                DROP TABLE IF EXISTS departments_m6 CASCADE;

                CREATE TABLE departments_m6 (\n                    id SERIAL PRIMARY KEY,
                    dept_name VARCHAR(100) NOT NULL
                );

                CREATE TABLE courses_m6 (\n                    id SERIAL PRIMARY KEY,
                    title VARCHAR(100) NOT NULL
                );

                CREATE TABLE students_m6 (\n                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    department_id INT REFERENCES departments_m6(id)
                );

                CREATE TABLE enrollments_m6 (\n                    id SERIAL PRIMARY KEY,
                    student_id INT REFERENCES students_m6(id),
                    course_id INT REFERENCES courses_m6(id),
                    grade DOUBLE PRECISION NOT NULL
                );

                INSERT INTO departments_m6 (dept_name) VALUES ('Computer Science'), ('Mathematics');
                INSERT INTO courses_m6 (title) VALUES ('Algorithms'), ('Databases'), ('Calculus');
                INSERT INTO students_m6 (name, email, department_id) VALUES
                    ('Alice Smith', 'alice@university.edu', 1),
                    ('Bob Jones', 'bob@university.edu', 1),
                    ('Charlie Brown', 'charlie@university.edu', 2);

                INSERT INTO enrollments_m6 (student_id, course_id, grade) VALUES
                    (1, 1, 3.8),
                    (1, 2, 4.0),
                    (2, 1, 3.2),
                    (3, 3, 3.9);
            """);
        }

        // 1. Transform schema to ModelRegistry
        registry = SchemaToMetadataTransformer.transform(SCHEMA);

        // 2. Generate sources
        GeneratorConfig config = GeneratorConfig.of("com.graphorm.m6");
        CodeGeneratorEngine engine = CodeGeneratorEngine.create(config);
        List<GeneratedSourceFile> sources = engine.generateAll(registry);

        // 3. Compile in-memory cleanly
        compilationResult = InMemoryCompiler.compile(sources);
        compilationResult.assertCleanCompilation();

        classLoader = compilationResult.classLoader();

        // 4. Instantiate GraphOrmClient via reflection: GraphOrmClient.create(dataSource, registry)
        Class<?> clientClass = classLoader.loadClass("com.graphorm.m6.client.GraphOrmClient");
        Method createMethod = clientClass.getMethod("create", DataSource.class, ModelRegistry.class);
        clientInstance = createMethod.invoke(null, dataSource, registry);
    }

    @AfterAll
    static void tearDown() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @DisplayName("Milestone 6: Execute db.student().findMany() and verify loaded students")
    void shouldExecuteStudentFindMany() throws Exception {
        Method studentAccessorMethod = clientInstance.getClass().getMethod("student");
        Object studentClient = studentAccessorMethod.invoke(clientInstance);

        Method findManyMethod = studentClient.getClass().getMethod("findMany");
        @SuppressWarnings("unchecked")
        List<Object> students = (List<Object>) findManyMethod.invoke(studentClient);

        assertThat(students).hasSize(3);

        Class<?> studentRecordClass = classLoader.loadClass("com.graphorm.m6.model.Student");
        Method nameMethod = studentRecordClass.getMethod("name");

        List<String> names = students.stream()
                .map(s -> {
                    try {
                        return (String) nameMethod.invoke(s);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        assertThat(names).containsExactlyInAnyOrder("Alice Smith", "Bob Jones", "Charlie Brown");
    }

    @Test
    @DisplayName("Milestone 6: Execute db.student().create(...) and verify newly inserted student in PostgreSQL")
    void shouldExecuteStudentCreate() throws Exception {
        Method studentAccessorMethod = clientInstance.getClass().getMethod("student");
        Object studentClient = studentAccessorMethod.invoke(clientInstance);

        Class<?> createInputClass = classLoader.loadClass("com.graphorm.m6.input.StudentCreateInput");
        Object builder = createInputClass.getMethod("builder").invoke(null);
        builder.getClass().getMethod("name", String.class).invoke(builder, "Diana Prince");
        builder.getClass().getMethod("email", String.class).invoke(builder, "diana@university.edu");
        builder.getClass().getMethod("departmentId", Integer.class).invoke(builder, 1);
        Object createInput = builder.getClass().getMethod("build").invoke(builder);

        Method createMethod = studentClient.getClass().getMethod("create", createInputClass);
        Object createdStudent = createMethod.invoke(studentClient, createInput);

        assertThat(createdStudent).isNotNull();
        Method idMethod = createdStudent.getClass().getMethod("id");
        Method nameMethod = createdStudent.getClass().getMethod("name");

        Integer createdId = (Integer) idMethod.invoke(createdStudent);
        assertThat(createdId).isNotNull().isGreaterThan(3);
        assertThat(nameMethod.invoke(createdStudent)).isEqualTo("Diana Prince");

        Method countMethod = studentClient.getClass().getMethod("count");
        long count = (long) countMethod.invoke(studentClient);
        assertThat(count).isEqualTo(4L);
    }

    @Test
    @DisplayName("Milestone 6: Execute db.studentDashboard().findMany() and verify view joins & aggregations")
    void shouldExecuteStudentDashboardFindMany() throws Exception {
        Method dashboardAccessorMethod = clientInstance.getClass().getMethod("studentDashboard");
        Object dashboardClient = dashboardAccessorMethod.invoke(clientInstance);

        Method findManyMethod = dashboardClient.getClass().getMethod("findMany");
        @SuppressWarnings("unchecked")
        List<Object> dashboards = (List<Object>) findManyMethod.invoke(dashboardClient);

        assertThat(dashboards).hasSizeGreaterThanOrEqualTo(3);

        Class<?> dashboardRecordClass = classLoader.loadClass("com.graphorm.m6.model.StudentDashboard");
        Method studentNameMethod = dashboardRecordClass.getMethod("studentName");
        Method deptNameMethod = dashboardRecordClass.getMethod("departmentName");
        Method courseCountMethod = dashboardRecordClass.getMethod("courseCount");
        Method gpaMethod = dashboardRecordClass.getMethod("gpa");

        Object aliceDashboard = dashboards.stream()
                .filter(d -> {
                    try {
                        return "Alice Smith".equals(studentNameMethod.invoke(d));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow();

        assertThat(deptNameMethod.invoke(aliceDashboard)).isEqualTo("Computer Science");
        assertThat(courseCountMethod.invoke(aliceDashboard)).isEqualTo(2);
        assertThat((Double) gpaMethod.invoke(aliceDashboard)).isEqualTo(3.9);
    }

    @Test
    @DisplayName("Milestone 6: Strict Read-Only Verification on StudentDashboardClient (Zero Mutation Methods)")
    void shouldVerifyStrictReadOnlyOnDashboardClient() throws Exception {
        Method dashboardAccessorMethod = clientInstance.getClass().getMethod("studentDashboard");
        Object dashboardClient = dashboardAccessorMethod.invoke(clientInstance);

        for (Method m : dashboardClient.getClass().getMethods()) {
            assertThat(m.getName()).doesNotContain("create");
            assertThat(m.getName()).doesNotContain("update");
            assertThat(m.getName()).doesNotContain("delete");
        }
    }
}
