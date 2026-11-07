package com.graphorm.spring.boot.graphql;

import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.spring.boot.autoconfigure.GraphOrmAutoConfiguration;
import graphql.ExecutionResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Milestone 7: Spring for GraphQL AutoConfiguration Integration Tests (TASK-13.4, TASK-13.5)")
class GraphOrmGraphQlAutoConfigurationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmGraphQlAutoConfigurationIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource sharedDataSource;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @BeforeAll
    static void initDatabase() throws Exception {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL container for Milestone 7 GraphQL tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_m7_gql")
                    .withUsername("test_m7")
                    .withPassword("test_m7");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(jdbcUrl);
            ds.setUser(username);
            ds.setPassword(password);
            sharedDataSource = ds;
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for Milestone 7 GraphQL tests...");
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
            username = "postgres";
            password = "";

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(jdbcUrl);
            ds.setUser(username);
            ds.setPassword(password);
            sharedDataSource = ds;
        }

        // Schema setup
        try (Connection conn = sharedDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS students_boot CASCADE");
            stmt.execute("DROP TABLE IF EXISTS departments_boot CASCADE");

            stmt.execute("""
                    CREATE TABLE departments_boot (
                        id SERIAL PRIMARY KEY,
                        dept_name VARCHAR(100) NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE students_boot (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        email VARCHAR(100) UNIQUE NOT NULL,
                        department_id INT REFERENCES departments_boot(id)
                    )
                    """);
        }
    }

    @AfterAll
    static void tearDown() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @BeforeEach
    void seedData() throws Exception {
        try (Connection conn = sharedDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE students_boot CASCADE");
            stmt.execute("TRUNCATE TABLE departments_boot CASCADE");
            stmt.execute("ALTER SEQUENCE departments_boot_id_seq RESTART WITH 1");
            stmt.execute("ALTER SEQUENCE students_boot_id_seq RESTART WITH 1");
            stmt.execute("INSERT INTO departments_boot (dept_name) VALUES ('Computer Science'), ('Mathematics')");
            stmt.execute("INSERT INTO students_boot (name, email, department_id) VALUES ('Ada Lovelace', 'ada@test.com', 1), ('Alan Turing', 'alan@test.com', 1), ('Euclid', 'euclid@test.com', 2)");
        }
    }

    private ApplicationContextRunner createRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        GraphQlAutoConfiguration.class,
                        GraphOrmAutoConfiguration.class
                ))
                .withPropertyValues(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "graphorm.schema-location=classpath:schema.graphorm",
                        "graphorm.graphql.enabled=true",
                        "graphorm.graphql.auto-fetchers=true"
                );
    }

    @Test
    @DisplayName("Auto-wires GraphQlSource with generic Model and View DataFetchers")
    void testGraphQlSourceAutoWiring() {
        createRunner().run(context -> {
            assertThat(context).hasSingleBean(GraphQlSource.class);
            assertThat(context).hasSingleBean(ExecutionGraphQlService.class);

            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);
            assertThat(graphQlSource.schema().getObjectType("Student")).isNotNull();
            assertThat(graphQlSource.schema().getObjectType("Department")).isNotNull();
            assertThat(graphQlSource.schema().getObjectType("StudentDashboard")).isNotNull();

            // Execute Query.students via GraphQlSource directly
            ExecutionResult queryResult = graphQlSource.graphQl().execute("""
                    query {
                        students {
                            id
                            name
                            email
                        }
                    }
                    """);

            assertThat(queryResult.getErrors()).isEmpty();
            Map<String, Object> data = queryResult.getData();
            assertThat(data).containsKey("students");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
            assertThat(students).hasSize(3);
            assertThat(students).extracting(m -> m.get("name"))
                    .containsExactlyInAnyOrder("Ada Lovelace", "Alan Turing", "Euclid");
        });
    }

    @Test
    @DisplayName("Executes nested relationship query with DataLoader batching via ExecutionGraphQlService")
    void testNestedRelationshipQuery() {
        createRunner().run(context -> {
            ExecutionGraphQlService service = context.getBean(ExecutionGraphQlService.class);

            ExecutionGraphQlRequest request = new DefaultExecutionGraphQlRequest(
                    """
                    query {
                        students {
                            name
                            department {
                                id
                                name
                            }
                        }
                    }
                    """,
                    null,
                    Collections.emptyMap(),
                    null,
                    "test-req-1",
                    null
            );

            ExecutionGraphQlResponse response = service.execute(request).block();
            assertThat(response).isNotNull();
            assertThat(response.getErrors()).isEmpty();

            Map<String, Object> data = response.getData();
            assertThat(data).containsKey("students");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
            assertThat(students).hasSize(3);

            for (Map<String, Object> s : students) {
                assertThat(s.get("department")).isNotNull();
            }
        });
    }

    @Test
    @DisplayName("Executes view query for virtual joined view model")
    void testViewQueryExecution() {
        createRunner().run(context -> {
            ExecutionGraphQlService service = context.getBean(ExecutionGraphQlService.class);

            ExecutionGraphQlRequest request = new DefaultExecutionGraphQlRequest(
                    """
                    query {
                        studentDashboards {
                            studentId
                            studentName
                            departmentName
                        }
                    }
                    """,
                    null,
                    Collections.emptyMap(),
                    null,
                    "test-req-2",
                    null
            );

            ExecutionGraphQlResponse response = service.execute(request).block();
            assertThat(response).isNotNull();
            assertThat(response.getErrors()).isEmpty();

            Map<String, Object> data = response.getData();
            assertThat(data).containsKey("studentDashboards");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dashboards = (List<Map<String, Object>>) data.get("studentDashboards");
            assertThat(dashboards).hasSize(3);
            assertThat(dashboards).extracting(m -> m.get("departmentName"))
                    .contains("Computer Science", "Mathematics");
        });
    }

    @Test
    @DisplayName("Executes model mutation to create a new student via ExecutionGraphQlService")
    void testModelMutationExecution() {
        createRunner().run(context -> {
            ExecutionGraphQlService service = context.getBean(ExecutionGraphQlService.class);

            ExecutionGraphQlRequest request = new DefaultExecutionGraphQlRequest(
                    """
                    mutation {
                        createStudent(input: { name: "Grace Hopper", email: "grace@test.com", departmentId: 1 }) {
                            id
                            name
                            email
                        }
                    }
                    """,
                    null,
                    Collections.emptyMap(),
                    null,
                    "test-req-3",
                    null
            );

            ExecutionGraphQlResponse response = service.execute(request).block();
            assertThat(response).isNotNull();
            assertThat(response.getErrors()).isEmpty();

            Map<String, Object> data = response.getData();
            assertThat(data).containsKey("createStudent");
            @SuppressWarnings("unchecked")
            Map<String, Object> created = (Map<String, Object>) data.get("createStudent");
            assertThat(created.get("name")).isEqualTo("Grace Hopper");
            assertThat(created.get("email")).isEqualTo("grace@test.com");

            // Verify in database via GraphOrmClient
            GraphOrmClient client = context.getBean(GraphOrmClient.class);
            List<Map<String, Object>> students = client.findMany("Student");
            assertThat(students).hasSize(4);
        });
    }
}
