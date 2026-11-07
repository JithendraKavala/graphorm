package com.graphorm.spring.boot.transaction;

import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.jdbc.transaction.TransactionManager;
import com.graphorm.spring.boot.autoconfigure.GraphOrmAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Milestone 7: Spring Transaction Integration Tests (TASK-13.2, TASK-13.5)")
class GraphOrmTransactionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmTransactionIntegrationTest.class);

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
            log.info("Starting Testcontainers PostgreSQL container for Milestone 7 transaction tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb_m7_tx")
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
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for Milestone 7 transaction tests...");
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
    void cleanTables() throws Exception {
        try (Connection conn = sharedDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE students_boot CASCADE");
            stmt.execute("TRUNCATE TABLE departments_boot CASCADE");
            stmt.execute("INSERT INTO departments_boot (id, dept_name) VALUES (1, 'Engineering')");
        }
    }

    private ApplicationContextRunner createRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        GraphOrmAutoConfiguration.class
                ))
                .withPropertyValues(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "graphorm.schema-location=classpath:schema.graphorm"
                )
                .withUserConfiguration(TransactionalTestConfig.class);
    }

    @Test
    @DisplayName("Declarative @Transactional commits on success")
    void testTransactionalCommit() {
        createRunner().run(context -> {
            StudentService service = context.getBean(StudentService.class);
            GraphOrmClient client = context.getBean(GraphOrmClient.class);

            service.createStudentInTx("Alice", "alice@test.com", 1);

            List<Map<String, Object>> students = client.findMany("Student");
            assertThat(students).hasSize(1);
            assertThat(students.getFirst()).containsEntry("name", "Alice");
        });
    }

    @Test
    @DisplayName("Declarative @Transactional rolls back on uncaught RuntimeException")
    void testTransactionalRollback() {
        createRunner().run(context -> {
            StudentService service = context.getBean(StudentService.class);
            GraphOrmClient client = context.getBean(GraphOrmClient.class);

            assertThatThrownBy(() -> service.createStudentAndFail("Bob", "bob@test.com", 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Simulated failure triggering rollback");

            // Verify rollback: row must not exist
            List<Map<String, Object>> students = client.findMany("Student");
            assertThat(students).isEmpty();
        });
    }

    @Test
    @DisplayName("SpringTransactionManagerAdapter executes programmatic transactions and commits")
    void testProgrammaticTransactionCommit() {
        createRunner().run(context -> {
            TransactionManager txManager = context.getBean(TransactionManager.class);
            GraphOrmClient client = context.getBean(GraphOrmClient.class);

            String result = txManager.executeInTransaction(conn -> {
                client.create("Student", Map.of("name", "Charlie", "email", "charlie@test.com", "departmentId", 1));
                return "committed";
            });

            assertThat(result).isEqualTo("committed");
            List<Map<String, Object>> students = client.findMany("Student");
            assertThat(students).hasSize(1);
            assertThat(students.getFirst()).containsEntry("name", "Charlie");
        });
    }

    @Test
    @DisplayName("SpringTransactionManagerAdapter rolls back on programmatic exception")
    void testProgrammaticTransactionRollback() {
        createRunner().run(context -> {
            TransactionManager txManager = context.getBean(TransactionManager.class);
            GraphOrmClient client = context.getBean(GraphOrmClient.class);

            assertThatThrownBy(() -> txManager.executeInTransaction(conn -> {
                client.create("Student", Map.of("name", "Dave", "email", "dave@test.com", "departmentId", 1));
                throw new IllegalStateException("Programmatic rollback");
            })).isInstanceOf(IllegalStateException.class);

            List<Map<String, Object>> students = client.findMany("Student");
            assertThat(students).isEmpty();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionalTestConfig {

        @Bean
        public StudentService studentService(GraphOrmClient client) {
            return new StudentService(client);
        }
    }

    @Service
    static class StudentService {
        private final GraphOrmClient client;

        StudentService(GraphOrmClient client) {
            this.client = client;
        }

        @Transactional
        public void createStudentInTx(String name, String email, int deptId) {
            client.create("Student", Map.of("name", name, "email", email, "departmentId", deptId));
        }

        @Transactional
        public void createStudentAndFail(String name, String email, int deptId) {
            client.create("Student", Map.of("name", name, "email", email, "departmentId", deptId));
            throw new RuntimeException("Simulated failure triggering rollback");
        }
    }
}
