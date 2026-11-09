package com.graphorm.jdbc.cache;

import com.graphorm.core.cache.CaffeineGraphOrmCacheManager;
import com.graphorm.core.cache.CacheKeyBuilder;
import com.graphorm.core.metadata.CacheMetadata;
import javax.sql.DataSource;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.jdbc.dialect.SqliteDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.transaction.JdbcTransactionManager;
import com.graphorm.jdbc.util.StatementInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("L2 Entity & View Result Cache and Tag Eviction Integration Tests")
class L2CacheAndEvictionTest {

    private static final String DB_URL = "jdbc:sqlite:file:memdb_l2_cache?mode=memory&cache=shared";
    private static Connection keepAliveConnection;

    private CaffeineGraphOrmCacheManager cacheManager;
    private AtomicInteger executionCount;
    private JdbcExecutor executor;
    private GraphOrmClient client;
    private ModelRegistry registry;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        keepAliveConnection = DriverManager.getConnection(DB_URL);
        try (Statement stmt = keepAliveConnection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS departments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL
                );
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE,
                    department_id INTEGER,
                    FOREIGN KEY (department_id) REFERENCES departments(id)
                );
            """);
            stmt.execute("""
                CREATE VIEW IF NOT EXISTS vw_student_dashboard AS
                SELECT s.id, s.name, s.email, d.name AS dept_name
                FROM students s
                LEFT JOIN departments d ON s.department_id = d.id;
            """);
        }
    }

    @AfterAll
    static void tearDownDatabase() throws SQLException {
        if (keepAliveConnection != null && !keepAliveConnection.isClosed()) {
            keepAliveConnection.close();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Statement stmt = keepAliveConnection.createStatement()) {
            stmt.execute("DELETE FROM students;");
            stmt.execute("DELETE FROM departments;");
            stmt.execute("INSERT INTO departments (id, name) VALUES (1, 'Engineering');");
            stmt.execute("INSERT INTO students (id, name, email, department_id) VALUES (1, 'Alice', 'alice@example.com', 1);");
        }

        ModelMetadata studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).dbColumnName("id").build())
                .addField(FieldMetadata.builder("name", String.class).dbColumnName("name").build())
                .addField(FieldMetadata.builder("email", String.class).dbColumnName("email").build())
                .addField(FieldMetadata.builder("department_id", Long.class).dbColumnName("department_id").build())
                .addPrimaryKeyField("id")
                .cacheMetadata(new CacheMetadata(true, Duration.ofSeconds(60), Set.of("Student", "students")))
                .build();

        ViewModelMetadata dashboardView = ViewModelMetadata.builder("StudentDashboard")
                .viewName("vw_student_dashboard")
                .databaseBacked(true)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).dbColumnName("id").build())
                .addField(FieldMetadata.builder("name", String.class).dbColumnName("name").build())
                .addField(FieldMetadata.builder("email", String.class).dbColumnName("email").build())
                .addField(FieldMetadata.builder("dept_name", String.class).dbColumnName("dept_name").build())
                .cacheMetadata(new CacheMetadata(true, Duration.ofSeconds(60), Set.of("Student", "Department")))
                .build();

        registry = ModelRegistry.builder()
                .addModel(studentModel)
                .addViewModel(dashboardView)
                .build();

        cacheManager = new CaffeineGraphOrmCacheManager();
        executionCount = new AtomicInteger(0);

        StatementInterceptor countingInterceptor = new StatementInterceptor() {
            @Override
            public void beforeExecute(String sql, List<Object> parameters) {
                executionCount.incrementAndGet();
            }
        };

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(DB_URL);

        SqliteDialect dialect = new SqliteDialect();
        executor = new JdbcExecutor(
                ds,
                dialect,
                DataSource::getConnection,
                QueryPlanCache.getDefault(),
                cacheManager,
                countingInterceptor
        );

        JdbcTransactionManager txManager = new JdbcTransactionManager(ds);
        client = new GraphOrmClient(executor, registry, dialect, txManager);
    }

    @Test
    @DisplayName("L2 Cache returns cached view query without executing SQL on second read")
    void testL2ViewCacheHit() {
        // First query - cache miss, hits database
        List<Map<String, Object>> firstResult = client.findMany("StudentDashboard");
        assertThat(firstResult).hasSize(1);
        assertThat(firstResult.getFirst()).containsEntry("name", "Alice");
        int countAfterFirst = executionCount.get();
        assertThat(countAfterFirst).isGreaterThanOrEqualTo(1);

        // Second query - cache hit, zero database executions!
        List<Map<String, Object>> secondResult = client.findMany("StudentDashboard");
        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.getFirst()).containsEntry("name", "Alice");
        assertThat(executionCount.get()).isEqualTo(countAfterFirst); // No new DB execution!
    }

    @Test
    @DisplayName("Mutation on Student automatically evicts dependent StudentDashboard view cache")
    void testMutationTagEviction() {
        // Populate cache
        List<Map<String, Object>> firstResult = client.findMany("StudentDashboard");
        assertThat(firstResult).hasSize(1);
        int baselineQueries = executionCount.get();

        // Second read is cached
        List<Map<String, Object>> cachedResult = client.findMany("StudentDashboard");
        assertThat(cachedResult).hasSize(1);
        assertThat(executionCount.get()).isEqualTo(baselineQueries);

        // Update Student -> triggers evictByTags("Student", "students")
        client.update("Student", 1L, Map.of("name", "Alice Wonder"));
        int queriesAfterUpdate = executionCount.get();
        assertThat(queriesAfterUpdate).isGreaterThan(baselineQueries);

        // Third read must be a cache MISS and re-query database to reflect updated name
        List<Map<String, Object>> freshResult = client.findMany("StudentDashboard");
        assertThat(freshResult).hasSize(1);
        assertThat(freshResult.getFirst()).containsEntry("name", "Alice Wonder");
        assertThat(executionCount.get()).isGreaterThan(queriesAfterUpdate);
    }

    @Test
    @DisplayName("Insert on Student evicts view cache so new record appears")
    void testInsertTagEviction() {
        List<Map<String, Object>> initialResult = client.findMany("StudentDashboard");
        assertThat(initialResult).hasSize(1);

        // Insert new student -> evicts "Student" tag
        client.create("Student", Map.of(
                "name", "Bob",
                "email", "bob@example.com",
                "department_id", 1L
        ));

        // View query reflects newly inserted record
        List<Map<String, Object>> updatedResult = client.findMany("StudentDashboard");
        assertThat(updatedResult).hasSize(2);
    }

    @Test
    @DisplayName("Delete on Student evicts view cache so deleted record disappears")
    void testDeleteTagEviction() {
        List<Map<String, Object>> initialResult = client.findMany("StudentDashboard");
        assertThat(initialResult).hasSize(1);

        // Delete student -> evicts "Student" tag
        client.delete("Student", 1L);

        // View query reflects deletion
        List<Map<String, Object>> afterDeleteResult = client.findMany("StudentDashboard");
        assertThat(afterDeleteResult).isEmpty();
    }

    @Test
    @DisplayName("Multi-tenant cache key partitioning isolates data across tenants")
    void testMultiTenantCacheIsolation() {
        String keyA = CacheKeyBuilder.forEntityId("tenant-alpha", "Student", 1L);
        String keyB = CacheKeyBuilder.forEntityId("tenant-beta", "Student", 1L);

        assertThat(keyA).isNotEqualTo(keyB);
        assertThat(keyA).contains("tenant-alpha");
        assertThat(keyB).contains("tenant-beta");

        // Put value under tenant-alpha
        cacheManager.put(keyA, Map.of("id", 1L, "name", "Alpha Student"), Duration.ofMinutes(10), Set.of("Student"));

        // Tenant-beta must see a cache miss
        Optional<Map> betaCached = cacheManager.get(keyB, Map.class);
        assertThat(betaCached).isEmpty();

        // Tenant-alpha sees the cached value
        Optional<Map> alphaCached = cacheManager.get(keyA, Map.class);
        assertThat(alphaCached).isPresent();
        assertThat(alphaCached.get()).containsEntry("name", "Alpha Student");
    }
}
