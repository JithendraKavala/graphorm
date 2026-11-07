package com.graphorm.graphql;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.engine.GraphOrmGraphQlEngine;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.util.SqlStatementCounter;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import graphql.ExecutionResult;
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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 5 Deliverable Test Suite:
 * Selection-Set Query Planner & N+1 Prevention Verification Tests (TASK-11.5).
 * <p>
 * Asserts:
 * <ul>
 *     <li>Exact minimal SQL projections for root queries and nested relationship queries (TASK-11.2).</li>
 *     <li>Auto-injection of hidden foreign key dependencies and target join keys (TASK-11.3).</li>
 *     <li>Zero N+1 query bottlenecks via DataLoader batching across 1:1, N:1, and 1:N relations (TASK-11.4).</li>
 *     <li>Milestone 5 deliverable: {@code { students { name department { name } } }} executes in exactly 2 SQL statements.</li>
 *     <li>100 items with nested relations load in exactly 2 SQL queries.</li>
 * </ul>
 */
@DisplayName("PostgreSQL N+1 Elimination & Selective Projection Tests (Milestone 5 / TASK-11.5)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresNPlusOneEliminationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresNPlusOneEliminationIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource rawDataSource;
    private static DataSource countedDataSource;
    private static SqlStatementCounter statementCounter;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;
    private static ModelRegistry registry;
    private static GraphOrmGraphQlEngine engine;

    private static final String GRAPHQL_SDL = """
            type Student {
                id: ID!
                name: String!
                email: String!
                gpa: Float
                departmentId: Int
                status: String!
                department: Department
            }

            type Department {
                id: ID!
                name: String!
                code: String!
                students: [Student!]!
            }

            type StudentDashboard {
                studentId: Int!
                studentName: String!
                departmentName: String
                courseCount: Int!
                avgGpa: Float
            }

            input FloatFilter {
                gt: Float
                gte: Float
                lt: Float
                lte: Float
                eq: Float
            }

            input StudentFilter {
                id: ID
                name: String
                email: String
                status: String
                gpa: FloatFilter
            }

            input StudentOrderBy {
                name: String
                gpa: String
            }

            type Query {
                students(where: StudentFilter, orderBy: StudentOrderBy, limit: Int, offset: Int): [Student!]!
                student(id: ID!): Student
                departments(limit: Int): [Department!]!
                department(id: ID!): Department
                studentDashboards(limit: Int): [StudentDashboard!]!
            }
            """;

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
            log.info("Starting Testcontainers PostgreSQL 16 container for Milestone 5 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_phase11")
                    .withUsername("phase11_user")
                    .withPassword("phase11_pass");
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

        statementCounter = new SqlStatementCounter();
        countedDataSource = statementCounter.wrap(rawDataSource);
        executor = new JdbcExecutor(countedDataSource, dialect);

        // 1. Initialize Database Schema
        try (Connection conn = rawDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS enrollments_p11 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS students_p11 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments_p11 CASCADE;");

            stmt.execute("""
                    CREATE TABLE departments_p11 (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        code VARCHAR(10) NOT NULL
                    );\
                    """);

            stmt.execute("""
                    CREATE TABLE students_p11 (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        email VARCHAR(100) UNIQUE NOT NULL,
                        gpa NUMERIC(3,2),
                        department_id INT REFERENCES departments_p11(id),
                        status VARCHAR(20) NOT NULL
                    );
                    """);

            stmt.execute("""
                    CREATE TABLE enrollments_p11 (
                        id SERIAL PRIMARY KEY,
                        student_id INT NOT NULL REFERENCES students_p11(id) ON DELETE CASCADE,
                        course_name VARCHAR(100) NOT NULL,
                        grade_points NUMERIC(3,2) NOT NULL
                    );
                    """);

            // Seed Initial Data
            stmt.execute("""
                    INSERT INTO departments_p11 (id, name, code) VALUES
                    (1, 'Computer Science', 'CS'),
                    (2, 'Mathematics', 'MATH'),
                    (3, 'Physics', 'PHYS');
                    """);

            stmt.execute("""
                    INSERT INTO students_p11 (id, name, email, gpa, department_id, status) VALUES
                    (1, 'Alice Smith', 'alice@university.edu', 3.90, 1, 'ACTIVE'),
                    (2, 'Bob Johnson', 'bob@university.edu', 3.40, 2, 'ACTIVE'),
                    (3, 'Charlie Brown', 'charlie@university.edu', 2.80, 1, 'PROBATION'),
                    (4, 'Diana Prince', 'diana@university.edu', 3.95, 3, 'ACTIVE');
                    """);

            stmt.execute("""
                    INSERT INTO enrollments_p11 (student_id, course_name, grade_points) VALUES
                    (1, 'Intro to Algorithms', 4.00),
                    (1, 'Database Systems', 3.80),
                    (2, 'Linear Algebra', 3.40),
                    (3, 'Intro to Algorithms', 2.80);
                    """);

            stmt.execute("SELECT setval(pg_get_serial_sequence('departments_p11', 'id'), (SELECT COALESCE(MAX(id), 1) FROM departments_p11));");
            stmt.execute("SELECT setval(pg_get_serial_sequence('students_p11', 'id'), (SELECT COALESCE(MAX(id), 1) FROM students_p11));");
            stmt.execute("SELECT setval(pg_get_serial_sequence('enrollments_p11', 'id'), (SELECT COALESCE(MAX(id), 1) FROM enrollments_p11));");
        }

        // 2. Parse GraphORM Schema
        String graphOrmSchema = """
                datasource db {
                    provider = "postgresql"
                }

                model Department {
                    id       Int       @id @default(autoincrement())
                    name     String
                    code     String
                    students Student[]

                    @@map("departments_p11")
                }

                model Student {
                    id           Int         @id @default(autoincrement())
                    name         String
                    email        String      @unique
                    gpa          Float?
                    departmentId Int?        @map("department_id")
                    status       String
                    department   Department? @relation(fields: [departmentId], references: [id])

                    @@map("students_p11")
                }

                model Enrollment {
                    id          Int    @id @default(autoincrement())
                    studentId   Int    @map("student_id")
                    courseName  String @map("course_name")
                    gradePoints Float  @map("grade_points")

                    @@map("enrollments_p11")
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

        registry = SchemaToMetadataTransformer.transform(graphOrmSchema);

        // 3. Boot GraphORM GraphQL Engine
        engine = GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .dialect(dialect)
                .schema(GRAPHQL_SDL)
                .build();

        log.info("Test environment and GraphORM engine initialized for Milestone 5");
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @BeforeEach
    void resetCounter() {
        statementCounter.reset();
    }

    // =========================================================================
    // Milestone 5 Deliverable Verification Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Milestone 5 Deliverable: { students { name department { name } } } executes in exactly 2 SQL statements with selective projection")
    void testMilestone5DeliverableNestedModelQuery() {
        String query = """
                query {
                    students {
                        name
                        department {
                            name
                        }
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("students");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
        assertThat(students).hasSize(4);

        // Verify correct association in GraphQL result
        Map<String, Object> alice = students.stream()
                .filter(s -> "Alice Smith".equals(s.get("name")))
                .findFirst().orElseThrow();
        assertThat(alice).containsOnlyKeys("name", "department");

        @SuppressWarnings("unchecked")
        Map<String, Object> aliceDept = (Map<String, Object>) alice.get("department");
        assertThat(aliceDept).containsOnlyKeys("name");
        assertThat(aliceDept.get("name")).isEqualTo("Computer Science");

        // 1. Assert exactly 2 SQL statements executed (0 N+1 queries)
        statementCounter.assertSelectCount(2);

        List<String> statements = statementCounter.statements();
        String studentQuery = statements.get(0);
        String departmentQuery = statements.get(1);

        log.info("Statement 1 (students): {}", studentQuery);
        log.info("Statement 2 (departments): {}", departmentQuery);

        // 2. Assert Statement 1 projection: only 'name' and 'department_id'
        String upperStudentQuery = studentQuery.toUpperCase(Locale.ROOT);
        assertThat(upperStudentQuery).contains("\"NAME\"");
        assertThat(upperStudentQuery).contains("\"DEPARTMENT_ID\"");
        // Verify unrequested fields are NOT selected
        assertThat(upperStudentQuery).doesNotContain("\"EMAIL\"");
        assertThat(upperStudentQuery).doesNotContain("\"GPA\"");
        assertThat(upperStudentQuery).doesNotContain("\"STATUS\"");

        // 3. Assert Statement 2 projection: only 'id' and 'name'
        String upperDeptQuery = departmentQuery.toUpperCase(Locale.ROOT);
        assertThat(upperDeptQuery).contains("\"NAME\"");
        assertThat(upperDeptQuery).contains("\"ID\"");
        // Verify unrequested fields are NOT selected
        assertThat(upperDeptQuery).doesNotContain("\"CODE\"");
        // Verify batched WHERE IN
        assertThat(upperDeptQuery).contains("\"ID\" IN");
    }

    @Test
    @Order(2)
    @DisplayName("TASK-11.4 / TASK-11.5: 1:N Relationship { departments { name students { name } } } executes in exactly 2 SQL statements")
    void testOneToManyNestedQuery() {
        String query = """
                query {
                    departments {
                        name
                        students {
                            name
                        }
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("departments");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> departments = (List<Map<String, Object>>) data.get("departments");
        assertThat(departments).hasSize(3);

        Map<String, Object> cs = departments.stream()
                .filter(d -> "Computer Science".equals(d.get("name")))
                .findFirst().orElseThrow();
        assertThat(cs).containsOnlyKeys("name", "students");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> csStudents = (List<Map<String, Object>>) cs.get("students");
        assertThat(csStudents).hasSize(2);
        assertThat(csStudents.stream().map(s -> s.get("name"))).containsExactlyInAnyOrder("Alice Smith", "Charlie Brown");

        // Assert exactly 2 SQL statements
        statementCounter.assertSelectCount(2);

        List<String> statements = statementCounter.statements();
        String deptSql = statements.get(0).toUpperCase(Locale.ROOT);
        String studentSql = statements.get(1).toUpperCase(Locale.ROOT);

        log.info("Dept Statement: {}", deptSql);
        log.info("Student Statement: {}", studentSql);

        // Verify selective projection
        assertThat(deptSql).contains("\"NAME\"");
        assertThat(deptSql).contains("\"ID\"");
        assertThat(deptSql).doesNotContain("\"CODE\"");

        assertThat(studentSql).contains("\"NAME\"");
        assertThat(studentSql).contains("\"DEPARTMENT_ID\"");
        assertThat(studentSql).doesNotContain("\"EMAIL\"");
        assertThat(studentSql).doesNotContain("\"GPA\"");
    }

    @Test
    @Order(3)
    @DisplayName("TASK-11.5: Zero N+1 query regression across 100 students in 5 departments")
    void testZeroNPlusOneWithLargeDataset() throws Exception {
        // Seed 100 additional students and 2 departments
        try (Connection conn = rawDataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        INSERT INTO departments_p11 (id, name, code) VALUES
                        (4, 'Chemistry', 'CHEM'),
                        (5, 'Biology', 'BIO')
                        ON CONFLICT (id) DO NOTHING;
                        """);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO students_p11 (name, email, gpa, department_id, status) VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 5; i <= 104; i++) {
                    ps.setString(1, "Student " + i);
                    ps.setString(2, "student" + i + "@university.edu");
                    ps.setDouble(3, 3.50);
                    ps.setInt(4, (i % 5) + 1); // evenly distributed across departments 1-5
                    ps.setString(5, "ACTIVE");
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        statementCounter.reset();

        String query = """
                query {
                    students(limit: 100) {
                        name
                        department {
                            name
                        }
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
        assertThat(students).hasSize(100);

        // Verification: 100 nested entities loaded in STILL EXACTLY 2 SQL statements!
        statementCounter.assertSelectCount(2);
        log.info("100 students loaded with relations in exactly {} SQL queries (Zero N+1!)",
                statementCounter.selectCount());
    }

    @Test
    @Order(4)
    @DisplayName("TASK-11.2: Selective column projection and join pruning on View Model query")
    void testViewModelSelectiveProjectionAndJoinPruning() {
        String query = """
                query {
                    studentDashboards(limit: 3) {
                        studentName
                        courseCount
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dashboards = (List<Map<String, Object>>) data.get("studentDashboards");
        assertThat(dashboards).isNotEmpty();

        Map<String, Object> first = dashboards.get(0);
        assertThat(first).containsOnlyKeys("studentName", "courseCount");

        // Assert exactly 1 SQL statement
        statementCounter.assertSelectCount(1);
        String sql = statementCounter.statements().get(0).toUpperCase(Locale.ROOT);

        log.info("View Dashboard Statement: {}", sql);

        // Assert selective projection
        assertThat(sql).contains("COUNT(E.ID)");
        assertThat(sql).contains("\"S\".\"NAME\"");
        // Assert unrequested fields are omitted
        assertThat(sql).doesNotContain("AVG(E.GRADE_POINTS)");
        // Assert unrequested joined table 'departments_p11' is PRUNED from joins
        assertThat(sql).doesNotContain("DEPARTMENTS_P11");
    }

    @Test
    @Order(5)
    @DisplayName("Single entity query with nested relation: { student(id: 1) { name department { name } } } executes in 2 SQL statements")
    void testSingleEntityWithRelation() {
        String query = """
                query {
                    student(id: "1") {
                        name
                        department {
                            name
                        }
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) data.get("student");
        assertThat(student).isNotNull();
        assertThat(student).containsOnlyKeys("name", "department");
        assertThat(student.get("name")).isEqualTo("Alice Smith");

        @SuppressWarnings("unchecked")
        Map<String, Object> dept = (Map<String, Object>) student.get("department");
        assertThat(dept.get("name")).isEqualTo("Computer Science");

        // Exactly 2 SQL statements: 1 for single student, 1 for department
        statementCounter.assertSelectCount(2);
    }
}
