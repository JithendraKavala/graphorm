package com.graphorm.example.university;

import com.graphorm.example.university.generated.model.Course;
import com.graphorm.example.university.generated.model.Department;
import com.graphorm.example.university.generated.model.DepartmentStats;
import com.graphorm.example.university.generated.model.Student;
import com.graphorm.example.university.generated.model.StudentDashboard;
import com.graphorm.example.university.web.dto.CreateCourseRequest;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the University Reference Application (TASK-16.2).
 * <p>
 * Verifies REST API endpoints backed by type-safe GraphORM Client queries over
 * regular models, database-backed views, and virtual aggregated views against a live PostgreSQL database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TASK-16.2: University Application E2E Integration Tests")
class UniversityApplicationE2EIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(UniversityApplicationE2EIntegrationTest.class);

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
            log.info("Starting Testcontainers PostgreSQL container for E2E tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_university_e2e")
                    .withUsername("test_univ")
                    .withPassword("test_univ");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for E2E tests...");
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
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema.sql");
        registry.add("spring.sql.init.data-locations", () -> "classpath:data.sql");
        registry.add("graphorm.schema-location", () -> "classpath:schema.graphorm");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @Order(1)
    @DisplayName("GET /api/university/departments - retrieves all departments")
    void testGetAllDepartments() {
        ResponseEntity<List<Department>> response = restTemplate.exchange(
                "/api/university/departments",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Department> departments = response.getBody();
        assertThat(departments).isNotNull().hasSize(3);
        assertThat(departments).extracting(Department::name)
                .containsExactly("Computer Science", "Mathematics", "Physics");
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/university/students - retrieves all students")
    void testGetAllStudents() {
        ResponseEntity<List<Student>> response = restTemplate.exchange(
                "/api/university/students",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Student> students = response.getBody();
        assertThat(students).isNotNull().hasSize(6);
        assertThat(students).extracting(Student::name)
                .contains("Grace Hopper", "Claude Shannon", "John von Neumann", "Emmy Noether", "Marie Curie");
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/university/students/1 - retrieves student by ID")
    void testGetStudentById() {
        ResponseEntity<Student> response = restTemplate.getForEntity("/api/university/students/1", Student.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Student student = response.getBody();
        assertThat(student).isNotNull();
        assertThat(student.id()).isEqualTo(1);
        assertThat(student.name()).isEqualTo("Grace Hopper");
        assertThat(student.email()).isEqualTo("hopper@student.edu");
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/university/courses - creates course via Client API")
    void testCreateCourse() {
        CreateCourseRequest request = new CreateCourseRequest(
                "Distributed Systems",
                "CS301",
                4,
                1,
                1
        );

        ResponseEntity<Course> response = restTemplate.postForEntity(
                "/api/university/courses",
                request,
                Course.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Course created = response.getBody();
        assertThat(created).isNotNull();
        assertThat(created.title()).isEqualTo("Distributed Systems");
        assertThat(created.code()).isEqualTo("CS301");
        assertThat(created.credits()).isEqualTo(4);
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/university/stats - queries database-backed view (v_department_stats)")
    void testGetDepartmentStats_DatabaseBackedView() {
        ResponseEntity<List<DepartmentStats>> response = restTemplate.exchange(
                "/api/university/stats",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<DepartmentStats> stats = response.getBody();
        assertThat(stats).isNotNull().hasSize(3);

        DepartmentStats csStats = stats.stream()
                .filter(s -> "Computer Science".equals(s.departmentName()))
                .findFirst()
                .orElseThrow();
        assertThat(csStats.studentCount()).isEqualTo(2); // Grace Hopper, Claude Shannon
        assertThat(csStats.courseCount()).isGreaterThanOrEqualTo(2); // CS101, CS201 (+ created CS301)
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/university/dashboards - queries virtual aggregated view (StudentDashboard)")
    void testGetStudentDashboards_VirtualAggregatedView() {
        ResponseEntity<List<StudentDashboard>> response = restTemplate.exchange(
                "/api/university/dashboards",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<StudentDashboard> dashboards = response.getBody();
        assertThat(dashboards).isNotNull().hasSize(6);

        // Grace Hopper: enrolled in 2 courses (CS101, CS201), avg grade points = 4.0
        StudentDashboard hopper = dashboards.stream()
                .filter(d -> "Grace Hopper".equals(d.studentName()))
                .findFirst()
                .orElseThrow();
        assertThat(hopper.departmentName()).isEqualTo("Computer Science");
        assertThat(hopper.enrolledCourses()).isEqualTo(2);
        assertThat(hopper.avgGradePoints()).isEqualTo(4.0);
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/university/dashboards/1 - queries single student dashboard")
    void testGetSingleStudentDashboard() {
        ResponseEntity<StudentDashboard> response = restTemplate.getForEntity(
                "/api/university/dashboards/1",
                StudentDashboard.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StudentDashboard dashboard = response.getBody();
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.studentId()).isEqualTo(1);
        assertThat(dashboard.studentName()).isEqualTo("Grace Hopper");
        assertThat(dashboard.departmentName()).isEqualTo("Computer Science");
        assertThat(dashboard.enrolledCourses()).isEqualTo(2);
        assertThat(dashboard.avgGradePoints()).isEqualTo(4.0);
    }
}
