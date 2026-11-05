package com.graphorm.graphql;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.engine.GraphOrmGraphQlEngine;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import graphql.ExecutionResult;
import graphql.GraphQL;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milestone 4 Deliverable Test Suite:
 * End-to-end GraphQL execution tests against real PostgreSQL using the GraphQL Java engine
 * wired automatically via GraphORM with zero handwritten DataFetchers or resolvers (TASK-10.6).
 */
@DisplayName("PostgreSQL Automatic GraphQL Execution Integration Tests (Milestone 4 / TASK-10.6)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresGraphQlExecutionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresGraphQlExecutionIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;
    private static ModelRegistry registry;
    private static GraphQL graphQL;

    private static final String GRAPHQL_SDL = """
            type Student {
                id: ID!
                name: String!
                email: String!
                gpa: Float
                departmentId: Int
                status: String!
            }

            type Department {
                id: ID!
                name: String!
                code: String!
            }

            type StudentSummary {
                studentId: Int!
                studentName: String!
                email: String!
                gpa: Float
                status: String!
            }

            type StudentDashboard {
                studentId: Int!
                studentName: String!
                departmentName: String
                courseCount: Int!
                avgGpa: Float
            }

            input StudentCreateInput {
                name: String!
                email: String!
                gpa: Float
                departmentId: Int
                status: String!
            }

            input StudentUpdateInput {
                name: String
                email: String
                gpa: Float
                departmentId: Int
                status: String
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

            input DashboardFilter {
                courseCount: Int
                studentName: String
            }

            type Query {
                students(where: StudentFilter, orderBy: StudentOrderBy, limit: Int, offset: Int): [Student!]!
                student(id: ID!): Student
                departments(limit: Int): [Department!]!
                department(id: ID!): Department
                studentSummaries(limit: Int): [StudentSummary!]!
                studentSummary(studentId: Int!): StudentSummary
                studentDashboards(where: DashboardFilter, limit: Int): [StudentDashboard!]!
                studentDashboard(studentId: Int!): StudentDashboard
            }

            type Mutation {
                createStudent(input: StudentCreateInput!): Student!
                updateStudent(id: ID!, input: StudentUpdateInput!): Student!
                deleteStudent(id: ID!): Boolean!
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
            log.info("Starting Testcontainers PostgreSQL 16 container for Phase 10 / Milestone 4 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_phase10")
                    .withUsername("phase10_user")
                    .withPassword("phase10_pass");
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

        // 1. Initialize Database Schema and Views
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP VIEW IF EXISTS vw_student_summary_p10 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS enrollments_p10 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS students_p10 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments_p10 CASCADE;");

            stmt.execute("""
                    CREATE TABLE departments_p10 (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        code VARCHAR(10) NOT NULL
                    );
                    """);

            stmt.execute("""
                    CREATE TABLE students_p10 (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        email VARCHAR(100) UNIQUE NOT NULL,
                        gpa NUMERIC(3,2),
                        department_id INT REFERENCES departments_p10(id),
                        status VARCHAR(20) NOT NULL
                    );
                    """);

            stmt.execute("""
                    CREATE TABLE enrollments_p10 (
                        id SERIAL PRIMARY KEY,
                        student_id INT NOT NULL REFERENCES students_p10(id) ON DELETE CASCADE,
                        course_name VARCHAR(100) NOT NULL,
                        grade_points NUMERIC(3,2) NOT NULL
                    );
                    """);

            stmt.execute("""
                    CREATE VIEW vw_student_summary_p10 AS
                    SELECT s.id AS student_id, s.name AS student_name, s.email, s.gpa, s.department_id, s.status
                    FROM students_p10 s;
                    """);

            // Seed Data
            stmt.execute("""
                    INSERT INTO departments_p10 (id, name, code) VALUES
                    (1, 'Computer Science', 'CS'),
                    (2, 'Mathematics', 'MATH');
                    """);

            stmt.execute("""
                    INSERT INTO students_p10 (id, name, email, gpa, department_id, status) VALUES
                    (1, 'Alice Smith', 'alice@university.edu', 3.90, 1, 'ACTIVE'),
                    (2, 'Bob Johnson', 'bob@university.edu', 3.40, 2, 'ACTIVE'),
                    (3, 'Charlie Brown', 'charlie@university.edu', 2.80, 1, 'PROBATION');
                    """);

            stmt.execute("""
                    INSERT INTO enrollments_p10 (student_id, course_name, grade_points) VALUES
                    (1, 'Intro to Algorithms', 4.00),
                    (1, 'Database Systems', 3.80),
                    (2, 'Linear Algebra', 3.40),
                    (3, 'Intro to Algorithms', 2.80);
                    """);

            // Advance sequences beyond seed IDs to avoid duplicate key violations on autoincrement inserts
            stmt.execute("SELECT setval(pg_get_serial_sequence('departments_p10', 'id'), (SELECT COALESCE(MAX(id), 1) FROM departments_p10));");
            stmt.execute("SELECT setval(pg_get_serial_sequence('students_p10', 'id'), (SELECT COALESCE(MAX(id), 1) FROM students_p10));");
            stmt.execute("SELECT setval(pg_get_serial_sequence('enrollments_p10', 'id'), (SELECT COALESCE(MAX(id), 1) FROM enrollments_p10));");
        }

        // 2. Parse GraphORM DSL into ModelRegistry
        String graphOrmSchema = """
                datasource db {
                    provider = "postgresql"
                }

                model Department {
                    id   Int    @id @default(autoincrement())
                    name String
                    code String

                    @@map("departments_p10")
                }

                model Student {
                    id           Int     @id @default(autoincrement())
                    name         String
                    email        String  @unique
                    gpa          Float?
                    departmentId Int?    @map("department_id")
                    status       String

                    @@map("students_p10")
                }

                model Enrollment {
                    id          Int    @id @default(autoincrement())
                    studentId   Int    @map("student_id")
                    courseName  String @map("course_name")
                    gradePoints Float  @map("grade_points")

                    @@map("enrollments_p10")
                }

                view StudentSummary {
                    studentId      Int     @map("student_id")
                    studentName    String  @map("student_name")
                    email          String
                    gpa            Float?
                    departmentId   Int?    @map("department_id")
                    status         String

                    @@view
                    @@map("vw_student_summary_p10")
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
        GraphOrmGraphQlEngine engine = GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .dialect(dialect)
                .schema(GRAPHQL_SDL)
                .build();

        graphQL = engine.graphQL();
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("MILESTONE 4: Auto-registered Query.students executes without handwritten DataFetchers")
    void testQueryStudentsList() {
        String query = """
                query {
                    students {
                        id
                        name
                        email
                        gpa
                        status
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(query);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
        assertThat(students).hasSize(3);

        Map<String, Object> alice = students.stream()
                .filter(s -> "Alice Smith".equals(s.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(alice.get("email")).isEqualTo("alice@university.edu");
        assertThat(alice.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @Order(2)
    @DisplayName("MILESTONE 4: Auto-registered Query.students with where filter, orderBy, limit, and offset")
    void testQueryStudentsFilterSortPagination() {
        String query = """
                query {
                    students(where: { gpa: { gte: 3.0 }, status: "ACTIVE" }, orderBy: { gpa: "DESC" }, limit: 2) {
                        name
                        gpa
                        status
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(query);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
        assertThat(students).hasSize(2);
        assertThat(students.get(0).get("name")).isEqualTo("Alice Smith");
        assertThat(students.get(1).get("name")).isEqualTo("Bob Johnson");
    }

    @Test
    @Order(3)
    @DisplayName("MILESTONE 4: Auto-registered Query.student(id) fetches single model entity")
    void testQueryStudentSingleById() {
        String query = """
                query {
                    student(id: 1) {
                        id
                        name
                        email
                        status
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(query);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        Map<String, Object> student = (Map<String, Object>) data.get("student");
        assertThat(student).isNotNull();
        assertThat(student.get("name")).isEqualTo("Alice Smith");
        assertThat(student.get("email")).isEqualTo("alice@university.edu");
    }

    @Test
    @Order(4)
    @DisplayName("MILESTONE 4: Auto-registered Mutation.createStudent inserts row and returns generated values")
    void testMutationCreateStudent() {
        String mutation = """
                mutation {
                    createStudent(input: {
                        name: "Diana Prince",
                        email: "diana@university.edu",
                        gpa: 3.95,
                        departmentId: 1,
                        status: "ACTIVE"
                    }) {
                        id
                        name
                        email
                        gpa
                        status
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(mutation);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        Map<String, Object> created = (Map<String, Object>) data.get("createStudent");
        assertThat(created).isNotNull();
        assertThat(created.get("name")).isEqualTo("Diana Prince");
        assertThat(created.get("email")).isEqualTo("diana@university.edu");
        assertThat(created.get("id")).isNotNull();

        // Verify entity is immediately queryable via GraphQL
        String verifyQuery = "{ student(id: " + created.get("id") + ") { name email } }";
        ExecutionResult verifyResult = graphQL.execute(verifyQuery);
        assertThat(verifyResult.getErrors()).isEmpty();
        Map<String, Object> verifyData = verifyResult.getData();
        Map<String, Object> queried = (Map<String, Object>) verifyData.get("student");
        assertThat(queried.get("name")).isEqualTo("Diana Prince");
    }

    @Test
    @Order(5)
    @DisplayName("MILESTONE 4: Auto-registered Mutation.updateStudent modifies row in PostgreSQL")
    void testMutationUpdateStudent() {
        String mutation = """
                mutation {
                    updateStudent(id: 1, input: {
                        gpa: 4.00,
                        status: "HONORS"
                    }) {
                        id
                        name
                        gpa
                        status
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(mutation);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        Map<String, Object> updated = (Map<String, Object>) data.get("updateStudent");
        assertThat(updated).isNotNull();
        assertThat(updated.get("name")).isEqualTo("Alice Smith");
        assertThat(updated.get("status")).isEqualTo("HONORS");
    }

    @Test
    @Order(6)
    @DisplayName("MILESTONE 4: Auto-registered Mutation.deleteStudent deletes row and returns boolean")
    void testMutationDeleteStudent() {
        String mutation = """
                mutation {
                    deleteStudent(id: 3)
                }
                """;

        ExecutionResult result = graphQL.execute(mutation);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        Boolean deleted = (Boolean) data.get("deleteStudent");
        assertThat(deleted).isTrue();

        // Verify deleted entity returns null
        ExecutionResult verifyResult = graphQL.execute("{ student(id: 3) { id name } }");
        Map<String, Object> verifyData = verifyResult.getData();
        assertThat(verifyData.get("student")).isNull();
    }

    @Test
    @Order(7)
    @DisplayName("MILESTONE 4: Auto-registered Query.studentSummaries queries database-backed PostgreSQL view")
    void testQueryDatabaseBackedView() {
        String query = """
                query {
                    studentSummaries {
                        studentId
                        studentName
                        email
                        status
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(query);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        List<Map<String, Object>> summaries = (List<Map<String, Object>>) data.get("studentSummaries");
        assertThat(summaries).isNotEmpty();

        Map<String, Object> alice = summaries.stream()
                .filter(s -> "Alice Smith".equals(s.get("studentName")))
                .findFirst()
                .orElseThrow();
        assertThat(alice.get("email")).isEqualTo("alice@university.edu");
    }

    @Test
    @Order(8)
    @DisplayName("MILESTONE 4: Auto-registered Query.studentDashboards executes virtual joined view with aggregations")
    void testQueryVirtualAggregatedView() {
        String query = """
                query {
                    studentDashboards {
                        studentId
                        studentName
                        departmentName
                        courseCount
                        avgGpa
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(query);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        List<Map<String, Object>> dashboards = (List<Map<String, Object>>) data.get("studentDashboards");
        assertThat(dashboards).isNotEmpty();

        Map<String, Object> alice = dashboards.stream()
                .filter(d -> "Alice Smith".equals(d.get("studentName")))
                .findFirst()
                .orElseThrow();

        assertThat(alice.get("departmentName")).isEqualTo("Computer Science");
        assertThat(alice.get("courseCount")).isEqualTo(2);
        assertThat(((Number) alice.get("avgGpa")).doubleValue()).isEqualTo(3.90);
    }

    @Test
    @Order(9)
    @DisplayName("MILESTONE 4: Auto-registered Query.studentDashboard(studentId) fetches single view entity")
    void testQuerySingleView() {
        String query = """
                query {
                    studentDashboard(studentId: 1) {
                        studentId
                        studentName
                        departmentName
                        courseCount
                    }
                }
                """;

        ExecutionResult result = graphQL.execute(query);
        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> data = result.getData();
        Map<String, Object> dashboard = (Map<String, Object>) data.get("studentDashboard");
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.get("studentName")).isEqualTo("Alice Smith");
        assertThat(dashboard.get("departmentName")).isEqualTo("Computer Science");
        assertThat(dashboard.get("courseCount")).isEqualTo(2);
    }

    @Test
    @Order(10)
    @DisplayName("MILESTONE 4: Strict Read-Only Invariant blocks any GraphQL mutation registrations targeting views")
    void testStrictReadOnlyViewMutationGuard() {
        String illegalSdl = """
                type StudentDashboard {
                    studentId: Int!
                    studentName: String!
                }

                type Query {
                    studentDashboards: [StudentDashboard!]!
                }

                type Mutation {
                    createStudentDashboard(name: String!): StudentDashboard!
                }
                """;

        assertThatThrownBy(() -> GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .dialect(dialect)
                .schema(illegalSdl)
                .failFastOnViewMutation(true)
                .build())
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentDashboard")
                .hasMessageContaining("strictly read-only");
    }
}
