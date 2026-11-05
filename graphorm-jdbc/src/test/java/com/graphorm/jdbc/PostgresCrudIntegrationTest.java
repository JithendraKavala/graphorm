package com.graphorm.jdbc;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.NullsOrder;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.DeleteBuilder;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test suite verifying full CRUD operations, pagination, sorting,
 * read-only view guards, and Milestone 1 deliverables against a real PostgreSQL database.
 */
@DisplayName("PostgreSQL CRUD & Milestone 1 Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresCrudIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresCrudIntegrationTest.class);

    public record StudentRecord(
            Long id,
            String name,
            String email,
            Double gpa,
            Long departmentId,
            LocalDateTime createdAt
    ) {}

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;

    @BeforeAll
    static void setUpAll() throws Exception {
        dialect = new PostgreSqlDialect();

        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL 16 container...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_test")
                    .withUsername("test_user")
                    .withPassword("test_password");
            container.start();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(container.getJdbcUrl());
            ds.setUser(container.getUsername());
            ds.setPassword(container.getPassword());
            dataSource = ds;
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432...");
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerNames(new String[]{"localhost"});
            ds.setPortNumbers(new int[]{5432});
            ds.setUser("postgres");
            ds.setPassword("");
            ds.setDatabaseName("postgres");
            dataSource = ds;
        }

        executor = new JdbcExecutor(dataSource, dialect);

        // Initialize database schema DDL
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS students CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments CASCADE;");

            stmt.execute("""
                CREATE TABLE departments (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE
                );
            """);

            stmt.execute("""
                CREATE TABLE students (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    gpa DOUBLE PRECISION,
                    department_id BIGINT REFERENCES departments(id),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. INSERT: Should insert department and students into PostgreSQL")
    void shouldInsertRecords() {
        // 1. Insert department
        BoundSql insertDept = InsertBuilder.of(dialect)
                .into("departments")
                .value("name", "Computer Science")
                .returning("id")
                .build();

        Optional<Map<String, Object>> deptRow = executor.queryForObject(insertDept);
        assertThat(deptRow).isPresent();
        Long deptId = ((Number) deptRow.get().get("id")).longValue();
        assertThat(deptId).isPositive();

        // 2. Insert student with RETURNING *
        BoundSql insertStudent = InsertBuilder.of(dialect)
                .into("students")
                .value("name", "Alice Turing")
                .value("email", "alice@university.edu")
                .value("gpa", 3.95)
                .value("department_id", deptId)
                .returningAll()
                .build();

        Optional<StudentRecord> studentRecord = executor.queryForObject(insertStudent, StudentRecord.class);
        assertThat(studentRecord).isPresent();
        assertThat(studentRecord.get().name()).isEqualTo("Alice Turing");
        assertThat(studentRecord.get().email()).isEqualTo("alice@university.edu");
        assertThat(studentRecord.get().gpa()).isEqualTo(3.95);
        assertThat(studentRecord.get().departmentId()).isEqualTo(deptId);
        assertThat(studentRecord.get().createdAt()).isNotNull();

        // 3. Insert second student without returning
        BoundSql insertSecond = InsertBuilder.of(dialect)
                .into("students")
                .value("name", "Bob Hopper")
                .value("email", "bob@university.edu")
                .value("gpa", 3.40)
                .value("department_id", deptId)
                .build();

        int rows = executor.executeUpdate(insertSecond);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("2. SELECT: Should query student by ID and by email with WherePredicate")
    void shouldQueryStudentByIdAndEmail() {
        SelectQuery query = SelectQuery.from("students")
                .column("id")
                .column("name")
                .column("email")
                .column("gpa")
                .column("department_id", "departmentId")
                .column("created_at", "createdAt")
                .where(WherePredicate.eq("email", "alice@university.edu"))
                .build();

        Optional<StudentRecord> result = executor.queryForObject(query, StudentRecord.class);
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Alice Turing");
        assertThat(result.get().email()).isEqualTo("alice@university.edu");
    }

    @Test
    @Order(3)
    @DisplayName("3. PAGINATION & SORTING: Should query students with LIMIT, OFFSET and ORDER BY")
    void shouldQueryWithPaginationAndSorting() {
        SelectQuery query = SelectQuery.from("students")
                .column("id")
                .column("name")
                .column("email")
                .column("gpa")
                .column("department_id", "departmentId")
                .column("created_at", "createdAt")
                .orderBy(new OrderByClause("gpa", OrderDirection.DESC, NullsOrder.LAST))
                .limit(1)
                .offset(0)
                .build();

        List<StudentRecord> students = executor.queryForList(query, StudentRecord.class);
        assertThat(students).hasSize(1);
        assertThat(students.getFirst().name()).isEqualTo("Alice Turing");
        assertThat(students.getFirst().gpa()).isEqualTo(3.95);

        // Fetch page 2 (second student)
        SelectQuery page2 = SelectQuery.from("students")
                .column("id")
                .column("name")
                .column("email")
                .column("gpa")
                .column("department_id", "departmentId")
                .column("created_at", "createdAt")
                .orderBy(new OrderByClause("gpa", OrderDirection.DESC, NullsOrder.LAST))
                .limit(1)
                .offset(1)
                .build();

        List<StudentRecord> page2Students = executor.queryForList(page2, StudentRecord.class);
        assertThat(page2Students).hasSize(1);
        assertThat(page2Students.getFirst().name()).isEqualTo("Bob Hopper");
        assertThat(page2Students.getFirst().gpa()).isEqualTo(3.40);
    }

    @Test
    @Order(4)
    @DisplayName("4. UPDATE: Should update student record in PostgreSQL")
    void shouldUpdateStudent() {
        BoundSql updateSql = UpdateBuilder.of(dialect)
                .table("students")
                .set("gpa", 3.99)
                .where(WherePredicate.eq("email", "alice@university.edu"))
                .returningAll()
                .build();

        Optional<StudentRecord> updated = executor.queryForObject(updateSql, StudentRecord.class);
        assertThat(updated).isPresent();
        assertThat(updated.get().gpa()).isEqualTo(3.99);

        // Verify with SELECT
        SelectQuery verify = SelectQuery.from("students")
                .column("gpa")
                .where(WherePredicate.eq("email", "alice@university.edu"))
                .build();

        Optional<Map<String, Object>> map = executor.queryForObject(verify);
        assertThat(map).isPresent();
        assertThat(((Number) map.get().get("gpa")).doubleValue()).isEqualTo(3.99);
    }

    @Test
    @Order(5)
    @DisplayName("5. DELETE: Should delete student from PostgreSQL")
    void shouldDeleteStudent() {
        BoundSql deleteSql = DeleteBuilder.of(dialect)
                .from("students")
                .where(WherePredicate.eq("email", "bob@university.edu"))
                .build();

        int affected = executor.executeUpdate(deleteSql);
        assertThat(affected).isEqualTo(1);

        // Verify row is gone
        SelectQuery verify = SelectQuery.from("students")
                .where(WherePredicate.eq("email", "bob@university.edu"))
                .build();

        Optional<Map<String, Object>> row = executor.queryForObject(verify);
        assertThat(row).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("6. READ-ONLY VIEW GUARD: Should immediately reject write queries targeting view models")
    void shouldEnforceReadOnlyViewGuards() {
        ViewModelMetadata view = ViewModelMetadata.builder("StudentSummaryView")
                .addField(com.graphorm.core.metadata.FieldMetadata.builder("studentId", Long.class).build())
                .addField(com.graphorm.core.metadata.FieldMetadata.builder("studentName", String.class).build())
                .build();

        assertThatThrownBy(() -> InsertBuilder.of(dialect).forEntity(view))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentSummaryView")
                .hasMessageContaining("INSERT");

        assertThatThrownBy(() -> UpdateBuilder.of(dialect).forEntity(view))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentSummaryView")
                .hasMessageContaining("UPDATE");

        assertThatThrownBy(() -> DeleteBuilder.of(dialect).forEntity(view))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentSummaryView")
                .hasMessageContaining("DELETE");
    }

    @Test
    @Order(7)
    @DisplayName("7. MILESTONE 1 VERIFICATION: Define schema DSL, parse to ModelRegistry, execute CRUD without DAO or entity class")
    void shouldVerifyMilestone1Deliverable() {
        // Schema definition in GraphORM DSL
        String schemaContent = """
            datasource db {
              provider = "postgresql"
              url      = "env(DATABASE_URL)"
            }

            model Student {
              id        Int     @id @default(autoincrement())
              name      String
              email     String  @unique
              gpa       Float?

              @@map("students")
            }

            view StudentDashboard {
              studentId   Int     @map("s.id")
              studentName String  @map("s.name")

              @@from(Student, as: "s")
            }
        """;

        // Parse, validate, and transform schema DSL into ModelRegistry
        ModelRegistry registry = SchemaToMetadataTransformer.transform(schemaContent);

        assertThat(registry.findModel("Student")).isPresent();
        assertThat(registry.findViewModel("StudentDashboard")).isPresent();

        ModelMetadata studentModel = registry.getModel("Student");
        ViewModelMetadata dashboardView = registry.getViewModel("StudentDashboard");

        // Verify Read-Only constraint on parsed View Model
        assertThat(dashboardView.isReadOnly()).isTrue();
        assertThatThrownBy(() -> InsertBuilder.of(dialect).forEntity(dashboardView))
                .isInstanceOf(ReadOnlyViewException.class);

        // Step 5: Execute INSERT on PostgreSQL using parsed ModelMetadata without DAO or entity classes
        String uniqueEmail = "milestone1_" + System.currentTimeMillis() + "@university.edu";
        BoundSql insertSql = InsertBuilder.of(dialect)
                .forEntity(studentModel)
                .value("name", "Grace Hopper")
                .value("email", uniqueEmail)
                .value("gpa", 4.0)
                .returningAll()
                .build();

        Optional<Map<String, Object>> insertedMap = executor.queryForObject(insertSql);
        assertThat(insertedMap).isPresent();
        assertThat(insertedMap.get()).containsEntry("name", "Grace Hopper");
        assertThat(insertedMap.get()).containsEntry("email", uniqueEmail);

        // Step 6: Query back using SelectBuilder forEntity without custom DAO or RowMapper
        SelectQuery selectQuery = SelectBuilder.forEntity(studentModel)
                .where(WherePredicate.eq("email", uniqueEmail))
                .build();

        Optional<Map<String, Object>> retrievedMap = executor.queryForObject(selectQuery);
        assertThat(retrievedMap).isPresent();
        assertThat(retrievedMap.get().get("name")).isEqualTo("Grace Hopper");
        assertThat(retrievedMap.get().get("email")).isEqualTo(uniqueEmail);
        assertThat(((Number) retrievedMap.get().get("gpa")).doubleValue()).isEqualTo(4.0);

        log.info("Milestone 1 successfully verified! Student defined in schema.graphorm, parsed into ModelRegistry, inserted into real PostgreSQL, and queried back with zero DAOs or entity classes.");
    }
}
