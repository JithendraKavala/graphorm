package com.graphorm.jdbc;

import com.graphorm.core.pagination.CursorCodec;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.ConflictAction;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostgreSQL P1 Query Engine Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresP1QueryIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresP1QueryIntegrationTest.class);

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;

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
            log.info("Starting Testcontainers PostgreSQL container...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_p1_test")
                    .withUsername("test_user")
                    .withPassword("test_password");
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

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS p1_products CASCADE;");
            stmt.execute("""
                CREATE TABLE p1_products (
                    id SERIAL PRIMARY KEY,
                    sku VARCHAR(50) UNIQUE NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    stock INT NOT NULL DEFAULT 0,
                    price NUMERIC(10, 2) NOT NULL
                );
            """);

            stmt.execute("DROP TABLE IF EXISTS p1_events CASCADE;");
            stmt.execute("""
                CREATE TABLE p1_events (
                    id SERIAL PRIMARY KEY,
                    event_type VARCHAR(50) NOT NULL,
                    payload VARCHAR(200) NOT NULL
                );
            """);
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Batch Insert and Keyset Cursor Pagination execution")
    void testBatchInsertAndKeysetPagination() {
        // 1. Batch Insert
        BatchInsertQuery insertQuery = BatchInsertQuery.into("p1_products")
                .columns("sku", "title", "stock", "price")
                .addRow("PROD-001", "Keyboard", 50, 49.99)
                .addRow("PROD-002", "Mouse", 100, 29.99)
                .addRow("PROD-003", "Monitor", 20, 199.99)
                .addRow("PROD-004", "Headset", 35, 79.99)
                .returning("id", "sku", "title")
                .build();

        List<Map<String, Object>> inserted = executor.executeBatchInsertAndReturn(insertQuery);
        assertThat(inserted).hasSize(4);

        // 2. Keyset pagination forward: first 2 items
        SelectQuery page1Query = SelectQuery.from("p1_products")
                .columns("id", "sku", "price")
                .orderBy(OrderByClause.asc("id"))
                .first(2)
                .build();

        List<Map<String, Object>> page1 = executor.queryForList(page1Query);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).get("sku")).isEqualTo("PROD-001");
        assertThat(page1.get(1).get("sku")).isEqualTo("PROD-002");

        // Encode cursor from last item of page 1
        String cursor = CursorCodec.encode(List.of(page1.get(1).get("id")));

        // 3. Keyset pagination: after cursor
        SelectQuery page2Query = SelectQuery.from("p1_products")
                .columns("id", "sku", "price")
                .orderBy(OrderByClause.asc("id"))
                .after(cursor)
                .first(2)
                .build();

        List<Map<String, Object>> page2 = executor.queryForList(page2Query);
        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).get("sku")).isEqualTo("PROD-003");
        assertThat(page2.get(1).get("sku")).isEqualTo("PROD-004");
    }

    @Test
    @Order(3)
    @DisplayName("PostgreSQL Upsert with ON CONFLICT DO UPDATE and DO NOTHING")
    void testPostgresUpsertExecution() {
        // Upsert 1: DO NOTHING on conflict
        UpsertQuery upsertDoNothing = UpsertQuery.into("p1_products")
                .value("sku", "PROD-001")
                .value("title", "Updated Keyboard Should Not Overwrite")
                .value("stock", 999)
                .value("price", 999.99)
                .onConflict("sku")
                .doNothing()
                .build();

        int affected = executor.executeUpsert(upsertDoNothing);
        // On conflict DO NOTHING, affected rows is 0
        assertThat(affected).isEqualTo(0);

        // Verify title remained unchanged
        SelectQuery checkQuery = SelectQuery.from("p1_products")
                .columns("sku", "title", "stock")
                .where(WherePredicate.eq("sku", "PROD-001"))
                .build();
        Map<String, Object> product = executor.queryForObject(checkQuery).orElseThrow();
        assertThat(product.get("title")).isEqualTo("Keyboard");

        // Upsert 2: DO UPDATE on conflict
        UpsertQuery upsertDoUpdate = UpsertQuery.into("p1_products")
                .value("sku", "PROD-001")
                .value("title", "Mechanical Keyboard RGB")
                .value("stock", 65)
                .value("price", 59.99)
                .onConflict("sku")
                .doUpdateSet("title", "Mechanical Keyboard RGB")
                .doUpdateSet("stock", 65)
                .returning("sku", "title", "stock")
                .build();

        Optional<Map<String, Object>> updated = executor.executeUpsertAndReturn(upsertDoUpdate);
        assertThat(updated).isPresent();
        assertThat(updated.get().get("title")).isEqualTo("Mechanical Keyboard RGB");
        assertThat(((Number) updated.get().get("stock")).intValue()).isEqualTo(65);
    }

    @Test
    @Order(2)
    @DisplayName("Bulk update execution via executeBulkUpdate")
    void testBulkUpdate() {
        com.graphorm.core.query.BulkUpdateQuery bulkUpdate = com.graphorm.core.query.BulkUpdateQuery.table("p1_products")
                .value("stock", 120)
                .where(WherePredicate.in("sku", List.of("PROD-002", "PROD-003")))
                .build();

        int rows = executor.executeBulkUpdate(bulkUpdate);
        assertThat(rows).isEqualTo(2);

        SelectQuery verifyQuery = SelectQuery.from("p1_products")
                .columns("stock")
                .where(WherePredicate.eq("sku", "PROD-002"))
                .build();
        Map<String, Object> p2 = executor.queryForObject(verifyQuery).orElseThrow();
        assertThat(((Number) p2.get("stock")).intValue()).isEqualTo(120);
    }

    @Test
    @Order(4)
    @DisplayName("Common Table Expression (WITH clause) execution")
    void testCteExecution() {
        SelectQuery cte = SelectQuery.from("p1_products")
                .columns("id", "title", "stock")
                .where(WherePredicate.gt("stock", 50))
                .build();

        SelectQuery query = SelectQuery.from("high_stock_items")
                .with("high_stock_items", cte)
                .columns("title", "stock")
                .orderBy(OrderByClause.desc("stock"))
                .build();

        List<Map<String, Object>> rows = executor.queryForList(query);
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            assertThat(((Number) row.get("stock")).intValue()).isGreaterThan(50);
        }
    }

    @Test
    @Order(5)
    @DisplayName("PreparedStatement executeBatch execution")
    void testPreparedStatementBatch() {
        String insertSql = "INSERT INTO p1_events (event_type, payload) VALUES (?, ?)";
        List<List<Object>> batches = List.of(
                List.of("LOGIN", "{\"user\":\"u1\"}"),
                List.of("PAGE_VIEW", "{\"path\":\"/home\"}"),
                List.of("LOGOUT", "{\"user\":\"u1\"}")
        );

        int[] results = executor.executeBatch(insertSql, batches);
        assertThat(results).hasSize(3);

        SelectQuery countQuery = SelectQuery.from("p1_events")
                .columns("COUNT(*) as cnt")
                .build();
        Map<String, Object> countRow = executor.queryForObject(countQuery).orElseThrow();
        assertThat(((Number) countRow.get("cnt")).intValue()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(6)
    @DisplayName("Explain Plan hook returns valid JSON EXPLAIN plan from PostgreSQL")
    void testExplainPlanExecution() {
        SelectQuery query = SelectQuery.from("p1_products")
                .columns("id", "title", "price")
                .where(WherePredicate.gt("price", 50.0))
                .build();

        String planJson = executor.explainPlan(query);

        assertThat(planJson).isNotBlank();
        assertThat(planJson).contains("Plan");
        assertThat(planJson).contains("Node Type");
    }
}
