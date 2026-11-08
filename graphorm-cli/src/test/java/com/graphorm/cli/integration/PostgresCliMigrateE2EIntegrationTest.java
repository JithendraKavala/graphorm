package com.graphorm.cli.integration;

import com.graphorm.cli.GraphOrmCli;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostgreSQL CLI Migrate E2E Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresCliMigrateE2EIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresCliMigrateE2EIntegrationTest.class);
    private static final String TEST_SCHEMA = "cli_migrate_test";

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @TempDir
    static Path tempDir;

    private static Path schemaFile;
    private static Path migrationsDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_test")
                    .withUsername("test_user")
                    .withPassword("test_password");
            container.start();

            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(jdbcUrl);
            ds.setUser(username);
            ds.setPassword(password);
            dataSource = ds;
        } else {
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
            username = "postgres";
            password = "";

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerNames(new String[]{"localhost"});
            ds.setPortNumbers(new int[]{5432});
            ds.setUser(username);
            ds.setPassword(password);
            ds.setDatabaseName("postgres");
            dataSource = ds;
        }

        cleanDatabase();

        schemaFile = tempDir.resolve("schema.graphorm");
        migrationsDir = tempDir.resolve("migrations");
        Files.createDirectories(migrationsDir);

        String schemaDsl = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                generator client {
                  provider = "graphorm-client-java"
                }

                model Author {
                  id        Int      @id @default(autoincrement())
                  name      String
                  email     String   @unique
                  articles  Article[]

                  @@map("cli_authors")
                }

                model Article {
                  id        Int      @id @default(autoincrement())
                  title     String
                  authorId  Int      @map("author_id")
                  author    Author   @relation(fields: [authorId], references: [id], onDelete: Cascade)

                  @@map("cli_articles")
                }
                """;
        Files.writeString(schemaFile, schemaDsl);
    }

    private static void cleanDatabase() {
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS " + TEST_SCHEMA + " CASCADE;");
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + TEST_SCHEMA + ";");
            } catch (Exception ignored) {}
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS " + TEST_SCHEMA + " CASCADE;");
            } catch (Exception ignored) {}
        }
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. graphorm migrate create init -> generates migration script on disk")
    void shouldCreateMigration() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "migrate", "create", "init",
                "-s", schemaFile.toString(),
                "-d", migrationsDir.toString(),
                "--url", jdbcUrl,
                "-u", username,
                "-p", password,
                "--db-schema", TEST_SCHEMA);

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Generated migration:");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("2. graphorm migrate status -> reports 1 pending migration")
    void shouldReportPendingStatus() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "migrate", "status",
                "-d", migrationsDir.toString(),
                "--url", jdbcUrl,
                "-u", username,
                "-p", password,
                "--db-schema", TEST_SCHEMA);

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Pending: 1");
        assertThat(out.toString()).contains("Applied: 0");
    }

    @Test
    @Order(3)
    @DisplayName("3. graphorm migrate apply -> executes migration and records in ledger")
    void shouldApplyMigration() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "migrate", "apply",
                "-d", migrationsDir.toString(),
                "--url", jdbcUrl,
                "-u", username,
                "-p", password,
                "--db-schema", TEST_SCHEMA);

        assertThat(err.toString()).isEmpty();
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Applied 1 migration(s)");
    }

    @Test
    @Order(4)
    @DisplayName("4. graphorm migrate status -> reports 0 pending, 1 applied")
    void shouldReportAppliedStatus() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "migrate", "status",
                "-d", migrationsDir.toString(),
                "--url", jdbcUrl,
                "-u", username,
                "-p", password,
                "--db-schema", TEST_SCHEMA);

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Pending: 0");
        assertThat(out.toString()).contains("Applied: 1");
    }

    @Test
    @Order(5)
    @DisplayName("5. graphorm migrate verify -> reports schema is in sync (no drift)")
    void shouldVerifyNoDrift() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "migrate", "verify",
                "-s", schemaFile.toString(),
                "--url", jdbcUrl,
                "-u", username,
                "-p", password,
                "--db-schema", TEST_SCHEMA);

        assertThat(err.toString()).isEmpty();
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("No drift detected");
    }

    @Test
    @Order(6)
    @DisplayName("6. graphorm migrate verify -> detects schema drift when table modified out-of-band")
    void shouldDetectDriftWhenModifiedOutOfBand() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + TEST_SCHEMA + ".cli_authors ADD COLUMN out_of_band_col VARCHAR(100);");
        }

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "migrate", "verify",
                "-s", schemaFile.toString(),
                "--url", jdbcUrl,
                "-u", username,
                "-p", password,
                "--db-schema", TEST_SCHEMA);

        assertThat(exitCode).isEqualTo(2); // exitCode 2 indicates drift detected
        assertThat(err.toString()).contains("Schema drift detected");
        assertThat(err.toString()).contains("cli_authors");
    }

    @Test
    @Order(7)
    @DisplayName("7. graphorm migrate diff -> outputs SQL DDL statements to rectify drift")
    void shouldGenerateDiffSql() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "migrate", "diff",
                "-s", schemaFile.toString(),
                "--url", jdbcUrl,
                "-u", username,
                "-p", password,
                "--db-schema", TEST_SCHEMA);

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("ALTER TABLE \"cli_authors\" DROP COLUMN IF EXISTS \"out_of_band_col\"");
    }
}
