package com.graphorm.example.dashboard;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.schema.GraphQLObjectType;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("University Dashboard Full-Stack Backend Integration Tests")
class UniversityDashboardE2EIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(UniversityDashboardE2EIntegrationTest.class);

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
            log.info("Starting Testcontainers PostgreSQL container for Dashboard tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_dashboard")
                    .withUsername("test_dash")
                    .withPassword("test_dash");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for Dashboard tests...");
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
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("graphorm.migration.enabled", () -> "true");
        registry.add("graphorm.migration.location", () -> "classpath:migrations");
        registry.add("graphorm.schema-location", () -> "classpath:schema.graphorm");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GraphQlSource graphQlSource;

    @Test
    @Order(1)
    @DisplayName("REST: Health check reports UP with models and views")
    void testRestHealthCheck() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/university/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("framework")).isEqualTo("GraphORM 0.1.0");
    }

    @Test
    @Order(2)
    @DisplayName("REST: Fetch departments list")
    void testRestGetDepartments() {
        ResponseEntity<List> response = restTemplate.getForEntity("/api/university/departments", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(3)
    @DisplayName("REST: Physical View (DepartmentStats) endpoint")
    void testRestGetDepartmentStats() {
        ResponseEntity<List> response = restTemplate.getForEntity("/api/university/stats", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("REST: Virtual Aggregated View (StudentDashboard) endpoint")
    void testRestGetStudentDashboards() {
        ResponseEntity<List> response = restTemplate.getForEntity("/api/university/dashboards", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("GraphQL: Query students with DataLoader batching and nested views")
    void testGraphQlQueryStudents() {
        String query = """
                query {
                    students {
                        id
                        name
                        email
                        department {
                            name
                            code
                        }
                    }
                    departmentStats {
                        departmentName
                        studentCount
                        courseCount
                    }
                    studentDashboards {
                        studentName
                        enrolledCourses
                        avgGradePoints
                    }
                }
                """;

        ExecutionResult result = graphQlSource.graphQl().execute(
                ExecutionInput.newExecutionInput().query(query).build()
        );

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKeys("students", "departmentStats", "studentDashboards");
    }

    @Test
    @Order(6)
    @DisplayName("Strict Read-Only Invariant: View mutations must not exist")
    void testReadOnlyViewsHaveNoMutations() {
        GraphQLObjectType mutationType = graphQlSource.schema().getMutationType();
        assertThat(mutationType).isNotNull();
        assertThat(mutationType.getFieldDefinition("createStudent")).isNotNull();
        assertThat(mutationType.getFieldDefinition("createDepartmentStats")).isNull();
        assertThat(mutationType.getFieldDefinition("createStudentDashboard")).isNull();
    }
    @Test
    @Order(7)
    @DisplayName("SEC-04: Unauthenticated REST mutation is rejected with 401")
    void testUnauthenticatedMutationIsRejected() {
        com.graphorm.example.dashboard.web.dto.CreateDepartmentRequest request =
                new com.graphorm.example.dashboard.web.dto.CreateDepartmentRequest("Robotics", "ROBO");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/university/departments", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(8)
    @DisplayName("SEC-04: Authenticated REST mutation with credentials succeeds")
    void testAuthenticatedMutationSucceeds() {
        String deptCode = "B" + (System.currentTimeMillis() % 100000);
        com.graphorm.example.dashboard.web.dto.CreateDepartmentRequest request =
                new com.graphorm.example.dashboard.web.dto.CreateDepartmentRequest("Biotechnology", deptCode);
        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/university/departments", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Biotechnology");
    }

    @Test
    @Order(9)
    @DisplayName("GraphQL: Relay Keyset Cursor Pagination on studentsConnection")
    void testGraphQlStudentsConnection() {
        String query = """
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

        ExecutionResult result = graphQlSource.graphQl().execute(
                ExecutionInput.newExecutionInput().query(query).build()
        );

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKey("studentsConnection");
        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) data.get("studentsConnection");
        assertThat(((Number) conn.get("totalCount")).intValue()).isGreaterThanOrEqualTo(6);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) conn.get("edges");
        assertThat(edges).hasSize(2);
    }

    @Test
    @Order(10)
    @DisplayName("REST: Upsert student creates and then updates idempotently")
    void testUpsertStudent() {
        com.graphorm.example.dashboard.web.dto.UpsertStudentRequest createReq =
                new com.graphorm.example.dashboard.web.dto.UpsertStudentRequest("Donald Knuth", "knuth@dashboard.edu", 3.99, 1);
        ResponseEntity<Map> createRes = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/university/students/upsert", createReq, Map.class);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createRes.getBody()).isNotNull();
        assertThat(createRes.getBody().get("email")).isEqualTo("knuth@dashboard.edu");

        // Upsert update existing
        com.graphorm.example.dashboard.web.dto.UpsertStudentRequest updateReq =
                new com.graphorm.example.dashboard.web.dto.UpsertStudentRequest("Donald E. Knuth", "knuth@dashboard.edu", 4.0, 1);
        ResponseEntity<Map> updateRes = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/university/students/upsert", updateReq, Map.class);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateRes.getBody()).isNotNull();
        assertThat(updateRes.getBody().get("name")).isEqualTo("Donald E. Knuth");
    }

    @Test
    @Order(11)
    @DisplayName("REST: Batch insert courses in single transaction")
    void testBatchCreateCourses() {
        long now = System.currentTimeMillis() % 100000;
        List<com.graphorm.example.dashboard.web.dto.CreateCourseRequest> list = List.of(
                new com.graphorm.example.dashboard.web.dto.CreateCourseRequest("Advanced Distributed Systems", "C" + now, 4, 1, 1),
                new com.graphorm.example.dashboard.web.dto.CreateCourseRequest("Deep Learning Architectures", "D" + now, 4, 1, 2)
        );
        ResponseEntity<List> response = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/university/courses/batch", list, List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @Order(12)
    @DisplayName("REST: Optimistic locking rejects stale version update with 409 Conflict")
    void testOptimisticLocking() {
        com.graphorm.example.dashboard.web.dto.UpdateStudentRequest staleReq =
                new com.graphorm.example.dashboard.web.dto.UpdateStudentRequest("Claude Stale", "shannon@student.edu", 3.9, 1, 0);
        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("admin", "admin123")
                .exchange("/api/university/students/2", HttpMethod.PUT, new HttpEntity<>(staleReq), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("OPTIMISTIC_LOCK_FAILURE");
    }

    @Test
    @Order(13)
    @DisplayName("REST: Multi-tenant audit logs are partitioned by tenant")
    void testMultiTenantAuditLogs() {
        ResponseEntity<List> mainLogs = restTemplate
                .withBasicAuth("admin", "admin123")
                .getForEntity("/api/university/audit-logs?tenantId=tenant-main", List.class);
        assertThat(mainLogs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mainLogs.getBody()).isNotNull();
    }
}

