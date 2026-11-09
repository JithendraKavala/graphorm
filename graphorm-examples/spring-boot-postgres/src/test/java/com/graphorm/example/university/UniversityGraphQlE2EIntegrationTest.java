package com.graphorm.example.university;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.schema.GraphQLObjectType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for GraphORM automatic GraphQL engine (TASK-16.2).
 * <p>
 * Verifies:
 * <ul>
 *     <li>Auto-registered queries for models and views</li>
 *     <li>Relay-spec cursor pagination connections (Connection/Edge/PageInfo)</li>
 *     <li>Nested relationship resolution with DataLoader batching</li>
 *     <li>Database-backed physical view queries</li>
 *     <li>Virtual aggregated view queries</li>
 *     <li>Model mutations</li>
 *     <li>Strict read-only view and physical model mutation exclusion</li>
 *     <li>Field-level, model-level, and view-level authorization via {@code @auth} and {@code @@auth}</li>
 *     <li>Backend-injected column attribute {@code @injected}</li>
 *     <li>Query depth and complexity security limits</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TASK-16.2: University GraphQL E2E Integration Tests")
class UniversityGraphQlE2EIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(UniversityGraphQlE2EIntegrationTest.class);

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
            log.info("Starting Testcontainers PostgreSQL container for GraphQL tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_university_gql")
                    .withUsername("test_univ")
                    .withPassword("test_univ");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for GraphQL tests...");
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
                stmt.execute("DELETE FROM students WHERE email = 'kjohnson@nasa.gov'");
                stmt.execute("DELETE FROM audit_logs WHERE action = 'CURRICULUM_REVISION'");
            } catch (Exception ignored) {}
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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
        registry.add("graphorm.security.max-query-depth", () -> "6");
        registry.add("graphorm.security.max-query-complexity", () -> "80");
    }

    @Autowired
    private GraphQlSource graphQlSource;

    private ExecutionResult executeQuery(String query) {
        return executeQuery(query, Map.of());
    }

    private ExecutionResult executeQuery(String query, Map<String, Object> context) {
        ExecutionInput.Builder builder = ExecutionInput.newExecutionInput().query(query);
        String tenantId = context != null ? (String) context.get("tenantId") : null;
        if (tenantId != null) {
            com.graphorm.core.tenant.TenantContext.setTenantId(tenantId);
        }
        if (context != null && !context.isEmpty()) {
            builder.graphQLContext(context);
        }
        try {
            return graphQlSource.graphQl().execute(builder.build());
        } finally {
            if (tenantId != null) {
                com.graphorm.core.tenant.TenantContext.clear();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Query regular models with nested relationships via DataLoader")
    void testQueryStudentsWithNestedRelationships() {
        String query = """
                query {
                    students {
                        id
                        name
                        email
                        version
                        department {
                            id
                            name
                            code
                        }
                        enrollments {
                            id
                            course {
                                id
                                title
                                code
                            }
                        }
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("students");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
        assertThat(students).hasSize(6);

        // Find Grace Hopper
        Map<String, Object> grace = students.stream()
                .filter(s -> "Grace Hopper".equals(s.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(grace.get("email")).isEqualTo("hopper@student.edu");
        assertThat(grace.get("version")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> dept = (Map<String, Object>) grace.get("department");
        assertThat(dept).isNotNull();
        assertThat(dept.get("name")).isEqualTo("Computer Science");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enrollments = (List<Map<String, Object>>) grace.get("enrollments");
        assertThat(enrollments).hasSize(2);
    }

    @Test
    @Order(2)
    @DisplayName("Relay Connection: Keyset cursor pagination on studentsConnection")
    void testRelayCursorPaginationStudents() {
        String queryFirstPage = """
                query {
                    studentsConnection(first: 2) {
                        totalCount
                        edges {
                            cursor
                            node {
                                id
                                name
                                email
                            }
                        }
                        pageInfo {
                            hasNextPage
                            hasPreviousPage
                            startCursor
                            endCursor
                        }
                    }
                }
                """;

        ExecutionResult result = executeQuery(queryFirstPage);
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("studentsConnection");

        @SuppressWarnings("unchecked")
        Map<String, Object> connection = (Map<String, Object>) data.get("studentsConnection");
        assertThat(connection.get("totalCount")).isEqualTo(6);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) connection.get("edges");
        assertThat(edges).hasSize(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> pageInfo = (Map<String, Object>) connection.get("pageInfo");
        assertThat(pageInfo.get("hasNextPage")).isEqualTo(true);
        String endCursor = (String) pageInfo.get("endCursor");
        assertThat(endCursor).isNotEmpty();

        // Query second page using after: endCursor
        String querySecondPage = String.format("""
                query {
                    studentsConnection(first: 2, after: "%s") {
                        edges {
                            cursor
                            node {
                                id
                                name
                            }
                        }
                        pageInfo {
                            hasNextPage
                            hasPreviousPage
                        }
                    }
                }
                """, endCursor);

        ExecutionResult page2Result = executeQuery(querySecondPage);
        assertThat(page2Result.getErrors()).isEmpty();
        Map<String, Object> page2Data = page2Result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> page2Conn = (Map<String, Object>) page2Data.get("studentsConnection");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> page2Edges = (List<Map<String, Object>>) page2Conn.get("edges");
        assertThat(page2Edges).hasSize(2);
    }

    @Test
    @Order(3)
    @DisplayName("Query database-backed view (DepartmentStats)")
    void testQueryDepartmentStats_DatabaseBackedView() {
        String query = """
                query {
                    departmentStats {
                        id
                        departmentName
                        studentCount
                        courseCount
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("departmentStats");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) data.get("departmentStats");
        assertThat(stats).hasSize(3);

        Map<String, Object> cs = stats.stream()
                .filter(s -> "Computer Science".equals(s.get("departmentName")))
                .findFirst()
                .orElseThrow();
        assertThat(cs.get("studentCount")).isEqualTo(2);
        assertThat(cs.get("courseCount")).isEqualTo(2);
    }

    @Test
    @Order(4)
    @DisplayName("Query virtual aggregated view (StudentDashboard)")
    void testQueryStudentDashboard_VirtualAggregatedView() {
        String query = """
                query {
                    studentDashboards {
                        studentId
                        studentName
                        departmentName
                        enrolledCourses
                        avgGradePoints
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("studentDashboards");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dashboards = (List<Map<String, Object>>) data.get("studentDashboards");
        assertThat(dashboards).hasSize(6);

        Map<String, Object> hopper = dashboards.stream()
                .filter(d -> "Grace Hopper".equals(d.get("studentName")))
                .findFirst()
                .orElseThrow();
        assertThat(hopper.get("departmentName")).isEqualTo("Computer Science");
        assertThat(hopper.get("enrolledCourses")).isEqualTo(2);
        assertThat(hopper.get("avgGradePoints")).isEqualTo(4.0);
    }

    @Test
    @Order(5)
    @DisplayName("Model mutation: create new Student via createStudent")
    void testCreateStudentMutation() {
        String mutation = """
                mutation {
                    createStudent(input: {
                        name: "Katherine Johnson"
                        email: "kjohnson@nasa.gov"
                        departmentId: 2
                    }) {
                        id
                        name
                        email
                        version
                    }
                }
                """;

        ExecutionResult result = executeQuery(mutation);
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("createStudent");

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) data.get("createStudent");
        assertThat(created.get("name")).isEqualTo("Katherine Johnson");
        assertThat(created.get("email")).isEqualTo("kjohnson@nasa.gov");
        assertThat(created.get("version")).isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("Strict Read-Only Invariant: view mutations must NOT exist in GraphQL schema")
    void testReadOnlyViewsHaveNoMutations() {
        GraphQLObjectType mutationType = graphQlSource.schema().getMutationType();
        assertThat(mutationType).isNotNull();

        // Model mutations must exist
        assertThat(mutationType.getFieldDefinition("createStudent")).isNotNull();
        assertThat(mutationType.getFieldDefinition("createDepartment")).isNotNull();
        assertThat(mutationType.getFieldDefinition("createCourse")).isNotNull();

        // View mutations must strictly NOT exist
        assertThat(mutationType.getFieldDefinition("createStudentDashboard")).isNull();
        assertThat(mutationType.getFieldDefinition("updateStudentDashboard")).isNull();
        assertThat(mutationType.getFieldDefinition("deleteStudentDashboard")).isNull();
        assertThat(mutationType.getFieldDefinition("createDepartmentStats")).isNull();
        assertThat(mutationType.getFieldDefinition("updateDepartmentStats")).isNull();
        assertThat(mutationType.getFieldDefinition("deleteDepartmentStats")).isNull();
    }

    @Test
    @Order(7)
    @DisplayName("@auth: rejects access to Instructor salary without ADMIN role")
    void testAuthRejectsInstructorSalaryWithoutAdminRole() {
        // Authenticated as regular STUDENT
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "student123",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
        );

        String query = """
                query {
                    instructors {
                        id
                        name
                        salary
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isNotEmpty();
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("Access denied");
        assertThat(error.getExtensions()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    @Order(8)
    @DisplayName("@auth: permits access to Instructor salary with ADMIN role")
    void testAuthPermitsInstructorSalaryWithAdminRole() {
        // Authenticated as ADMIN
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "admin123",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        String query = """
                query {
                    instructors {
                        id
                        name
                        salary
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("instructors");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instructors = (List<Map<String, Object>>) data.get("instructors");
        assertThat(instructors).isNotEmpty();
        assertThat(instructors.get(0).get("salary")).isNotNull();
    }

    @Test
    @Order(9)
    @DisplayName("Security: rejects queries exceeding max query depth")
    void testRejectsExceededQueryDepth() {
        String deepQuery = """
                query DeepQuery {
                    students {
                        department {
                            students {
                                department {
                                    students {
                                        department {
                                            id
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """;

        ExecutionResult result = executeQuery(deepQuery);
        assertThat(result.getErrors()).isNotEmpty();
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("depth");
        assertThat(error.getExtensions()).containsEntry("code", "GRAPHQL_DEPTH_LIMIT_EXCEEDED");
    }

    @Test
    @Order(10)
    @DisplayName("Security: rejects queries exceeding max query complexity")
    void testRejectsExceededQueryComplexity() {
        String complexQuery = """
                query ComplexQuery {
                    s1: students { id name email department { id name code } enrollments { id course { id title code credits } } }
                    s2: students { id name email department { id name code } enrollments { id course { id title code credits } } }
                    s3: students { id name email department { id name code } enrollments { id course { id title code credits } } }
                    s4: students { id name email department { id name code } enrollments { id course { id title code credits } } }
                }
                """;

        ExecutionResult result = executeQuery(complexQuery);
        assertThat(result.getErrors()).isNotEmpty();
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("complexity");
        assertThat(error.getExtensions()).containsEntry("code", "GRAPHQL_COMPLEXITY_LIMIT_EXCEEDED");
    }

    @Test
    @Order(11)
    @DisplayName("@@readonly: GradeScale queries succeed and mutations are strictly omitted from GraphQL schema")
    void testReadOnlyPhysicalModelGradeScale() {
        GraphQLObjectType mutationType = graphQlSource.schema().getMutationType();
        assertThat(mutationType).isNotNull();

        // GradeScale mutations must NOT exist
        assertThat(mutationType.getFieldDefinition("createGradeScale")).isNull();
        assertThat(mutationType.getFieldDefinition("updateGradeScale")).isNull();
        assertThat(mutationType.getFieldDefinition("deleteGradeScale")).isNull();

        // GradeScale queries must work
        String query = """
                query {
                    gradeScales {
                        id
                        letter
                        minScore
                        gpaPoint
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("gradeScales");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scales = (List<Map<String, Object>>) data.get("gradeScales");
        assertThat(scales).hasSize(5);

        Map<String, Object> scaleA = scales.stream()
                .filter(s -> "A".equals(s.get("letter")))
                .findFirst()
                .orElseThrow();
        assertThat(scaleA.get("minScore")).isEqualTo(90.0);
        assertThat(scaleA.get("gpaPoint")).isEqualTo(4.0);
    }

    @Test
    @Order(12)
    @DisplayName("@@auth on Model: rejects access to auditLogs query without ADMIN role")
    void testModelLevelAuthRejectsUnauthorized() {
        // Authenticated as regular STUDENT
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "student123",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
        );

        String query = """
                query {
                    auditLogs {
                        id
                        action
                        tenantId
                        performedBy
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isNotEmpty();
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("Access denied");
        assertThat(error.getExtensions()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    @Order(13)
    @DisplayName("@@auth on Model: permits access to auditLogs query with ADMIN role")
    void testModelLevelAuthPermitsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "admin123",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        String query = """
                query {
                    auditLogs {
                        id
                        action
                        tenantId
                        performedBy
                    }
                }
                """;

        ExecutionResult result = executeQuery(query, Map.of("tenantId", "tenant-main"));
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("auditLogs");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) data.get("auditLogs");
        assertThat(logs).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(14)
    @DisplayName("@@auth on View: rejects access to auditSummaries query without ADMIN role")
    void testViewLevelAuthRejectsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "student123",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
        );

        String query = """
                query {
                    auditSummaries {
                        id
                        action
                        tenantId
                        performedBy
                    }
                }
                """;

        ExecutionResult result = executeQuery(query);
        assertThat(result.getErrors()).isNotEmpty();
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("Access denied");
        assertThat(error.getExtensions()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    @Order(15)
    @DisplayName("@@auth on View: permits access to auditSummaries query with ADMIN role")
    void testViewLevelAuthPermitsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "admin123",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        String query = """
                query {
                    auditSummaries {
                        id
                        action
                        tenantId
                        performedBy
                    }
                }
                """;

        ExecutionResult result = executeQuery(query, Map.of("tenantId", "tenant-main"));
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("auditSummaries");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summaries = (List<Map<String, Object>>) data.get("auditSummaries");
        assertThat(summaries).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(16)
    @DisplayName("@injected: backend automatically injects context and security values on createAuditLog mutation")
    void testInjectedColumnsOnCreateAuditLogMutation() {
        // Authenticated as ADMIN with username "auditor_clara"
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("auditor_clara", "admin123",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        String mutation = """
                mutation {
                    createAuditLog(input: {
                        action: "CURRICULUM_REVISION"
                        details: "Added quantum computing modules"
                    }) {
                        id
                        action
                        details
                        tenantId
                        performedBy
                    }
                }
                """;

        // tenantId provided in GraphQL execution context
        Map<String, Object> context = Map.of("tenantId", "tenant-science-faculty");
        ExecutionResult result = executeQuery(mutation, context);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("createAuditLog");

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) data.get("createAuditLog");
        assertThat(created.get("action")).isEqualTo("CURRICULUM_REVISION");
        assertThat(created.get("details")).isEqualTo("Added quantum computing modules");
        // Verified: tenantId was injected from GraphQLContext
        assertThat(created.get("tenantId")).isEqualTo("tenant-science-faculty");
        // Verified: performedBy was injected from Spring Security SecurityContextHolder
        assertThat(created.get("performedBy")).isEqualTo("auditor_clara");
    }
}
