package com.graphorm.cli.integration;

import com.graphorm.cli.GraphOrmCli;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.GeneratedSourceFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-14.5 & TASK-14.6: PostgreSQL Introspect & CLI End-to-End Integration Tests")
class PostgresIntrospectIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresIntrospectIntegrationTest.class);
    private static final String TEST_SCHEMA = "cli_introspect_test";

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static String jdbcUrl;
    private static String dbUsername;
    private static String dbPassword;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL container for CLI Introspect tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_cli")
                    .withUsername("test_cli")
                    .withPassword("test_cli");
            container.start();

            jdbcUrl = container.getJdbcUrl();
            dbUsername = container.getUsername();
            dbPassword = container.getPassword();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(jdbcUrl);
            ds.setUser(dbUsername);
            ds.setPassword(dbPassword);
            dataSource = ds;
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for CLI Introspect tests...");
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
            dbUsername = "postgres";
            dbPassword = "";

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(jdbcUrl);
            ds.setUser(dbUsername);
            ds.setPassword(dbPassword);
            dataSource = ds;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + TEST_SCHEMA + ";");

            stmt.execute("""
                DROP VIEW IF EXISTS cli_introspect_test.vw_student_summary CASCADE;
                DROP TABLE IF EXISTS cli_introspect_test.enrollments CASCADE;
                DROP TABLE IF EXISTS cli_introspect_test.courses CASCADE;
                DROP TABLE IF EXISTS cli_introspect_test.students CASCADE;
                DROP TABLE IF EXISTS cli_introspect_test.departments CASCADE;

                CREATE TABLE cli_introspect_test.departments (
                    id SERIAL PRIMARY KEY,
                    dept_name VARCHAR(100) NOT NULL
                );

                CREATE TABLE cli_introspect_test.students (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    department_id INT REFERENCES cli_introspect_test.departments(id)
                );

                CREATE TABLE cli_introspect_test.courses (
                    id SERIAL PRIMARY KEY,
                    title VARCHAR(100) NOT NULL
                );

                CREATE TABLE cli_introspect_test.enrollments (
                    id SERIAL PRIMARY KEY,
                    student_id INT REFERENCES cli_introspect_test.students(id),
                    course_id INT REFERENCES cli_introspect_test.courses(id)
                );

                CREATE VIEW cli_introspect_test.vw_student_summary AS
                SELECT
                    s.id AS student_id,
                    s.name AS student_name,
                    d.dept_name AS department_name
                FROM cli_introspect_test.students s
                JOIN cli_introspect_test.departments d ON s.department_id = d.id;
            """);
        }
    }

    @AfterAll
    static void tearDown() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @DisplayName("Should introspect PostgreSQL schema into schema.graphorm, validate, and generate compilable code")
    void shouldIntrospectValidateAndGenerate() throws Exception {
        Path outputSchema = tempDir.resolve("introspected.graphorm");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        // 1. Run graphorm introspect
        int introspectCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "introspect",
                "--url", jdbcUrl,
                "-u", dbUsername,
                "-p", dbPassword,
                "-s", TEST_SCHEMA,
                "-o", outputSchema.toString()
        );

        assertThat(introspectCode).isEqualTo(0);
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString())
                .contains("Successfully introspected database")
                .contains("Found 4 model(s) and 1 view(s)");

        assertThat(Files.exists(outputSchema)).isTrue();
        String schemaContent = Files.readString(outputSchema, StandardCharsets.UTF_8);

        // Verify introspected schema contains expected models, relations, and view
        assertThat(schemaContent)
                .contains("model Department")
                .contains("model Student")
                .contains("model Course")
                .contains("model Enrollment")
                .contains("view VwStudentSummary")
                .contains("@@map(\"departments\")")
                .contains("@@map(\"students\")")
                .contains("@@map(\"vw_student_summary\")")
                .contains("@@view");

        // 2. Run graphorm validate on the introspected schema
        StringWriter validateOut = new StringWriter();
        StringWriter validateErr = new StringWriter();

        int validateCode = GraphOrmCli.execute(new PrintWriter(validateOut), new PrintWriter(validateErr),
                "validate",
                "-s", outputSchema.toString()
        );

        assertThat(validateCode).isEqualTo(0);
        assertThat(validateErr.toString()).isEmpty();
        assertThat(validateOut.toString()).contains("introspected.graphorm' is valid!");

        // 3. Run graphorm generate to produce Java code
        Path generatedDir = tempDir.resolve("generated_code");
        StringWriter generateOut = new StringWriter();
        StringWriter generateErr = new StringWriter();

        int generateCode = GraphOrmCli.execute(new PrintWriter(generateOut), new PrintWriter(generateErr),
                "generate",
                "-s", outputSchema.toString(),
                "-o", generatedDir.toString(),
                "-p", "com.example.introspected"
        );

        assertThat(generateCode).isEqualTo(0);
        assertThat(generateErr.toString()).isEmpty();
        assertThat(generateOut.toString()).contains("Successfully generated");

        // 4. Verify all generated sources compile cleanly using InMemoryCompiler
        List<GeneratedSourceFile> sourceFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(generatedDir)) {
            List<Path> javaPaths = stream.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(javaPaths).isNotEmpty();

            for (Path jp : javaPaths) {
                Path relative = generatedDir.relativize(jp);
                String fileName = jp.getFileName().toString();
                String className = fileName.substring(0, fileName.length() - 5);
                String packageName = relative.getParent() != null ? relative.getParent().toString().replace('/', '.') : "";
                String code = Files.readString(jp, StandardCharsets.UTF_8);
                sourceFiles.add(new GeneratedSourceFile(packageName, className, code));
            }
        }

        InMemoryCompiler.CompilationResult compilation = InMemoryCompiler.compile(sourceFiles);
        compilation.assertCleanCompilation();
        assertThat(compilation.success()).isTrue();
    }

    @Test
    @DisplayName("Should fail when introspect target file exists and --force is not passed")
    void shouldFailWhenFileExistsWithoutForce() throws Exception {
        Path existing = tempDir.resolve("existing.graphorm");
        Files.writeString(existing, "// existing");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "introspect",
                "--url", jdbcUrl,
                "-u", dbUsername,
                "-p", dbPassword,
                "-s", TEST_SCHEMA,
                "-o", existing.toString()
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Error: Target schema file already exists");
    }

    @Test
    @DisplayName("Should fail with informative error when connection fails")
    void shouldFailOnInvalidConnection() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "introspect",
                "--url", "jdbc:postgresql://localhost:59999/nonexistent",
                "-u", "invalid",
                "-p", "invalid",
                "-o", tempDir.resolve("never_created.graphorm").toString()
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Database introspection failed");
    }

    @Test
    @DisplayName("Should fail when JDBC URL is missing")
    void shouldFailWhenNoUrlProvided() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "introspect",
                "-o", tempDir.resolve("test.graphorm").toString()
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Error: Database connection URL is required");
    }
}
