package com.graphorm.jdbc.migration;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.jdbc.ddl.diff.SchemaDiff;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real integration test for Schema Lifecycle and Migration Engine against PostgreSQL.
 * Uses Testcontainers if Docker is available, otherwise connects to local PostgreSQL.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresMigrationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresMigrationIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static PostgreSqlDialect dialect;
    private static MigrationManager manager;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        dialect = new PostgreSqlDialect();
        manager = new MigrationManager(dialect);

        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL 16 container...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_test")
                    .withUsername("test_user")
                    .withPassword("test_password");
            container.start();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(container.getJdbcUrl());
            ds.setUser(container.getUsername());
            ds.setPassword(container.getPassword());
            dataSource = ds;
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for Migration Integration Tests...");
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerNames(new String[]{"localhost"});
            ds.setPortNumbers(new int[]{5432});
            ds.setUser("postgres");
            ds.setPassword("");
            ds.setDatabaseName("postgres");
            dataSource = ds;
        }

        cleanTestEnvironment();
    }

    private static void cleanTestEnvironment() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS _graphorm_migrations CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS migration_posts CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS migration_users CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS tamper_test CASCADE;");
            stmt.execute("DROP TYPE IF EXISTS \"UserRole\" CASCADE;");
        }
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        try {
            cleanTestEnvironment();
        } catch (Exception ignored) {
        }
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Should apply migrations in order and update ledger table")
    void shouldApplyMigrationsInOrder() throws Exception {
        Path migrationsDir = tempDir.resolve("migrations_order");
        Files.createDirectories(migrationsDir);

        // Migration 1: Create enum and users table
        String m1 = """
                CREATE TYPE "UserRole" AS ENUM ('ADMIN', 'USER');
                CREATE TABLE migration_users (
                    id SERIAL PRIMARY KEY,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    role "UserRole" NOT NULL DEFAULT 'USER'
                );
                """;
        Files.writeString(migrationsDir.resolve("0001_create_users.sql"), m1);

        // Migration 2: Create posts table with foreign key
        String m2 = """
                CREATE TABLE migration_posts (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES migration_users(id) ON DELETE CASCADE,
                    title VARCHAR(255) NOT NULL
                );
                """;
        Files.writeString(migrationsDir.resolve("0002_create_posts.sql"), m2);

        try (Connection conn = dataSource.getConnection()) {
            // Check status before applying
            MigrationStatus status = manager.status(migrationsDir, conn);
            assertThat(status.isUpToDate()).isFalse();
            assertThat(status.pendingMigrations()).hasSize(2);
            assertThat(status.appliedMigrations()).isEmpty();

            // Apply migrations
            MigrationResult result = manager.apply(migrationsDir, conn);
            assertThat(result.appliedMigrationNames()).containsExactly("0001_create_users", "0002_create_posts");

            // Status after applying
            MigrationStatus statusAfter = manager.status(migrationsDir, conn);
            assertThat(statusAfter.isUpToDate()).isTrue();
            assertThat(statusAfter.pendingMigrations()).isEmpty();
            assertThat(statusAfter.appliedMigrations()).hasSize(2);

            // Verify tables and types actually exist in Postgres
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO migration_users (email, role) VALUES ('alice@example.com', 'ADMIN');");
                try (ResultSet rs = stmt.executeQuery("SELECT role FROM migration_users WHERE email = 'alice@example.com'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("role")).isEqualTo("ADMIN");
                }
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. Should detect migration checksum tampering and reject execution")
    void shouldDetectTampering() throws Exception {
        Path migrationsDir = tempDir.resolve("migrations_tamper");
        Files.createDirectories(migrationsDir);

        String initialSql = "CREATE TABLE tamper_test (id SERIAL PRIMARY KEY);";
        Path mFile = migrationsDir.resolve("0001_tamper.sql");
        Files.writeString(mFile, initialSql);

        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS tamper_test CASCADE;");
            }

            // Apply initial
            MigrationResult result = manager.apply(migrationsDir, conn);
            assertThat(result.appliedMigrationNames()).containsExactly("0001_tamper");

            // Tamper file on disk!
            Files.writeString(mFile, "CREATE TABLE tamper_test (id SERIAL PRIMARY KEY, tampered_col TEXT);");

            // Status should detect tampering
            MigrationStatus status = manager.status(migrationsDir, conn);
            assertThat(status.hasTampered()).isTrue();
            assertThat(status.tamperedMigrations()).hasSize(1);
            assertThat(status.tamperedMigrations().get(0).migrationName()).isEqualTo("0001_tamper");

            // Apply must abort with MigrationException
            assertThatThrownBy(() -> manager.apply(migrationsDir, conn))
                    .isInstanceOf(MigrationException.class)
                    .hasMessageContaining("Migration checksum mismatch");
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. Should introspect PostgreSQL catalog and detect schema drift")
    void shouldDetectSchemaDrift() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Live DB currently has migration_users (id, email, role)
            // Define ModelRegistry with an extra column 'nickname' that does not exist in DB
            ModelMetadata expectedUser = ModelMetadata.builder("User")
                    .tableName("migration_users")
                    .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).generated(true).build())
                    .addField(FieldMetadata.builder("email", String.class).nullable(false).unique(true).build())
                    .addField(FieldMetadata.builder("role").typeName("UserRole").nullable(false).build())
                    .addField(FieldMetadata.builder("nickname", String.class).nullable(true).build()) // DRIFT!
                    .addPrimaryKeyField("id")
                    .build();

            ModelRegistry registry = ModelRegistry.builder()
                    .addModel(expectedUser)
                    .build();

            SchemaDiff drift = manager.verify(registry, conn, "public");

            assertThat(drift.isEmpty()).isFalse();
            // Should detect that table 'migration_users' has column difference
            assertThat(drift.alteredTables()).anyMatch(t ->
                    t.tableName().equalsIgnoreCase("migration_users") &&
                    t.addedColumns().stream().anyMatch(c -> c.name().equalsIgnoreCase("nickname"))
            );
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. Should acquire and release PostgreSQL advisory locks correctly")
    void shouldAcquireAndReleaseAdvisoryLock() throws Exception {
        AdvisoryLockManager lockMgr = new AdvisoryLockManager(dialect);

        try (Connection conn = dataSource.getConnection()) {
            String res = lockMgr.withLock(conn, () -> {
                // Verify lock is active by inspecting pg_locks
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT objid FROM pg_locks WHERE locktype = 'advisory' AND objid = " + AdvisoryLockManager.DEFAULT_LOCK_KEY)) {
                    assertThat(rs.next()).isTrue();
                }
                return "SUCCESS";
            });

            assertThat(res).isEqualTo("SUCCESS");

            // Verify lock has been released
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT objid FROM pg_locks WHERE locktype = 'advisory' AND objid = " + AdvisoryLockManager.DEFAULT_LOCK_KEY)) {
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
