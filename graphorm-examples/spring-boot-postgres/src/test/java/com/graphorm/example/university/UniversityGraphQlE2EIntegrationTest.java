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
 *     <li>Nested relationship resolution with DataLoader batching</li>
 *     <li>Database-backed physical view queries</li>
 *     <li>Virtual aggregated view queries</li>
 *     <li>Model mutations</li>
 *     <li>Strict read-only view mutation exclusion</li>
 *     <li>Field-level authorization via {@code @auth} directive</li>
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
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema.sql");
        registry.add("spring.sql.init.data-locations", () -> "classpath:data.sql");
        registry.add("graphorm.schema-location", () -> "classpath:schema.graphorm");
        registry.add("graphorm.security.max-query-depth", () -> "6");
        registry.add("graphorm.security.max-query-complexity", () -> "80");
    }

    @Autowired
    private GraphQlSource graphQlSource;

    private ExecutionResult executeQuery(String query) {
        return graphQlSource.graphQl().execute(
                ExecutionInput.newExecutionInput().query(query).build()
        );
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
    @Order(3)
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
    @Order(4)
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
    }

    @Test
    @Order(5)
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
    @Order(6)
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
    @Order(7)
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
    @Order(8)
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
    @Order(9)
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
}
