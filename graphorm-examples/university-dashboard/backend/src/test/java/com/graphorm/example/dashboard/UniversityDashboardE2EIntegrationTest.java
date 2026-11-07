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
            jdbcUrl = "jdbc:postgresql://localhost:5432/university_db";
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
        com.graphorm.example.dashboard.web.dto.CreateDepartmentRequest request =
                new com.graphorm.example.dashboard.web.dto.CreateDepartmentRequest("Biotechnology", "BIOT");
        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/university/departments", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Biotechnology");
    }
}
