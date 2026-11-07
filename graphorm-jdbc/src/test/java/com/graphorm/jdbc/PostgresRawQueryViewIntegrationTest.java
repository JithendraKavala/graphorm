package com.graphorm.jdbc;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
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
 * End-to-end integration test suite querying raw SQL subquery view models (Strategy C: @@query)
 * with PostgreSQL, testing UNION ALL queries, pushdown filters, sorting, pagination, and strict read-only enforcement.
 */
@DisplayName("PostgreSQL Raw SQL Subquery View Integration Tests (Strategy C: @@query)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresRawQueryViewIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresRawQueryViewIntegrationTest.class);

    public record UserDirectoryRecord(
            Long id,
            String name,
            String email,
            String role,
            Double score
    ) {}

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;
    private static ModelRegistry registry;
    private static GraphOrmClient client;
    private static ViewModelMetadata userDirectoryView;

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
            log.info("Starting Testcontainers PostgreSQL container for raw query view tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_rawquery")
                    .withUsername("rq_user")
                    .withPassword("rq_pass");
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

        // 1. Create base physical tables
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS students_rq CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS instructors_rq CASCADE;");

            stmt.execute("""
                CREATE TABLE students_rq (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    score DOUBLE PRECISION NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE instructors_rq (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    score DOUBLE PRECISION NOT NULL
                );
            """);

            // Seed Students
            stmt.execute("""
                INSERT INTO students_rq (id, name, email, score) VALUES
                (1, 'Alice Smith', 'alice@test.edu', 3.9),
                (2, 'Bob Jones', 'bob@test.edu', 3.4),
                (3, 'Charlie Brown', 'charlie@test.edu', 3.8);
            """);

            // Seed Instructors
            stmt.execute("""
                INSERT INTO instructors_rq (id, name, email, score) VALUES
                (10, 'Dr. Alan Turing', 'turing@test.edu', 4.0),
                (20, 'Dr. Grace Hopper', 'hopper@test.edu', 4.0);
            """);
        }

        // 2. Parse Schema with Raw Query View
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                model Student {
                    id    Int    @id
                    name  String
                    email String @unique
                    score Float

                    @@map("students_rq")
                }

                model Instructor {
                    id    Int    @id
                    name  String
                    email String @unique
                    score Float

                    @@map("instructors_rq")
                }

                view UserDirectory {
                    id    Int    @id
                    name  String
                    email String
                    role  String
                    score Float

                    @@query(\"\"\"
                        SELECT id, name, email, 'student' AS role, score FROM students_rq
                        UNION ALL
                        SELECT id, name, email, 'instructor' AS role, score FROM instructors_rq
                    \"\"\")
                }
                """;

        registry = SchemaToMetadataTransformer.transform(schema);
        userDirectoryView = registry.getViewModel("UserDirectory");
        client = new GraphOrmClient(executor, registry, dialect);
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. UNION QUERY: Query raw SQL UNION ALL view across multiple underlying tables")
    void shouldQueryAllRowsAcrossUnion() {
        assertThat(userDirectoryView.isRawQuery()).isTrue();
        assertThat(userDirectoryView.isDatabaseBacked()).isFalse();

        SelectQuery query = ViewQueryPlanner.forView(userDirectoryView, registry, dialect)
                .orderByAsc("name")
                .build();

        List<UserDirectoryRecord> records = executor.queryForList(query, UserDirectoryRecord.class);

        // 3 students + 2 instructors = 5 total rows
        assertThat(records).hasSize(5);
        assertThat(records).extracting(UserDirectoryRecord::name)
                .containsExactly("Alice Smith", "Bob Jones", "Charlie Brown", "Dr. Alan Turing", "Dr. Grace Hopper");
        assertThat(records).extracting(UserDirectoryRecord::role)
                .contains("student", "instructor");
    }

    @Test
    @Order(2)
    @DisplayName("2. PUSHDOWN FILTERING: Filter view using WHERE pushdown on derived subquery")
    void shouldApplyPushdownFilterOnUnionQuery() {
        SelectQuery query = ViewQueryPlanner.forView(userDirectoryView, registry, dialect)
                .where(WherePredicate.eq("role", "instructor"))
                .orderByAsc("name")
                .build();

        List<UserDirectoryRecord> instructors = executor.queryForList(query, UserDirectoryRecord.class);

        assertThat(instructors).hasSize(2);
        assertThat(instructors).extracting(UserDirectoryRecord::name)
                .containsExactly("Dr. Alan Turing", "Dr. Grace Hopper");
        assertThat(instructors).allMatch(r -> "instructor".equals(r.role()));
    }

    @Test
    @Order(3)
    @DisplayName("3. NUMERIC RANGE FILTER: Apply pushdown comparison predicate on union view")
    void shouldApplyPushdownFilterWithRange() {
        SelectQuery query = ViewQueryPlanner.forView(userDirectoryView, registry, dialect)
                .where(WherePredicate.gte("score", 3.8))
                .orderByAsc("name")
                .build();

        List<UserDirectoryRecord> highScorers = executor.queryForList(query, UserDirectoryRecord.class);

        // Alice (3.9), Charlie (3.8), Turing (4.0), Hopper (4.0)
        assertThat(highScorers).hasSize(4);
        assertThat(highScorers).extracting(UserDirectoryRecord::name)
                .containsExactly("Alice Smith", "Charlie Brown", "Dr. Alan Turing", "Dr. Grace Hopper");
    }

    @Test
    @Order(4)
    @DisplayName("4. SORTING & PAGINATION: Apply pushdown LIMIT and OFFSET with ORDER BY")
    void shouldApplyPushdownOrderingAndPagination() {
        SelectQuery query = ViewQueryPlanner.forView(userDirectoryView, registry, dialect)
                .orderByAsc("name")
                .limit(2)
                .offset(1)
                .build();

        List<UserDirectoryRecord> page = executor.queryForList(query, UserDirectoryRecord.class);

        // Names sorted: Alice Smith, Bob Jones, Charlie Brown, Dr. Alan Turing, Dr. Grace Hopper
        // Offset 1, Limit 2 => Bob Jones, Charlie Brown
        assertThat(page).hasSize(2);
        assertThat(page).extracting(UserDirectoryRecord::name)
                .containsExactly("Bob Jones", "Charlie Brown");
    }

    @Test
    @Order(5)
    @DisplayName("5. CLIENT QUERY & COUNT: Query through GraphOrmClient with count aggregation")
    void shouldSupportGraphOrmClientOperations() {
        List<Map<String, Object>> results = client.findMany("UserDirectory", WherePredicate.eq("role", "student"));
        assertThat(results).hasSize(3);

        long totalCount = client.count("UserDirectory");
        assertThat(totalCount).isEqualTo(5L);

        long studentCount = client.count("UserDirectory", WherePredicate.eq("role", "student"));
        assertThat(studentCount).isEqualTo(3L);

        long instructorCount = client.count("UserDirectory", WherePredicate.eq("role", "instructor"));
        assertThat(instructorCount).isEqualTo(2L);
    }

    @Test
    @Order(6)
    @DisplayName("6. STRICT READ-ONLY INVARIANT: Mutations on @@query views throw ReadOnlyViewException")
    void shouldEnforceReadOnlyInvariant() {
        assertThatThrownBy(() -> client.create("UserDirectory", Map.of("name", "Fake", "role", "student")))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("UserDirectory");

        assertThatThrownBy(() -> client.update("UserDirectory", 1L, Map.of("name", "Fake")))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("UserDirectory");

        assertThatThrownBy(() -> client.delete("UserDirectory", 1L))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("UserDirectory");
    }
}
