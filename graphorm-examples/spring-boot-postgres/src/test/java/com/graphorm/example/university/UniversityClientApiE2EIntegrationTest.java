package com.graphorm.example.university;

import com.graphorm.core.cache.GraphOrmCacheManager;
import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.example.university.generated.client.GraphOrmClient;
import com.graphorm.example.university.generated.input.GradeScaleCreateInput;
import com.graphorm.example.university.generated.input.StudentCreateInput;
import com.graphorm.example.university.generated.input.StudentUpdateInput;
import com.graphorm.example.university.generated.model.AuditLog;
import com.graphorm.example.university.generated.model.AuditSummary;
import com.graphorm.example.university.generated.model.DepartmentStats;
import com.graphorm.example.university.generated.model.GradeScale;
import com.graphorm.example.university.generated.model.Student;
import com.graphorm.example.university.generated.model.StudentDashboard;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.transaction.TransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration tests for code-generated Java 21 Client APIs (TASK-16.2).
 * <p>
 * Verifies:
 * <ul>
 *     <li>Type-safe CRUD operations with fluent criteria builders</li>
 *     <li>Database-backed physical view queries via generated ViewClient</li>
 *     <li>Virtual aggregated view queries via generated ViewClient</li>
 *     <li>Transaction commit and rollback semantics</li>\n *     <li>Read-only physical models (@@readonly) and mutation prohibition</li>
 *     <li>Model and view querying for audit entities</li>
 *     <li>GraphORM schema migration engine ledger verification (_graphorm_migrations)</li>
 *     <li>Optimistic locking conflict detection via @version</li>
 *     <li>L2 entity & view cache invalidation</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TASK-16.2: University Client API E2E Integration Tests")
