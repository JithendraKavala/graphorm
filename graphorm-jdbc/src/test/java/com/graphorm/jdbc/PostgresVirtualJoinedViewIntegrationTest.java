package com.graphorm.jdbc;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.DeleteBuilder;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers integration test suite verifying multi-table virtual joined views (TASK-8.5).
 * Validates dynamic construction of multi-table join trees, column alias resolution,
 * pushdown filtering, ordering, pagination, and read-only enforcement in real PostgreSQL.
 */
@DisplayName("PostgreSQL Virtual Joined Views Integration Tests (TASK-8.5)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresVirtualJoinedViewIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresVirtualJoinedViewIntegrationTest.class);

    public record StudentDashboardRecord(
            Long studentId,
            String studentName,
            String studentEmail,
            String departmentName,
            String courseName,
            Double gradePoints
    ) {}

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;
    private static ModelRegistry registry;
    private static ViewModelMetadata dashboardView;
    private static ViewModelMetadata enrolledDashboardView;

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
            log.info("Starting Testcontainers PostgreSQL 16 container for Phase 8 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_phase8")
                    .withUsername("phase8_user")
                    .withPassword("phase8_pass");
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

        // 1. Create physical PostgreSQL tables (NO physical views created!)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS enrollments_v8 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS students_v8 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments_v8 CASCADE;");

            stmt.execute("""
                CREATE TABLE departments_v8 (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    code VARCHAR(50) NOT NULL UNIQUE
                );
            """);

            stmt.execute("""
                CREATE TABLE students_v8 (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    department_id BIGINT REFERENCES departments_v8(id),
                    gpa DOUBLE PRECISION NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE enrollments_v8 (
                    id BIGSERIAL PRIMARY KEY,
                    student_id BIGINT REFERENCES students_v8(id),
                    course_name VARCHAR(255) NOT NULL,
                    grade_points DOUBLE PRECISION NOT NULL
                );
            """);

            // Seed Departments
            stmt.execute("INSERT INTO departments_v8 (id, name, code) VALUES " +
                    "(1, 'Computer Science', 'CS'), " +
                    "(2, 'Mathematics', 'MATH'), " +
                    "(3, 'Physics', 'PHYS');");

            // Seed Students
            stmt.execute("""
                INSERT INTO students_v8 (id, name, email, department_id, gpa) VALUES
                (1, 'Ada Lovelace', 'ada@cs.edu', 1, 4.0),
                (2, 'Alan Kay', 'alan@cs.edu', 1, 3.8),
                (3, 'Carl Gauss', 'carl@math.edu', 2, 3.9),
                (4, 'David Hilbert', 'david@math.edu', 2, 3.2),
                (5, 'Marie Curie', 'marie@phys.edu', 3, 3.95),
                (6, 'Solo Scholar', 'solo@independent.edu', NULL, 3.5);
            """);

            // Seed Enrollments
            stmt.execute("""
                INSERT INTO enrollments_v8 (student_id, course_name, grade_points) VALUES
                (1, 'Operating Systems', 4.0),
                (1, 'Algorithms', 4.0),
                (2, 'Computer Graphics', 3.8),
                (3, 'Number Theory', 4.0),
                (5, 'Quantum Mechanics', 3.9);
            """);
        }

        // 2. Parse Schema with GraphORM-defined virtual views (Zero DDL views in Postgres)
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                model Department {
                    id   Int    @id
                    name String
                    code String

                    @@map("departments_v8")
                }

                model Student {
                    id           Int     @id @default(autoincrement())
                    name         String
                    email        String  @unique
                    departmentId Int?    @map("department_id")
                    gpa          Float

                    @@map("students_v8")
                }

                model Enrollment {
                    id          Int    @id @default(autoincrement())
                    studentId   Int    @map("student_id")
                    courseName  String @map("course_name")
                    gradePoints Float  @map("grade_points")

                    @@map("enrollments_v8")
                }

                view StudentDashboard {
                    studentId      Int     @map("s.id")
                    studentName    String  @map("s.name")
                    studentEmail   String  @map("s.email")
                    departmentName String? @map("d.name")
                    courseName     String? @map("e.course_name")
                    gradePoints    Float?  @map("e.grade_points")

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                }

                view EnrolledStudentDashboard {
                    studentId      Int    @map("s.id")
                    studentName    String @map("s.name")
                    departmentName String @map("d.name")
                    courseName     String @map("e.course_name")
                    gradePoints    Float  @map("e.grade_points")

                    @@from(Student, as: "s")
                    @@innerJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@innerJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                }
                """;

        registry = SchemaToMetadataTransformer.transform(schema);
        dashboardView = registry.getViewModel("StudentDashboard");
        enrolledDashboardView = registry.getViewModel("EnrolledStudentDashboard");
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. MULTI-TABLE JOIN: Query 3-table virtual joined view (StudentDashboard) with LEFT JOINs")
    void shouldQueryMultiTableVirtualJoinedView() {
        assertThat(dashboardView.isDatabaseBacked()).isFalse();
        assertThat(dashboardView.sourceModelName()).isEqualTo("Student");
        assertThat(dashboardView.joinDefinitions()).hasSize(2);

        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry)
                .orderByAsc("studentName")
                .build();

        List<StudentDashboardRecord> records = executor.queryForList(query, StudentDashboardRecord.class);

        // Verify total records returned (Ada Lovelace: 2, Alan Kay: 1, Carl Gauss: 1, David Hilbert: 1, Marie Curie: 1, Solo Scholar: 1 = 7)
        assertThat(records).hasSize(7);

        // Verify Ada Lovelace (has CS department, 2 courses)
        List<StudentDashboardRecord> adaRows = records.stream()
                .filter(r -> "Ada Lovelace".equals(r.studentName()))
                .toList();
        assertThat(adaRows).hasSize(2);
        assertThat(adaRows).allMatch(r -> "Computer Science".equals(r.departmentName()));
        assertThat(adaRows).extracting(StudentDashboardRecord::courseName)
                .containsExactlyInAnyOrder("Operating Systems", "Algorithms");

        // Verify Solo Scholar (has no department -> null departmentName, no courses -> null courseName)
        StudentDashboardRecord solo = records.stream()
                .filter(r -> "Solo Scholar".equals(r.studentName()))
                .findFirst().orElseThrow();
        assertThat(solo.departmentName()).isNull();
        assertThat(solo.courseName()).isNull();
        assertThat(solo.gradePoints()).isNull();

        // Verify David Hilbert (has Math department, but no courses -> null courseName)
        StudentDashboardRecord david = records.stream()
                .filter(r -> "David Hilbert".equals(r.studentName()))
                .findFirst().orElseThrow();
        assertThat(david.departmentName()).isEqualTo("Mathematics");
        assertThat(david.courseName()).isNull();
    }

    @Test
    @Order(2)
    @DisplayName("2. PUSHDOWN FILTERING: Filter view using view field aliases mapped to source table columns")
    void shouldFilterWithPushdownFieldAliases() {
        // Filter by view field names: departmentName = 'Computer Science' AND gradePoints >= 4.0
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry)
                .where(WherePredicate.and(
                        WherePredicate.eq("departmentName", "Computer Science"),
                        WherePredicate.gte("gradePoints", 4.0)
                ))
                .orderByAsc("courseName")
                .build();

        List<StudentDashboardRecord> results = executor.queryForList(query, StudentDashboardRecord.class);

        // Ada Lovelace has 2 courses with grade 4.0 in Computer Science
        assertThat(results).hasSize(2);
        assertThat(results).extracting(StudentDashboardRecord::studentName)
                .containsOnly("Ada Lovelace");
        assertThat(results).extracting(StudentDashboardRecord::courseName)
                .containsExactly("Algorithms", "Operating Systems");
    }

    @Test
    @Order(3)
    @DisplayName("3. PAGINATION & SORTING: Pushdown ordering and LIMIT/OFFSET on virtual joined view")
    void shouldPaginateVirtualViewQuery() {
        // Page 1 (limit 3, offset 0) sorted by studentName ASC, courseName ASC
        SelectQuery page1Query = ViewQueryPlanner.forView(dashboardView, registry)
                .orderByAsc("studentName")
                .limit(3)
                .offset(0)
                .build();

        List<StudentDashboardRecord> page1 = executor.queryForList(page1Query, StudentDashboardRecord.class);
        assertThat(page1).hasSize(3);

        // Page 2 (limit 3, offset 3)
        SelectQuery page2Query = ViewQueryPlanner.forView(dashboardView, registry)
                .orderByAsc("studentName")
                .limit(3)
                .offset(3)
                .build();

        List<StudentDashboardRecord> page2 = executor.queryForList(page2Query, StudentDashboardRecord.class);
        assertThat(page2).hasSize(3);

        // Ensure distinct items across pages
        assertThat(page1).doesNotContainAnyElementsOf(page2);
    }

    @Test
    @Order(4)
    @DisplayName("4. INNER JOINS: Query virtual view with @@innerJoin filtering un-enrolled students")
    void shouldQueryInnerJoinedVirtualView() {
        assertThat(enrolledDashboardView.joinDefinitions()).allMatch(j -> j.joinType() == com.graphorm.core.query.JoinType.INNER);

        SelectQuery query = ViewQueryPlanner.forView(enrolledDashboardView, registry)
                .orderByAsc("studentName")
                .build();

        List<StudentDashboardRecord> results = executor.queryForList(query, StudentDashboardRecord.class);

        // Only students with BOTH department and enrollment (Ada: 2, Alan: 1, Carl: 1, Marie: 1 = 5)
        // David Hilbert (no enrollments) & Solo Scholar (no department and no enrollments) must be omitted
        assertThat(results).hasSize(5);
        assertThat(results).extracting(StudentDashboardRecord::studentName)
                .doesNotContain("David Hilbert", "Solo Scholar");
    }

    @Test
    @Order(5)
    @DisplayName("5. READ-ONLY ENFORCEMENT: Write query builders reject virtual joined view models")
    void shouldEnforceReadOnlyInvariantOnVirtualViews() {
        assertThat(dashboardView.isReadOnly()).isTrue();
        assertThat(enrolledDashboardView.isReadOnly()).isTrue();

        assertThatThrownBy(() -> QueryGuards.assertWritable(dashboardView, "INSERT"))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform INSERT on read-only view 'StudentDashboard'");

        assertThatThrownBy(() -> InsertBuilder.forEntity(dialect, dashboardView))
                .isInstanceOf(ReadOnlyViewException.class);

        assertThatThrownBy(() -> UpdateBuilder.forEntity(dialect, dashboardView))
                .isInstanceOf(ReadOnlyViewException.class);

        assertThatThrownBy(() -> DeleteBuilder.forEntity(dialect, dashboardView))
                .isInstanceOf(ReadOnlyViewException.class);
    }
}
