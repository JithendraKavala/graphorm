package com.graphorm.example.university;

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
 *     <li>Transaction commit and rollback semantics</li>
 *     <li>Read-only physical models (@@readonly) and mutation prohibition</li>
 *     <li>Model and view querying for audit entities</li>
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
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema.sql");
        registry.add("spring.sql.init.data-locations", () -> "classpath:data.sql");
        registry.add("graphorm.schema-location", () -> "classpath:schema.graphorm");
    }

    @Autowired
    private GraphOrmClient db;

    @Autowired
    private TransactionManager transactionManager;

    @Test
    @Order(1)
    @DisplayName("Find all students using generated client")
    void testFindAllStudents() {
        List<Student> students = db.student().findMany();
        assertThat(students).hasSize(6);
    }

    @Test
    @Order(2)
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
    @Order(3)
    @DisplayName("Find unique student by ID")
    void testFindStudentById() {
        Optional<Student> studentOpt = db.student().findById(1);
        assertThat(studentOpt).isPresent();
        Student student = studentOpt.get();
        assertThat(student.name()).isEqualTo("Grace Hopper");
        assertThat(student.email()).isEqualTo("hopper@student.edu");
    }

    @Test
    @Order(4)
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
    @Order(5)
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
    @Order(6)
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
    @Order(7)
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
    @Order(8)
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
    @Order(9)
    @DisplayName("Query audit logs and audit summary view via generated client")
    void testAuditLogAndSummaryClient() {
        List<AuditLog> logs = db.auditLog().findMany();
        assertThat(logs).hasSizeGreaterThanOrEqualTo(2);

        List<AuditSummary> summaries = db.auditSummary().findMany();
        assertThat(summaries).hasSizeGreaterThanOrEqualTo(2);
        assertThat(summaries).extracting(AuditSummary::performedBy)
                .contains("system");
    }
}
