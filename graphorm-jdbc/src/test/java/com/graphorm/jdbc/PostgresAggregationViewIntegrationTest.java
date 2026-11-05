package com.graphorm.jdbc;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.HavingPredicate;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.DeleteBuilder;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.util.SqlStatementCounter;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Testcontainers integration test suite verifying Stage 4 View Aggregations, Computed Fields,
 * GROUP BY, HAVING, and Join Pruning against a real PostgreSQL database (TASK-9.4 & Milestone 3).
 *
 * <p>Validates Milestone 3 deliverable:
 * Single SQL query executed for aggregated dashboard view, returning exact calculated values.
 */
@DisplayName("PostgreSQL View Aggregations & GROUP BY Integration Tests (TASK-9.4 / Milestone 3)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresAggregationViewIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresAggregationViewIntegrationTest.class);

    public record StudentDashboardSummary(
            Long studentId,
            String studentName,
            String departmentName,
            Long courseCount,
            Double avgGpa
    ) {}

    public record StudentDepartmentSummary(
            String studentName,
            String departmentName
    ) {}

    private static PostgreSQLContainer<?> container;
    private static DataSource rawDataSource;
    private static SqlStatementCounter statementCounter;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;
    private static ModelRegistry registry;
    private static ViewModelMetadata dashboardView;

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
            log.info("Starting Testcontainers PostgreSQL 16 container for Phase 9 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_phase9")
                    .withUsername("phase9_user")
                    .withPassword("phase9_pass");
            container.start();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(container.getJdbcUrl());
            ds.setUser(container.getUsername());
            ds.setPassword(container.getPassword());
            rawDataSource = ds;
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432...");
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerNames(new String[]{"localhost"});
            ds.setPortNumbers(new int[]{5432});
            ds.setUser("postgres");
            ds.setPassword("");
            ds.setDatabaseName("postgres");
            rawDataSource = ds;
        }

        // Wrap DataSource with SqlStatementCounter to track exact statement executions
        statementCounter = new SqlStatementCounter();
        DataSource countingDataSource = statementCounter.wrap(rawDataSource);
        executor = new JdbcExecutor(countingDataSource, dialect);

        // 1. Create physical PostgreSQL tables (Zero DDL views in Postgres)
        try (Connection conn = rawDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS enrollments_v9 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS students_v9 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments_v9 CASCADE;");

            stmt.execute("""
                CREATE TABLE departments_v9 (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    code VARCHAR(50) NOT NULL UNIQUE
                );
            """);

            stmt.execute("""
                CREATE TABLE students_v9 (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    department_id BIGINT REFERENCES departments_v9(id)
                );
            """);

            stmt.execute("""
                CREATE TABLE enrollments_v9 (
                    id BIGSERIAL PRIMARY KEY,
                    student_id BIGINT REFERENCES students_v9(id),
                    course_name VARCHAR(255) NOT NULL,
                    grade_points DOUBLE PRECISION NOT NULL
                );
            """);

            // Seed Departments
            stmt.execute("INSERT INTO departments_v9 (id, name, code) VALUES " +
                    "(1, 'Computer Science', 'CS'), " +
                    "(2, 'Mathematics', 'MATH'), " +
                    "(3, 'Physics', 'PHYS');");

            // Seed Students
            stmt.execute("""
                INSERT INTO students_v9 (id, name, email, department_id) VALUES
                (1, 'Ada Lovelace', 'ada@cs.edu', 1),
                (2, 'Alan Kay', 'alan@cs.edu', 1),
                (3, 'Carl Gauss', 'carl@math.edu', 2),
                (4, 'David Hilbert', 'david@math.edu', 2),
                (5, 'Marie Curie', 'marie@phys.edu', 3),
                (6, 'Solo Scholar', 'solo@independent.edu', NULL);
            """);

            // Seed Enrollments
            // Ada: 3 courses, grade_points: 4.0, 4.0, 3.7 -> avg = 3.9
            // Alan: 2 courses, grade_points: 3.8, 3.6 -> avg = 3.7
            // Carl: 1 course, grade_points: 4.0 -> avg = 4.0
            // Marie: 2 courses, grade_points: 3.9, 3.7 -> avg = 3.8
            // David: 0 courses
            // Solo: 0 courses
            stmt.execute("""
                INSERT INTO enrollments_v9 (student_id, course_name, grade_points) VALUES
                (1, 'Operating Systems', 4.0),
                (1, 'Algorithms', 4.0),
                (1, 'Distributed Systems', 3.7),
                (2, 'Computer Graphics', 3.8),
                (2, 'Compilers', 3.6),
                (3, 'Number Theory', 4.0),
                (5, 'Quantum Mechanics', 3.9),
                (5, 'Thermodynamics', 3.7);
            """);
        }

        // 2. Parse Schema with GraphORM-defined virtual view having aggregations, expressions & groupBy
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                model Department {
                    id   Int    @id
                    name String
                    code String

                    @@map("departments_v9")
                }

                model Student {
                    id           Int     @id @default(autoincrement())
                    name         String
                    email        String  @unique
                    departmentId Int?    @map("department_id")

                    @@map("students_v9")
                }

                model Enrollment {
                    id          Int    @id @default(autoincrement())
                    studentId   Int    @map("student_id")
                    courseName  String @map("course_name")
                    gradePoints Float  @map("grade_points")

                    @@map("enrollments_v9")
                }

                view StudentDashboard {
                    studentId      Int     @map("s.id")
                    studentName    String  @map("s.name")
                    departmentName String? @map("d.name")
                    courseCount    Int     @aggregate(count: "e.id")
                    avgGpa         Float?  @expression("AVG(e.grade_points)")

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                    @@groupBy(["s.id", "s.name", "d.name"])
                }
                """;

        registry = SchemaToMetadataTransformer.transform(schema);
        dashboardView = registry.getViewModel("StudentDashboard");
    }

    @BeforeEach
    void resetStatementCounter() {
        statementCounter.reset();
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("TASK-9.4 / MILESTONE 3: Query StudentDashboard with COUNT and AVG executed in EXACTLY 1 SQL query")
    void shouldExecuteMilestone3AggregatedDashboardViewInSingleQuery() {
        assertThat(dashboardView.isDatabaseBacked()).isFalse();
        assertThat(dashboardView.groupByColumns()).containsExactly("s.id", "s.name", "d.name");

        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry)
                .orderByAsc("studentId")
                .build();

        List<StudentDashboardSummary> results = executor.queryForList(query, StudentDashboardSummary.class);

        // MILESTONE 3 VERIFICATION: Exactly 1 SQL query executed!
        statementCounter.assertTotalCount(1);
        statementCounter.assertSelectCount(1);

        // Verify total records returned (all 6 students)
        assertThat(results).hasSize(6);

        // 1. Ada Lovelace: 3 courses, avg = 3.9
        StudentDashboardSummary ada = results.stream().filter(r -> r.studentId() == 1L).findFirst().orElseThrow();
        assertThat(ada.studentName()).isEqualTo("Ada Lovelace");
        assertThat(ada.departmentName()).isEqualTo("Computer Science");
        assertThat(ada.courseCount()).isEqualTo(3L);
        assertThat(ada.avgGpa()).isCloseTo(3.9, within(0.001));

        // 2. Alan Kay: 2 courses, avg = 3.7
        StudentDashboardSummary alan = results.stream().filter(r -> r.studentId() == 2L).findFirst().orElseThrow();
        assertThat(alan.studentName()).isEqualTo("Alan Kay");
        assertThat(alan.departmentName()).isEqualTo("Computer Science");
        assertThat(alan.courseCount()).isEqualTo(2L);
        assertThat(alan.avgGpa()).isCloseTo(3.7, within(0.001));

        // 3. Carl Gauss: 1 course, avg = 4.0
        StudentDashboardSummary carl = results.stream().filter(r -> r.studentId() == 3L).findFirst().orElseThrow();
        assertThat(carl.studentName()).isEqualTo("Carl Gauss");
        assertThat(carl.departmentName()).isEqualTo("Mathematics");
        assertThat(carl.courseCount()).isEqualTo(1L);
        assertThat(carl.avgGpa()).isCloseTo(4.0, within(0.001));

        // 4. David Hilbert: 0 courses, avg = null
        StudentDashboardSummary david = results.stream().filter(r -> r.studentId() == 4L).findFirst().orElseThrow();
        assertThat(david.studentName()).isEqualTo("David Hilbert");
        assertThat(david.departmentName()).isEqualTo("Mathematics");
        assertThat(david.courseCount()).isEqualTo(0L);
        assertThat(david.avgGpa()).isNull();

        // 5. Marie Curie: 2 courses, avg = 3.8
        StudentDashboardSummary marie = results.stream().filter(r -> r.studentId() == 5L).findFirst().orElseThrow();
        assertThat(marie.studentName()).isEqualTo("Marie Curie");
        assertThat(marie.departmentName()).isEqualTo("Physics");
        assertThat(marie.courseCount()).isEqualTo(2L);
        assertThat(marie.avgGpa()).isCloseTo(3.8, within(0.001));

        // 6. Solo Scholar: 0 courses, avg = null, department = null
        StudentDashboardSummary solo = results.stream().filter(r -> r.studentId() == 6L).findFirst().orElseThrow();
        assertThat(solo.studentName()).isEqualTo("Solo Scholar");
        assertThat(solo.departmentName()).isNull();
        assertThat(solo.courseCount()).isEqualTo(0L);
        assertThat(solo.avgGpa()).isNull();
    }

    @Test
    @Order(2)
    @DisplayName("TASK-9.4: Pushdown filtering on source table columns with aggregation in single query")
    void shouldFilterWithPushdownConditionsInSingleQuery() {
        // Filter by view field: departmentName = 'Computer Science'
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry)
                .where(WherePredicate.eq("departmentName", "Computer Science"))
                .orderByDesc("courseCount")
                .build();

        List<StudentDashboardSummary> results = executor.queryForList(query, StudentDashboardSummary.class);

        // Exactly 1 SQL query executed
        statementCounter.assertTotalCount(1);
        statementCounter.assertSelectCount(1);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).studentName()).isEqualTo("Ada Lovelace");
        assertThat(results.get(0).courseCount()).isEqualTo(3L);
        assertThat(results.get(1).studentName()).isEqualTo("Alan Kay");
        assertThat(results.get(1).courseCount()).isEqualTo(2L);
    }

    @Test
    @Order(3)
    @DisplayName("TASK-9.4: HAVING filter on aggregated field (courseCount >= 2) in single query")
    void shouldFilterWithHavingClauseInSingleQuery() {
        // Filter using HAVING: courseCount >= 2
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry)
                .having(HavingPredicate.gte("courseCount", 2))
                .orderBy(OrderByClause.desc("courseCount"), OrderByClause.asc("studentName"))
                .build();

        List<StudentDashboardSummary> results = executor.queryForList(query, StudentDashboardSummary.class);

        // Exactly 1 SQL query executed
        statementCounter.assertTotalCount(1);
        statementCounter.assertSelectCount(1);

        // Only students with 2 or more courses: Ada (3), Alan (2), Marie (2)
        // Gauss (1), Hilbert (0), Solo (0) are excluded
        assertThat(results).hasSize(3);
        assertThat(results).extracting(StudentDashboardSummary::studentName)
                .containsExactly("Ada Lovelace", "Alan Kay", "Marie Curie");
        assertThat(results).extracting(StudentDashboardSummary::courseCount)
                .containsExactly(3L, 2L, 2L);
    }

    @Test
    @Order(4)
    @DisplayName("TASK-9.4: Automatic routing of aggregate condition in .where() to HAVING clause")
    void shouldRouteAggregateWhereConditionToHavingInSingleQuery() {
        // Passing an aggregate condition to .where() should be automatically routed to HAVING
        SelectQuery query = ViewQueryPlanner.forView(dashboardView, registry)
                .where(WherePredicate.and(
                        WherePredicate.eq("departmentName", "Computer Science"),
                        WherePredicate.gt("courseCount", 2)
                ))
                .build();

        List<StudentDashboardSummary> results = executor.queryForList(query, StudentDashboardSummary.class);

        statementCounter.assertTotalCount(1);
        statementCounter.assertSelectCount(1);

        // In CS department, only Ada Lovelace has courseCount > 2
        assertThat(results).hasSize(1);
        assertThat(results.get(0).studentName()).isEqualTo("Ada Lovelace");
        assertThat(results.get(0).courseCount()).isEqualTo(3L);
    }

    @Test
    @Order(5)
    @DisplayName("TASK-9.4: Pagination and sorting on aggregated computed view")
    void shouldPaginateAggregatedView() {
        // Page 1 (limit 2, offset 0), sorted by courseCount DESC, studentName ASC
        SelectQuery page1Query = ViewQueryPlanner.forView(dashboardView, registry)
                .orderBy(OrderByClause.desc("courseCount"), OrderByClause.asc("studentName"))
                .limit(2)
                .offset(0)
                .build();

        List<StudentDashboardSummary> page1 = executor.queryForList(page1Query, StudentDashboardSummary.class);
        statementCounter.assertTotalCount(1);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).studentName()).isEqualTo("Ada Lovelace"); // 3 courses
        assertThat(page1.get(1).studentName()).isEqualTo("Alan Kay");     // 2 courses

        // Page 2 (limit 2, offset 2)
        statementCounter.reset();
        SelectQuery page2Query = ViewQueryPlanner.forView(dashboardView, registry)
                .orderBy(OrderByClause.desc("courseCount"), OrderByClause.asc("studentName"))
                .limit(2)
                .offset(2)
                .build();

        List<StudentDashboardSummary> page2 = executor.queryForList(page2Query, StudentDashboardSummary.class);
        statementCounter.assertTotalCount(1);
        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).studentName()).isEqualTo("Marie Curie"); // 2 courses
        assertThat(page2.get(1).studentName()).isEqualTo("Carl Gauss");  // 1 course
    }

    @Test
    @Order(6)
    @DisplayName("TASK-9.4: Join Pruning Optimization omits unselected table joins and GROUP BY in real Postgres")
    void shouldPruneUnselectedJoinsAndGroupBy() {
        // Select only studentName and departmentName -> enrollment table and GROUP BY must be pruned!
        SelectQuery prunedQuery = ViewQueryPlanner.forView(dashboardView, registry)
                .select("studentName", "departmentName")
                .orderByAsc("studentName")
                .build();

        // Verify the planned query does NOT have GROUP BY and does NOT join enrollments_v9
        assertThat(prunedQuery.hasGroupBy()).isFalse();
        assertThat(prunedQuery.joins()).hasSize(1); // Only Department join retained
        assertThat(prunedQuery.joins().get(0).table().identifier()).isEqualTo("departments_v9");

        List<StudentDepartmentSummary> records = executor.queryForList(prunedQuery, StudentDepartmentSummary.class);

        // Exactly 1 SQL statement executed
        statementCounter.assertTotalCount(1);
        statementCounter.assertSelectCount(1);

        // All 6 students returned cleanly without duplicates from enrollments
        assertThat(records).hasSize(6);
        assertThat(records).extracting(StudentDepartmentSummary::studentName)
                .containsExactly(
                        "Ada Lovelace",
                        "Alan Kay",
                        "Carl Gauss",
                        "David Hilbert",
                        "Marie Curie",
                        "Solo Scholar"
                );
    }

    @Test
    @Order(7)
    @DisplayName("TASK-9.4: Read-only invariant enforcement on aggregated view")
    void shouldEnforceReadOnlyInvariantOnAggregatedView() {
        assertThat(dashboardView.isReadOnly()).isTrue();

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
