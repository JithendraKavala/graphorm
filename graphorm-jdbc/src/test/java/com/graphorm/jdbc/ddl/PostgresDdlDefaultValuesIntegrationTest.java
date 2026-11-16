package com.graphorm.jdbc.ddl;

import com.graphorm.core.metadata.DefaultValue;
import com.graphorm.core.metadata.EnumMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostgreSQL DDL Defaults Integration Test")
class PostgresDdlDefaultValuesIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresDdlDefaultValuesIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static final PostgreSqlDialect dialect = new PostgreSqlDialect();

    @BeforeAll
    static void setUpAll() throws Exception {
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
            log.info("Connecting to local PostgreSQL instance on localhost:5432 for DDL Defaults Integration Test...");
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
            stmt.execute("DROP TABLE IF EXISTS defaults_verification_items CASCADE;");
            stmt.execute("DROP TYPE IF EXISTS \"item_status_role\" CASCADE;");
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
    @DisplayName("Should create table with all supported default value types and verify inserted defaults in PostgreSQL")
    void shouldCreateAndVerifyDefaultsInPostgres() throws Exception {
        EnumMetadata roleEnum = EnumMetadata.builder("item_status_role")
                .values("ACTIVE", "INACTIVE")
                .build();

        ModelMetadata model = ModelMetadata.builder("DefaultsVerificationItem")
                .tableName("defaults_verification_items")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).generated(true).defaultValue("autoincrement()").build())
                .addField(FieldMetadata.builder("status", String.class).nullable(false).defaultValue(DefaultValue.ofString("ACTIVE")).build())
                .addField(FieldMetadata.builder("quote", String.class).nullable(false).defaultValue(DefaultValue.ofString("John's")).build())
                .addField(FieldMetadata.builder("empty", String.class).nullable(false).defaultValue(DefaultValue.ofString("")).build())
                .addField(FieldMetadata.builder("count", Integer.class).nullable(false).defaultValue(DefaultValue.ofNumeric("0")).build())
                .addField(FieldMetadata.builder("offset", Integer.class).nullable(false).defaultValue(DefaultValue.ofNumeric("-5")).build())
                .addField(FieldMetadata.builder("price", Double.class).nullable(false).defaultValue(DefaultValue.ofNumeric("9.99")).build())
                .addField(FieldMetadata.builder("active", Boolean.class).nullable(false).defaultValue(DefaultValue.ofBoolean(true)).build())
                .addField(FieldMetadata.builder("inactive", Boolean.class).nullable(false).defaultValue(DefaultValue.ofBoolean(false)).build())
                .addField(FieldMetadata.builder("none", String.class).nullable(true).defaultValue(DefaultValue.ofNull()).build())
                .addField(FieldMetadata.builder("createdAt", java.time.Instant.class).dbColumnName("created_at").nullable(false).defaultValue(DefaultValue.ofExpression("now()")).build())
                .addField(FieldMetadata.builder("updatedAt", java.time.Instant.class).dbColumnName("updated_at").nullable(false).defaultValue(DefaultValue.ofExpression("CURRENT_TIMESTAMP")).build())
                .addField(FieldMetadata.builder("today", java.time.LocalDate.class).nullable(false).defaultValue(DefaultValue.ofExpression("CURRENT_DATE")).build())
                .addField(FieldMetadata.builder("role").typeName("item_status_role").nullable(false).defaultValue(DefaultValue.ofString("ACTIVE")).build())
                .addPrimaryKeyField("id")
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addEnum(roleEnum)
                .addModel(model)
                .build();

        DatabaseSchema schema = DatabaseSchema.fromModelRegistry(registry, dialect);
        TableDefinition table = schema.findTable("defaults_verification_items").orElseThrow();

        String enumSql = dialect.buildCreateEnumSql("item_status_role", List.of("ACTIVE", "INACTIVE"));
        String tableSql = dialect.buildCreateTableSql(table);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(enumSql);
            stmt.execute(tableSql);

            // Insert using database default values
            stmt.execute("INSERT INTO defaults_verification_items (id) VALUES (DEFAULT);");

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM defaults_verification_items WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("id")).isEqualTo(1);
                assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                assertThat(rs.getString("quote")).isEqualTo("John's");
                assertThat(rs.getString("empty")).isEqualTo("");
                assertThat(rs.getInt("count")).isEqualTo(0);
                assertThat(rs.getInt("offset")).isEqualTo(-5);
                assertThat(rs.getDouble("price")).isEqualTo(9.99);
                assertThat(rs.getBoolean("active")).isTrue();
                assertThat(rs.getBoolean("inactive")).isFalse();
                assertThat(rs.getString("none")).isNull();
                assertThat(rs.getTimestamp("created_at")).isNotNull();
                assertThat(rs.getTimestamp("updated_at")).isNotNull();
                assertThat(rs.getDate("today")).isNotNull();
                assertThat(rs.getString("role")).isEqualTo("ACTIVE");
            }
        }
    }
}
