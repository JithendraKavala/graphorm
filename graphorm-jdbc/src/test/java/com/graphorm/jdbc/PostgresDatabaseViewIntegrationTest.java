package com.graphorm.jdbc;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.DeleteBuilder;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end Testcontainers integration test suite querying a PostgreSQL physical view
 * with filters and pagination, and verifying strict read-only invariant enforcement (TASK-7.4 & Milestone 2).
 */
@DisplayName("PostgreSQL Database-Backed View Models Integration Tests & Milestone 2 (TASK-7.4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresDatabaseViewIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresDatabaseViewIntegrationTest.class);

    public record StudentSummaryRecord(
            Long id,
            String name,
            String departmentName,
            Integer totalCredits,
            String status
    ) {}

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;
    private static ViewModelMetadata studentSummaryView;

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
            log.info("Starting Testcontainers PostgreSQL 16 container for Phase 7 / Milestone 2 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_phase7")
                    .withUsername("phase7_user")
                    .withPassword("phase7_pass");
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

        // 1. Initialize schema, tables, and physical PostgreSQL view
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP VIEW IF EXISTS vw_student_summary CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS enrollments_m2 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS courses_m2 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS students_m2 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments_m2 CASCADE;");

            stmt.execute("""
                CREATE TABLE departments_m2 (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE
                );
            """);

            stmt.execute("""
                CREATE TABLE students_m2 (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    department_id BIGINT REFERENCES departments_m2(id),
                    status VARCHAR(50) NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE courses_m2 (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    credits INT NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE enrollments_m2 (
                    id BIGSERIAL PRIMARY KEY,
                    student_id BIGINT REFERENCES students_m2(id),
                    course_id BIGINT REFERENCES courses_m2(id)
                );
            """);

            // Seed Departments
            stmt.execute("INSERT INTO departments_m2 (id, name) VALUES (1, 'Computer Science'), (2, 'Mathematics');");

            // Seed Courses
            stmt.execute("""
                INSERT INTO courses_m2 (id, name, credits) VALUES
                (101, 'Algorithms', 4),
                (102, 'Operating Systems', 4),
                (103, 'Database Systems', 3),
                (104, 'Linear Algebra', 4),
                (105, 'Calculus III', 4),
                (106, 'Discrete Mathematics', 3);
            """);

            // Seed Students
            stmt.execute("""
                INSERT INTO students_m2 (id, name, email, department_id, status) VALUES
                (1, 'Ada Lovelace', 'ada@cs.university.edu', 1, 'ACTIVE'),
                (2, 'Alan Kay', 'alan.kay@cs.university.edu', 1, 'ACTIVE'),
                (3, 'Alice Turing', 'alice@cs.university.edu', 1, 'ACTIVE'),
                (4, 'Bob Hopper', 'bob@cs.university.edu', 1, 'INACTIVE'),
                (5, 'Carl Gauss', 'carl@math.university.edu', 2, 'ACTIVE'),
                (6, 'David Hilbert', 'david@math.university.edu', 2, 'SUSPENDED');
            """);

            // Seed Enrollments
            // Ada Lovelace (1): 101 (4) + 102 (4) + 103 (3) = 11 credits
            // Alan Kay (2): 101 (4) + 103 (3) = 7 credits
            // Alice Turing (3): 101 (4) + 102 (4) + 103 (3) + 106 (3) = 14 credits
            // Bob Hopper (4): 102 (4) = 4 credits
            // Carl Gauss (5): 104 (4) + 105 (4) + 106 (3) = 11 credits
            // David Hilbert (6): 0 enrollments = 0 credits
            stmt.execute("""
                INSERT INTO enrollments_m2 (student_id, course_id) VALUES
                (1, 101), (1, 102), (1, 103),
                (2, 101), (2, 103),
                (3, 101), (3, 102), (3, 103), (3, 106),
                (4, 102),
                (5, 104), (5, 105), (5, 106);
            """);

            // Create Milestone 2 deliverable physical view: vw_student_summary
            stmt.execute("""
                CREATE OR REPLACE VIEW vw_student_summary AS
                SELECT
                    s.id AS id,
                    s.name AS name,
                    d.name AS dept_name,
                    COALESCE(SUM(c.credits), 0)::INT AS total_credits,
                    s.status AS status
                FROM students_m2 s
                JOIN departments_m2 d ON s.department_id = d.id
                LEFT JOIN enrollments_m2 e ON s.id = e.student_id
                LEFT JOIN courses_m2 c ON e.course_id = c.id
                GROUP BY s.id, s.name, d.name, s.status;
            """);
        }

        // 2. Parse schema and retrieve ViewModelMetadata
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                view StudentSummary {
                    id             Int    @id
                    name           String
                    departmentName String @map("dept_name")
                    totalCredits   Int    @map("total_credits")
                    status         String

                    @@view
                    @@map("vw_student_summary")
                }
                """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        studentSummaryView = registry.getViewModel("StudentSummary");
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. MILESTONE 2: Query all records from PostgreSQL view vw_student_summary with column mapping")
    void shouldQueryAllRecordsFromDatabaseView() {
        assertThat(studentSummaryView.isDatabaseBacked()).isTrue();
        assertThat(studentSummaryView.viewName()).isEqualTo("vw_student_summary");
        assertThat(studentSummaryView.isReadOnly()).isTrue();

        SelectQuery query = SelectBuilder.forEntity(studentSummaryView)
                .orderByAsc("name")
                .build();

        List<StudentSummaryRecord> summaries = executor.queryForList(query, StudentSummaryRecord.class);

        assertThat(summaries).hasSize(6);

        // Verify column alias mappings (dept_name -> departmentName, total_credits -> totalCredits)
        StudentSummaryRecord alice = summaries.stream().filter(s -> "Alice Turing".equals(s.name())).findFirst().orElseThrow();
        assertThat(alice.departmentName()).isEqualTo("Computer Science");
        assertThat(alice.totalCredits()).isEqualTo(14);
        assertThat(alice.status()).isEqualTo("ACTIVE");

        StudentSummaryRecord ada = summaries.stream().filter(s -> "Ada Lovelace".equals(s.name())).findFirst().orElseThrow();
        assertThat(ada.departmentName()).isEqualTo("Computer Science");
        assertThat(ada.totalCredits()).isEqualTo(11);
        assertThat(ada.status()).isEqualTo("ACTIVE");

        StudentSummaryRecord carl = summaries.stream().filter(s -> "Carl Gauss".equals(s.name())).findFirst().orElseThrow();
        assertThat(carl.departmentName()).isEqualTo("Mathematics");
        assertThat(carl.totalCredits()).isEqualTo(11);
        assertThat(carl.status()).isEqualTo("ACTIVE");

        StudentSummaryRecord alan = summaries.stream().filter(s -> "Alan Kay".equals(s.name())).findFirst().orElseThrow();
        assertThat(alan.departmentName()).isEqualTo("Computer Science");
        assertThat(alan.totalCredits()).isEqualTo(7);

        StudentSummaryRecord bob = summaries.stream().filter(s -> "Bob Hopper".equals(s.name())).findFirst().orElseThrow();
        assertThat(bob.departmentName()).isEqualTo("Computer Science");
        assertThat(bob.totalCredits()).isEqualTo(4);
        assertThat(bob.status()).isEqualTo("INACTIVE");

        StudentSummaryRecord david = summaries.stream().filter(s -> "David Hilbert".equals(s.name())).findFirst().orElseThrow();
        assertThat(david.departmentName()).isEqualTo("Mathematics");
        assertThat(david.totalCredits()).isEqualTo(0);
        assertThat(david.status()).isEqualTo("SUSPENDED");
    }

    @Test
    @Order(2)
    @DisplayName("2. PUSHDOWN FILTERING: Comparison operators and logical AND pushdown on view")
    void shouldPushdownFilterWithComparisonOperators() {
        // Filter: total_credits >= 10 AND status = 'ACTIVE'
        SelectQuery query = SelectBuilder.forEntity(studentSummaryView)
                .where(WherePredicate.and(
                        WherePredicate.gte("total_credits", 10),
                        WherePredicate.eq("status", "ACTIVE")
                ))
                .orderByAsc("name")
                .build();

        List<StudentSummaryRecord> results = executor.queryForList(query, StudentSummaryRecord.class);

        // Expected: Ada Lovelace (11), Alice Turing (14), Carl Gauss (11)
        // Alan Kay (7 credits < 10) -> excluded
        // Bob Hopper (INACTIVE) -> excluded
        // David Hilbert (SUSPENDED) -> excluded
        assertThat(results).extracting(StudentSummaryRecord::name)
                .containsExactly("Ada Lovelace", "Alice Turing", "Carl Gauss");
    }

    @Test
    @Order(3)
    @DisplayName("3. STRING MATCHING: Filtering database view by department and prefix")
    void shouldPushdownFilterWithStringMatching() {
        // Filter: dept_name = 'Computer Science' AND name LIKE 'A%'
        SelectQuery query = SelectBuilder.forEntity(studentSummaryView)
                .where(WherePredicate.and(
                        WherePredicate.eq("dept_name", "Computer Science"),
                        WherePredicate.startsWith("name", "A")
                ))
                .orderByAsc("name")
                .build();

        List<StudentSummaryRecord> results = executor.queryForList(query, StudentSummaryRecord.class);

        // Ada Lovelace, Alan Kay, Alice Turing
        assertThat(results).extracting(StudentSummaryRecord::name)
                .containsExactly("Ada Lovelace", "Alan Kay", "Alice Turing");
    }

    @Test
    @Order(4)
    @DisplayName("4. PUSHDOWN PAGINATION: LIMIT & OFFSET over sorted view dataset")
    void shouldPushdownSortingAndPagination() {
        // Page size = 2, sorted by total_credits DESC, name ASC
        // Expected order:
        // Page 0: Alice Turing (14), Ada Lovelace (11)
        // Page 1: Carl Gauss (11), Alan Kay (7)
        // Page 2: Bob Hopper (4), David Hilbert (0)
        List<String> traversedNames = new ArrayList<>();

        for (int page = 0; page < 3; page++) {
            SelectQuery pageQuery = SelectBuilder.forEntity(studentSummaryView)
                    .orderBy(
                            OrderByClause.desc("total_credits"),
                            OrderByClause.asc("name")
                    )
                    .limit(2)
                    .offset(page * 2)
                    .build();

            List<StudentSummaryRecord> pageResults = executor.queryForList(pageQuery, StudentSummaryRecord.class);
            assertThat(pageResults).hasSize(2);
            pageResults.forEach(r -> traversedNames.add(r.name()));
        }

        assertThat(traversedNames).containsExactly(
                "Alice Turing",
                "Ada Lovelace",
                "Carl Gauss",
                "Alan Kay",
                "Bob Hopper",
                "David Hilbert"
        );
    }

    @Test
    @Order(5)
    @DisplayName("5. STRICT READ-ONLY INVARIANT: Reject write query builders targeting view metadata (Milestone 2)")
    void shouldEnforceReadOnlyInvariantOnWriteBuilders() {
        // 1. InsertBuilder rejection
        assertThatThrownBy(() -> InsertBuilder.forEntity(dialect, studentSummaryView))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform INSERT on read-only view 'StudentSummary'")
                .satisfies(e -> {
                    ReadOnlyViewException roe = (ReadOnlyViewException) e;
                    assertThat(roe.getEntityName()).isEqualTo("StudentSummary");
                    assertThat(roe.getOperation()).isEqualTo("INSERT");
                    assertThat(roe.getErrorCode()).isEqualTo("ERR_READ_ONLY_VIEW");
                });

        assertThatThrownBy(() -> InsertBuilder.of(dialect).into(SourceTable.view("vw_student_summary")))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform INSERT on read-only view 'vw_student_summary'");

        // 2. UpdateBuilder rejection
        assertThatThrownBy(() -> UpdateBuilder.forEntity(dialect, studentSummaryView))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform UPDATE on read-only view 'StudentSummary'")
                .satisfies(e -> {
                    ReadOnlyViewException roe = (ReadOnlyViewException) e;
                    assertThat(roe.getEntityName()).isEqualTo("StudentSummary");
                    assertThat(roe.getOperation()).isEqualTo("UPDATE");
                    assertThat(roe.getErrorCode()).isEqualTo("ERR_READ_ONLY_VIEW");
                });

        assertThatThrownBy(() -> UpdateBuilder.of(dialect).table(SourceTable.view("vw_student_summary")))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform UPDATE on read-only view 'vw_student_summary'");

        // 3. DeleteBuilder rejection
        assertThatThrownBy(() -> DeleteBuilder.forEntity(dialect, studentSummaryView))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform DELETE on read-only view 'StudentSummary'")
                .satisfies(e -> {
                    ReadOnlyViewException roe = (ReadOnlyViewException) e;
                    assertThat(roe.getEntityName()).isEqualTo("StudentSummary");
                    assertThat(roe.getOperation()).isEqualTo("DELETE");
                    assertThat(roe.getErrorCode()).isEqualTo("ERR_READ_ONLY_VIEW");
                });

        assertThatThrownBy(() -> DeleteBuilder.of(dialect).from(SourceTable.view("vw_student_summary")))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform DELETE on read-only view 'vw_student_summary'");

        // 4. QueryGuards factory rejection
        assertThatThrownBy(() -> QueryGuards.createInsert(studentSummaryView, Map.of("name", "Illegal")))
                .isInstanceOf(ReadOnlyViewException.class);

        assertThatThrownBy(() -> QueryGuards.createUpdate(studentSummaryView, Map.of("name", "Illegal"), WherePredicate.eq("id", 1)))
                .isInstanceOf(ReadOnlyViewException.class);

        assertThatThrownBy(() -> QueryGuards.createDelete(studentSummaryView, WherePredicate.eq("id", 1)))
                .isInstanceOf(ReadOnlyViewException.class);
    }

    @Test
    @Order(6)
    @DisplayName("6. TABLE ALIAS SUPPORT: Query view directly via SelectQuery.fromView with alias and filter")
    void shouldSupportQueryingWithTableAlias() {
        SelectQuery query = SelectQuery.fromView("vw_student_summary")
                .as("v")
                .column("v.id", "id")
                .column("v.name", "name")
                .column("v.dept_name", "departmentName")
                .column("v.total_credits", "totalCredits")
                .column("v.status", "status")
                .where(WherePredicate.lt("v.total_credits", 10))
                .orderByAsc("v.name")
                .build();

        List<StudentSummaryRecord> results = executor.queryForList(query, StudentSummaryRecord.class);

        // Expected students with total_credits < 10:
        // Alan Kay (7), Bob Hopper (4), David Hilbert (0)
        assertThat(results).extracting(StudentSummaryRecord::name)
                .containsExactly("Alan Kay", "Bob Hopper", "David Hilbert");
    }
}
