package com.graphorm.jdbc;

import com.graphorm.core.query.NullsOrder;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
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

/**
 * End-to-end Testcontainers integration test suite asserting multi-condition queries,
 * comparison operators, string matching, multi-column sorting, nulls positioning,
 * and pushdown pagination against real PostgreSQL (TASK-5.4).
 */
@DisplayName("PostgreSQL Filter, Sort & Pagination Pushdown Integration Tests (TASK-5.4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresFilterPaginationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresFilterPaginationIntegrationTest.class);

    public record StudentRecord(
            Long id,
            String name,
            String email,
            Double gpa,
            Long departmentId,
            Integer age,
            String status
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
            log.info("Starting Testcontainers PostgreSQL 16 container for Phase 5 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_phase5")
                    .withUsername("phase5_user")
                    .withPassword("phase5_pass");
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

        // Initialize schema and seed data
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS students_p5 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments_p5 CASCADE;");

            stmt.execute("""
                CREATE TABLE departments_p5 (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE
                );
            """);

            stmt.execute("""
                CREATE TABLE students_p5 (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    gpa DOUBLE PRECISION,
                    department_id BIGINT REFERENCES departments_p5(id),
                    age INT NOT NULL,
                    status VARCHAR(50) NOT NULL
                );
            """);

            stmt.execute("INSERT INTO departments_p5 (id, name) VALUES (1, 'Computer Science'), (2, 'Mathematics');");

            // 10 diverse students with varying GPA (some NULL), age, status, department
            stmt.execute("""
                INSERT INTO students_p5 (name, email, gpa, department_id, age, status) VALUES
                ('Alice Turing', 'alice@cs.university.edu', 3.95, 1, 22, 'ACTIVE'),
                ('Alan Kay', 'alan.kay@cs.university.edu', 3.80, 1, 24, 'ACTIVE'),
                ('Ada Lovelace', 'ada@cs.university.edu', 4.00, 1, 20, 'ACTIVE'),
                ('Bob Hopper', 'bob@cs.university.edu', 3.20, 1, 19, 'INACTIVE'),
                ('Bernhard Riemann', 'bernhard@math.university.edu', 3.90, 2, 25, 'ACTIVE'),
                ('Carl Gauss', 'carl@math.university.edu', 4.00, 2, 23, 'ACTIVE'),
                ('Charles Babbage', 'charles@cs.university.edu', 2.80, 1, 17, 'SUSPENDED'),
                ('David Hilbert', 'david@math.university.edu', NULL, 2, 26, 'ACTIVE'),
                ('Emmy Noether', 'emmy@math.university.edu', 3.98, 2, 21, 'ACTIVE'),
                ('Euclid Alexandria', 'euclid@math.university.edu', NULL, 2, 30, 'INACTIVE');
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
    @DisplayName("1. ACCEPTANCE CRITERIA: age >= 18 AND (name LIKE 'A%' OR status = 'ACTIVE')")
    void shouldExecuteAcceptanceCriteriaFilter() {
        SelectQuery query = SelectQuery.from("students_p5")
                .column("id")
                .column("name")
                .column("email")
                .column("gpa")
                .column("department_id", "departmentId")
                .column("age")
                .column("status")
                .where(WherePredicate.and(
                        WherePredicate.gte("age", 18),
                        WherePredicate.or(
                                WherePredicate.like("name", "A%"),
                                WherePredicate.eq("status", "ACTIVE")
                        )
                ))
                .orderByAsc("name")
                .build();

        List<StudentRecord> students = executor.queryForList(query, StudentRecord.class);

        // Expected students (age >= 18 AND (starts with A OR active)):
        // Ada Lovelace (age 20, starts with A, ACTIVE)
        // Alan Kay (age 24, starts with A, ACTIVE)
        // Alice Turing (age 22, starts with A, ACTIVE)
        // Bernhard Riemann (age 25, ACTIVE)
        // Carl Gauss (age 23, ACTIVE)
        // David Hilbert (age 26, ACTIVE)
        // Emmy Noether (age 21, ACTIVE)
        // Bob Hopper: age 19, but INACTIVE and starts with B -> excluded
        // Charles Babbage: age 17 -> excluded
        // Euclid Alexandria: age 30, INACTIVE and starts with E -> excluded
        assertThat(students).extracting(StudentRecord::name)
                .containsExactly(
                        "Ada Lovelace",
                        "Alan Kay",
                        "Alice Turing",
                        "Bernhard Riemann",
                        "Carl Gauss",
                        "David Hilbert",
                        "Emmy Noether"
                );
    }

    @Test
    @Order(2)
    @DisplayName("2. RELATIONAL COMPARISON OPERATORS: eq, neq, gt, gte, lt, lte, between, notBetween")
    void shouldExecuteRelationalComparisonOperators() {
        // eq: exact department
        SelectQuery qEq = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.eq("department_id", 1L))
                .build();
        List<Map<String, Object>> rEq = executor.queryForList(qEq);
        assertThat(rEq).hasSize(5);

        // neq: not Computer Science
        SelectQuery qNeq = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.neq("department_id", 1L))
                .build();
        List<Map<String, Object>> rNeq = executor.queryForList(qNeq);
        assertThat(rNeq).hasSize(5);

        // gt & lt: age between 20 and 25 exclusive
        SelectQuery qGtLt = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.and(
                        WherePredicate.gt("age", 20),
                        WherePredicate.lt("age", 25)
                ))
                .orderByAsc("name")
                .build();
        List<StudentRecord> rGtLt = executor.queryForList(qGtLt, StudentRecord.class);
        assertThat(rGtLt).extracting(StudentRecord::name)
                .containsExactly("Alan Kay", "Alice Turing", "Carl Gauss", "Emmy Noether");

        // between: gpa between 3.90 and 4.00 inclusive
        SelectQuery qBetween = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.between("gpa", 3.90, 4.00))
                .orderByAsc("name")
                .build();
        List<StudentRecord> rBetween = executor.queryForList(qBetween, StudentRecord.class);
        assertThat(rBetween).extracting(StudentRecord::name)
                .containsExactly("Ada Lovelace", "Alice Turing", "Bernhard Riemann", "Carl Gauss", "Emmy Noether");

        // notBetween: age not between 18 and 25 inclusive
        SelectQuery qNotBetween = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.notBetween("age", 18, 25))
                .orderByAsc("name")
                .build();
        List<StudentRecord> rNotBetween = executor.queryForList(qNotBetween, StudentRecord.class);
        assertThat(rNotBetween).extracting(StudentRecord::name)
                .containsExactly("Charles Babbage", "David Hilbert", "Euclid Alexandria");
    }

    @Test
    @Order(3)
    @DisplayName("3. STRING MATCHING: like, ilike, contains, startsWith, endsWith, iContains")
    void shouldExecuteStringMatchingOperators() {
        // like: case-sensitive match
        SelectQuery qLike = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.like("email", "%@math.university.edu"))
                .build();
        assertThat(executor.queryForList(qLike)).hasSize(5);

        // ilike: case-insensitive match
        SelectQuery qIlike = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.ilike("name", "alice%"))
                .build();
        assertThat(executor.queryForList(qIlike, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("Alice Turing");

        // contains: substring anywhere
        SelectQuery qContains = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.contains("email", "kay"))
                .build();
        assertThat(executor.queryForList(qContains, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("Alan Kay");

        // startsWith: prefix match
        SelectQuery qStarts = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.startsWith("name", "Carl"))
                .build();
        assertThat(executor.queryForList(qStarts, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("Carl Gauss");

        // endsWith: suffix match
        SelectQuery qEnds = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.endsWith("name", "Noether"))
                .build();
        assertThat(executor.queryForList(qEnds, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("Emmy Noether");

        // iContains: case-insensitive substring
        SelectQuery qIContains = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.iContains("name", "TUR"))
                .build();
        assertThat(executor.queryForList(qIContains, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("Alice Turing");
    }

    @Test
    @Order(4)
    @DisplayName("4. NULL CHECKING: isNull, isNotNull, and eq(col, null)")
    void shouldExecuteNullCheckingOperators() {
        // isNull: students with unassigned/null GPA
        SelectQuery qNull = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.isNull("gpa"))
                .orderByAsc("name")
                .build();
        assertThat(executor.queryForList(qNull, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("David Hilbert", "Euclid Alexandria");

        // eq with null should also render IS NULL
        SelectQuery qEqNull = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.eq("gpa", null))
                .orderByAsc("name")
                .build();
        assertThat(executor.queryForList(qEqNull, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("David Hilbert", "Euclid Alexandria");

        // isNotNull: students with recorded GPA
        SelectQuery qNotNull = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.isNotNull("gpa"))
                .build();
        assertThat(executor.queryForList(qNotNull)).hasSize(8);
    }

    @Test
    @Order(5)
    @DisplayName("5. IN & NOT IN: collections, empty collections safe handling")
    void shouldExecuteInAndNotInOperators() {
        // in with multiple values
        SelectQuery qIn = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.in("status", "INACTIVE", "SUSPENDED"))
                .orderByAsc("name")
                .build();
        assertThat(executor.queryForList(qIn, StudentRecord.class))
                .extracting(StudentRecord::name)
                .containsExactly("Bob Hopper", "Charles Babbage", "Euclid Alexandria");

        // notIn with values
        SelectQuery qNotIn = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.notIn("status", List.of("INACTIVE", "SUSPENDED")))
                .build();
        assertThat(executor.queryForList(qNotIn)).hasSize(7);

        // Empty IN yields 1 = 0 (matches 0 rows without error)
        SelectQuery qEmptyIn = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.in("status", List.of()))
                .build();
        assertThat(executor.queryForList(qEmptyIn)).isEmpty();

        // Empty NOT IN yields 1 = 1 (matches all rows without error)
        SelectQuery qEmptyNotIn = SelectQuery.from("students_p5")
                .column("name")
                .where(WherePredicate.notIn("status", List.of()))
                .build();
        assertThat(executor.queryForList(qEmptyNotIn)).hasSize(10);
    }

    @Test
    @Order(6)
    @DisplayName("6. NULLS POSITIONING: NULLS FIRST vs NULLS LAST in PostgreSQL ORDER BY")
    void shouldExecuteNullsPositioning() {
        // gpa ASC NULLS FIRST: null GPAs appear first
        SelectQuery qNullsFirst = SelectQuery.from("students_p5")
                .column("name")
                .column("gpa")
                .orderBy(OrderByClause.ascNullsFirst("gpa"))
                .build();

        List<StudentRecord> rNullsFirst = executor.queryForList(qNullsFirst, StudentRecord.class);
        assertThat(rNullsFirst).hasSize(10);
        // The first 2 students must have null GPA
        assertThat(rNullsFirst.get(0).gpa()).isNull();
        assertThat(rNullsFirst.get(1).gpa()).isNull();
        assertThat(rNullsFirst.get(2).gpa()).isNotNull();

        // gpa ASC NULLS LAST: null GPAs appear last
        SelectQuery qNullsLast = SelectQuery.from("students_p5")
                .column("name")
                .column("gpa")
                .orderBy(OrderByClause.ascNullsLast("gpa"))
                .build();

        List<StudentRecord> rNullsLast = executor.queryForList(qNullsLast, StudentRecord.class);
        assertThat(rNullsLast).hasSize(10);
        // The last 2 students must have null GPA
        assertThat(rNullsLast.get(8).gpa()).isNull();
        assertThat(rNullsLast.get(9).gpa()).isNull();
        assertThat(rNullsLast.get(0).gpa()).isNotNull();
    }

    @Test
    @Order(7)
    @DisplayName("7. MULTI-COLUMN SORTING: department_id ASC, gpa DESC NULLS LAST, name ASC")
    void shouldExecuteMultiColumnSorting() {
        SelectQuery query = SelectQuery.from("students_p5")
                .column("id")
                .column("name")
                .column("email")
                .column("gpa")
                .column("department_id", "departmentId")
                .column("age")
                .column("status")
                .orderBy(
                        OrderByClause.asc("department_id"),
                        OrderByClause.descNullsLast("gpa"),
                        OrderByClause.asc("name")
                )
                .build();

        List<StudentRecord> results = executor.queryForList(query, StudentRecord.class);
        assertThat(results).hasSize(10);

        // Department 1 students first, ordered by GPA DESC
        // Ada Lovelace (4.00), Alice Turing (3.95), Alan Kay (3.80), Bob Hopper (3.20), Charles Babbage (2.80)
        assertThat(results.subList(0, 5)).extracting(StudentRecord::name)
                .containsExactly("Ada Lovelace", "Alice Turing", "Alan Kay", "Bob Hopper", "Charles Babbage");

        // Department 2 students second, ordered by GPA DESC NULLS LAST
        // Carl Gauss (4.00), Emmy Noether (3.98), Bernhard Riemann (3.90), then nulls sorted by name: David Hilbert, Euclid Alexandria
        assertThat(results.subList(5, 10)).extracting(StudentRecord::name)
                .containsExactly("Carl Gauss", "Emmy Noether", "Bernhard Riemann", "David Hilbert", "Euclid Alexandria");
    }

    @Test
    @Order(8)
    @DisplayName("8. PUSHDOWN PAGINATION: LIMIT & OFFSET over sorted filtered dataset")
    void shouldExecutePushdownPagination() {
        // Page size = 3, sorting by name ASC
        List<String> collectedNames = new ArrayList<>();

        for (int page = 0; page < 4; page++) {
            SelectQuery pageQuery = SelectQuery.from("students_p5")
                    .column("name")
                    .orderByAsc("name")
                    .limit(3)
                    .offset(page * 3)
                    .build();

            List<StudentRecord> pageResults = executor.queryForList(pageQuery, StudentRecord.class);

            if (page < 3) {
                assertThat(pageResults).hasSize(3);
            } else {
                assertThat(pageResults).hasSize(1); // 10th student on last page
            }

            pageResults.forEach(s -> collectedNames.add(s.name()));
        }

        // All 10 students traversed in exact alphabetical order without duplicates
        assertThat(collectedNames).containsExactly(
                "Ada Lovelace",
                "Alan Kay",
                "Alice Turing",
                "Bernhard Riemann",
                "Bob Hopper",
                "Carl Gauss",
                "Charles Babbage",
                "David Hilbert",
                "Emmy Noether",
                "Euclid Alexandria"
        );
    }

    @Test
    @Order(9)
    @DisplayName("9. DYNAMIC FILTER COMPOSITION: Assembling queries conditionally with andWhere")
    void shouldAssembleDynamicFilters() {
        // Build base query
        SelectQuery.Builder builder = SelectQuery.from("students_p5")
                .column("name")
                .column("gpa")
                .orderByAsc("name");

        // Dynamically add search term
        String searchPrefix = "A";
        if (searchPrefix != null) {
            builder.andWhere(WherePredicate.startsWith("name", searchPrefix));
        }

        // Dynamically add minimum GPA
        Double minGpa = 3.9;
        if (minGpa != null) {
            builder.andWhere(WherePredicate.gte("gpa", minGpa));
        }

        SelectQuery query = builder.build();
        List<StudentRecord> results = executor.queryForList(query, StudentRecord.class);

        // Name starts with A AND GPA >= 3.9:
        // Ada Lovelace (4.0), Alice Turing (3.95). Alan Kay has 3.80 -> excluded.
        assertThat(results).extracting(StudentRecord::name)
                .containsExactly("Ada Lovelace", "Alice Turing");
    }
}