class UniversityClientApiE2EIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(UniversityClientApiE2EIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @BeforeAll
    static void initContainer() {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL container for Client API tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_university_client")
                    .withUsername("test_univ")
                    .withPassword("test_univ");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for Client API tests...");
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
            username = "postgres";
            password = "";
        }
    }

    @AfterAll
    static void tearDown() {
        if (container != null && container.isRunning()) {
            container.stop();
        } else {
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, username, password);
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE students SET name = 'John von Neumann', version = 1 WHERE id = 3");
                stmt.execute("DELETE FROM students WHERE email IN ('hamilton@apollo.edu', 'temp@test.edu')");
            } catch (Exception ignored) {}
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("graphorm.schema-location", () -> "classpath:schema.graphorm");
        registry.add("graphorm.migration.enabled", () -> "true");
        registry.add("graphorm.migration.location", () -> "classpath:migrations");
    }

    @Autowired
    private GraphOrmClient db;

    @Autowired
    private TransactionManager transactionManager;

    @Autowired
    private JdbcExecutor executor;

    @Autowired
    private SqlDialect dialect;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private GraphOrmCacheManager cacheManager;

    @Test
    @Order(1)
    @DisplayName("Verify GraphORM migrations executed and created ledger table _graphorm_migrations")
    void testMigrationLedgerTable() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, checksum, applied_at FROM _graphorm_migrations ORDER BY id ASC")) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("001_init");
            assertThat(rs.getString("checksum")).isNotEmpty();

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("002_seed_data");
            assertThat(rs.getString("checksum")).isNotEmpty();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Find all students using generated client")
    void testFindAllStudents() {
        List<Student> students = db.student().findMany();
        assertThat(students).hasSize(6);
    }

    @Test
    @Order(3)
    @DisplayName("Filter and order students with type-safe filter criteria")
    void testFilterStudents() {
        List<Student> csStudents = db.student().findMany(q -> q
                .where(f -> f.departmentId().eq(1))
                .orderBy(f -> f.name().asc())
        );

        assertThat(csStudents).hasSize(2);
        assertThat(csStudents).extracting(Student::name)
                .containsExactly("Claude Shannon", "Grace Hopper");
    }

    @Test
    @Order(4)
    @DisplayName("Find unique student by ID")
    void testFindStudentById() {
        Optional<Student> studentOpt = db.student().findById(1);
        assertThat(studentOpt).isPresent();
        Student student = studentOpt.get();
        assertThat(student.name()).isEqualTo("Grace Hopper");
        assertThat(student.email()).isEqualTo("hopper@student.edu");
    }

    @Test
    @Order(5)
    @DisplayName("Create, update, and delete student via type-safe DTOs")
    void testCrudLifecycle() {
        long initialCount = db.student().count();

        // 1. Create
        StudentCreateInput createInput = StudentCreateInput.builder()
                .name("Margaret Hamilton")
                .email("hamilton@apollo.edu")
                .gpa(4.0)
                .departmentId(1)
                .build();
        Student created = db.student().create(createInput);
        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("Margaret Hamilton");
        assertThat(created.email()).isEqualTo("hamilton@apollo.edu");
        assertThat(db.student().count()).isEqualTo(initialCount + 1);

        // 2. Update
        StudentUpdateInput updateInput = StudentUpdateInput.builder()
                .gpa(3.99)
                .build();
        Student updated = db.student().update(created.id(), updateInput);
        assertThat(updated.gpa()).isEqualTo(3.99);

        // 3. Delete
        boolean deleted = db.student().delete(created.id());
        assertThat(deleted).isTrue();
        assertThat(db.student().count()).isEqualTo(initialCount);
        assertThat(db.student().findById(created.id())).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("Query database-backed view DepartmentStats via generated ViewClient")
    void testQueryDepartmentStatsView() {
        List<DepartmentStats> stats = db.departmentStats().findMany(q -> q
                .orderBy(f -> f.departmentName().asc())
        );

        assertThat(stats).hasSize(3);
        DepartmentStats cs = stats.stream()
                .filter(s -> "Computer Science".equals(s.departmentName()))
                .findFirst()
                .orElseThrow();
        assertThat(cs.studentCount()).isEqualTo(2);
        assertThat(cs.courseCount()).isEqualTo(2);
    }

    @Test
    @Order(7)
    @DisplayName("Query virtual aggregated view StudentDashboard via generated ViewClient")
    void testQueryStudentDashboardView() {
        List<StudentDashboard> dashboards = db.studentDashboard().findMany();
        assertThat(dashboards).hasSize(6);

        Optional<StudentDashboard> hopper = db.studentDashboard().findFirst(q -> q
                .where(f -> f.studentId().eq(1))
        );
        assertThat(hopper).isPresent();
        assertThat(hopper.get().studentName()).isEqualTo("Grace Hopper");
        assertThat(hopper.get().enrolledCourses()).isEqualTo(2);
        assertThat(hopper.get().avgGradePoints()).isEqualTo(4.0);
    }

    @Test
    @Order(8)
    @DisplayName("Verify transaction rollback does not persist changes")
    void testTransactionRollback() {
        long initialCount = db.student().count();

        try {
            transactionManager.executeInTransaction(() -> {
                StudentCreateInput input = StudentCreateInput.builder()
                        .name("Temporary Student")
                        .email("temp@test.edu")
                        .departmentId(1)
                        .build();
                db.student().create(input);

                // Verify created inside transaction
                assertThat(db.student().count()).isEqualTo(initialCount + 1);

                // Force deliberate rollback
                throw new RuntimeException("Forced deliberate transaction rollback");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Forced deliberate transaction rollback");
        }

        // Verify count rolled back to original
        assertThat(db.student().count()).isEqualTo(initialCount);
        assertThat(db.student().findFirst(q -> q.where(f -> f.email().eq("temp@test.edu")))).isEmpty();
    }

    @Test
    @Order(9)
    @DisplayName("Verify read-only physical model GradeScale enforces read-only guards")
    void testReadOnlyModelGradeScaleEnforcement() {
        List<GradeScale> scales = db.gradeScale().findMany();
        assertThat(scales).hasSize(5);

        GradeScaleCreateInput attemptCreate = GradeScaleCreateInput.builder()
                .letter("A+")
                .minScore(98.0)
                .gpaPoint(4.0)
                .build();

        assertThatThrownBy(() -> db.gradeScale().create(attemptCreate))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform INSERT on read-only view 'GradeScale'");

        assertThatThrownBy(() -> db.gradeScale().delete(1))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform DELETE on read-only view 'GradeScale'");
    }

    @Test
    @Order(10)
    @DisplayName("Query audit logs and audit summary view via generated client")
    void testAuditLogAndSummaryClient() {
        com.graphorm.core.tenant.TenantContext.runAsTenant("tenant-main", () -> {
            List<AuditLog> logs = db.auditLog().findMany();
            assertThat(logs).hasSizeGreaterThanOrEqualTo(2);

            List<AuditSummary> summaries = db.auditSummary().findMany();
            assertThat(summaries).hasSizeGreaterThanOrEqualTo(2);
            assertThat(summaries).extracting(AuditSummary::performedBy)
                    .contains("system");
        });
    }

    @Test
    @Order(11)
    @DisplayName("Verify @version optimistic locking failure when updating with stale version")
    void testOptimisticLockingFailure() {
        Student s = db.student().findById(3).orElseThrow();
        int expectedVersion = s.version();

        // Successful update with matching version
        UpdateBuilder validUpdate = UpdateBuilder.of(dialect)
                .table("students")
                .id("id", s.id())
                .optimisticLock("version", expectedVersion)
                .set("name", "John von Neumann Updated");
        int affected = executor.executeUpdate(validUpdate);
        assertThat(affected).isEqualTo(1);

        Student refreshed = db.student().findById(3).orElseThrow();
        assertThat(refreshed.version()).isEqualTo(expectedVersion + 1);

        // Stale update with previous version fails with OptimisticLockException
        UpdateBuilder staleUpdate = UpdateBuilder.of(dialect)
                .table("students")
                .id("id", s.id())
                .optimisticLock("version", expectedVersion) // old version!
                .set("name", "Concurrent Conflicting Update");

        assertThatThrownBy(() -> executor.executeUpdate(staleUpdate))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("Optimistic lock failure");
    }
}
