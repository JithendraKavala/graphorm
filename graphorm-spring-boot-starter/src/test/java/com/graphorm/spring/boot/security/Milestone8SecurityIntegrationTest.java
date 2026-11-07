package com.graphorm.spring.boot.security;

import com.graphorm.spring.boot.autoconfigure.GraphOrmAutoConfiguration;
import graphql.ExecutionResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Milestone 8: Security, Performance & Production Readiness Integration Tests (TASK-15.5)")
class Milestone8SecurityIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(Milestone8SecurityIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource sharedDataSource;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @Configuration(proxyBeanMethods = false)
    static class TestMetricsConfiguration {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @BeforeAll
    static void initDatabase() throws Exception {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL container for Milestone 8 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_m8_sec")
                    .withUsername("test_m8")
                    .withPassword("test_m8");
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
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for Milestone 8 tests...");
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
            username = "postgres";
            password = "";

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(jdbcUrl);
            ds.setUser(username);
            ds.setPassword(password);
            sharedDataSource = ds;
        }

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
                        secret_code VARCHAR(100),
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
        SecurityContextHolder.clearContext();
        try (Connection conn = sharedDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE students_boot CASCADE");
            stmt.execute("TRUNCATE TABLE departments_boot CASCADE");
            stmt.execute("ALTER SEQUENCE departments_boot_id_seq RESTART WITH 1");
            stmt.execute("ALTER SEQUENCE students_boot_id_seq RESTART WITH 1");
            stmt.execute("INSERT INTO departments_boot (dept_name) VALUES ('Computer Science'), ('Mathematics')");
            stmt.execute("INSERT INTO students_boot (name, email, secret_code, department_id) VALUES " +
                    "('Ada Lovelace', 'ada@test.com', 'TOP-SECRET-ADA', 1), " +
                    "('Alan Turing', 'alan@test.com', 'TOP-SECRET-ALAN', 1), " +
                    "('Euclid', 'euclid@test.com', 'TOP-SECRET-EUCLID', 2)");
        }
    }

    @AfterEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private ApplicationContextRunner createRunner(String... additionalProperties) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(TestMetricsConfiguration.class)
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
                        "graphorm.schema-location=classpath:security-schema.graphorm",
                        "graphorm.graphql.enabled=true",
                        "graphorm.graphql.auto-fetchers=true",
                        "graphorm.metrics.enabled=true"
                );

        if (additionalProperties != null && additionalProperties.length > 0) {
            runner = runner.withPropertyValues(additionalProperties);
        }
        return runner;
    }

    @Test
    @DisplayName("Blocks queries exceeding max query depth limit (TASK-15.1)")
    void testMaxQueryDepthExceeded() {
        // Configure shallow maxQueryDepth = 2
        createRunner("graphorm.security.max-query-depth=2").run(context -> {
            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);

            // Depth of this query is 3 (students -> department -> students)
            ExecutionResult result = graphQlSource.graphQl().execute("""
                    query DeepQuery {
                        students {
                            department {
                                id
                            }
                        }
                    }
                    """);

            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("depth");
            assertThat(result.getErrors().get(0).getExtensions()).containsEntry("code", "GRAPHQL_DEPTH_LIMIT_EXCEEDED");
        });
    }

    @Test
    @DisplayName("Allows queries within max query depth limit (TASK-15.1)")
    void testQueryWithinDepthLimitAllowed() {
        createRunner("graphorm.security.max-query-depth=5").run(context -> {
            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);

            ExecutionResult result = graphQlSource.graphQl().execute("""
                    query AllowedDepthQuery {
                        students {
                            id
                            name
                            department {
                                id
                                name
                            }
                        }
                    }
                    """);

            assertThat(result.getErrors()).isEmpty();
            Map<String, Object> data = result.getData();
            assertThat(data).containsKey("students");
        });
    }

    @Test
    @DisplayName("Blocks queries exceeding max query complexity limit (TASK-15.1)")
    void testMaxQueryComplexityExceeded() {
        // Configure very low maxQueryComplexity = 2
        createRunner("graphorm.security.max-query-complexity=2").run(context -> {
            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);

            ExecutionResult result = graphQlSource.graphQl().execute("""
                    query ComplexQuery {
                        students {
                            id
                            name
                            email
                        }
                    }
                    """);

            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("complexity");
            assertThat(result.getErrors().get(0).getExtensions()).containsEntry("code", "GRAPHQL_COMPLEXITY_LIMIT_EXCEEDED");
        });
    }

    @Test
    @DisplayName("Rejects access to @auth field when user lacks required role (TASK-15.2)")
    void testFieldAuthorizationDenied() {
        // Authenticate with regular USER role
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        createRunner().run(context -> {
            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);

            ExecutionResult result = graphQlSource.graphQl().execute("""
                    query {
                        students {
                            id
                            name
                            secretCode
                        }
                    }
                    """);

            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("Access denied");
            assertThat(result.getErrors().get(0).getExtensions()).containsEntry("code", "ACCESS_DENIED");
        });
    }

    @Test
    @DisplayName("Allows access to @auth field when user has required ADMIN role (TASK-15.2)")
    void testFieldAuthorizationGranted() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        createRunner().run(context -> {
            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);

            ExecutionResult result = graphQlSource.graphQl().execute("""
                    query {
                        students {
                            id
                            name
                            secretCode
                        }
                    }
                    """);

            assertThat(result.getErrors()).isEmpty();
            Map<String, Object> data = result.getData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
            assertThat(students).hasSize(3);
            assertThat(students).extracting(s -> s.get("secretCode"))
                    .containsExactlyInAnyOrder("TOP-SECRET-ADA", "TOP-SECRET-ALAN", "TOP-SECRET-EUCLID");
        });
    }

    @Test
    @DisplayName("Enforces field-level security on view models with @auth directive (TASK-15.2)")
    void testViewModelFieldAuthorization() {
        // Unauthenticated access
        SecurityContextHolder.clearContext();

        createRunner().run(context -> {
            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);

            ExecutionResult result = graphQlSource.graphQl().execute("""
                    query {
                        studentDashboards {
                            studentId
                            studentName
                            adminNotes
                        }
                    }
                    """);

            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("Access denied");

            // Now authenticate as ADMIN
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
            );

            ExecutionResult adminResult = graphQlSource.graphQl().execute("""
                    query {
                        studentDashboards {
                            studentId
                            studentName
                            adminNotes
                        }
                    }
                    """);

            assertThat(adminResult.getErrors()).isEmpty();
            Map<String, Object> data = adminResult.getData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dashboards = (List<Map<String, Object>>) data.get("studentDashboards");
            assertThat(dashboards).hasSize(3);
            assertThat(dashboards).extracting(d -> d.get("adminNotes"))
                    .containsExactlyInAnyOrder("TOP-SECRET-ADA", "TOP-SECRET-ALAN", "TOP-SECRET-EUCLID");
        });
    }

    @Test
    @DisplayName("Micrometer records query duration metrics during GraphQL execution (TASK-15.4)")
    void testQueryMetricsRecordedDuringExecution() {
        createRunner().run(context -> {
            GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

            ExecutionResult result = graphQlSource.graphQl().execute("""
                    query {
                        students {
                            id
                            name
                        }
                    }
                    """);

            assertThat(result.getErrors()).isEmpty();

            Timer timer = meterRegistry.find("graphorm.query.duration")
                    .tag("queryType", "SELECT")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1);
        });
    }
}
