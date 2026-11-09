package com.graphorm.jdbc.integration;

import com.graphorm.core.exception.DuplicateKeyException;
import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqliteDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SQLite In-Memory CRUD & QueryExecutionEngine Integration Tests")
class SqliteInMemoryCrudIntegrationTest {

    private static final String DB_URL = "jdbc:sqlite:file:memdb_crud?mode=memory&cache=shared";
    private static Connection keepAliveConnection;
    private static QueryExecutionEngine engine;
    private static SqliteDialect dialect;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        keepAliveConnection = DriverManager.getConnection(DB_URL);
        try (Statement stmt = keepAliveConnection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS departments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    code TEXT UNIQUE
                );
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE,
                    department_id INTEGER,
                    age INTEGER,
                    active INTEGER DEFAULT 1,
                    FOREIGN KEY (department_id) REFERENCES departments(id)
                );
            """);
        }

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(DB_URL);

        dialect = new SqliteDialect();
        engine = new JdbcExecutor(ds, dialect);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (keepAliveConnection != null && !keepAliveConnection.isClosed()) {
            keepAliveConnection.close();
        }
    }

    @Test
    @DisplayName("Should execute single insert and select one")
    void shouldInsertAndSelectOne() {
        InsertQuery insertDept = InsertQuery.into("departments")
                .value("name", "Computer Science")
                .value("code", "CS")
                .build();
        Optional<Map<String, Object>> insertedDept = engine.executeInsertAndReturn(insertDept);
        assertThat(insertedDept).isPresent();
        Number deptId = (Number) insertedDept.get().get("id");
        assertThat(deptId).isNotNull();
        assertThat(insertedDept.get().get("name")).isEqualTo("Computer Science");

        SelectQuery select = SelectQuery.from("departments")
                .columns("id", "name", "code")
                .where(WherePredicate.eq("id", deptId))
                .limit(1)
                .build();

        Optional<Map<String, Object>> found = engine.executeSelectOne(select);
        assertThat(found).isPresent();
        assertThat(found.get().get("code")).isEqualTo("CS");
    }

    @Test
    @DisplayName("Should execute insert, select with filtering and pagination")
    void shouldInsertAndQueryWithFilteringAndPagination() {
        // Insert department
        Optional<Map<String, Object>> dept = engine.executeInsertAndReturn(
                InsertQuery.into("departments").value("name", "Mathematics").value("code", "MATH").build()
        );
        Number deptId = (Number) dept.orElseThrow().get("id");

        // Insert students
        engine.executeInsert(InsertQuery.into("students")
                .value("name", "Alice")
                .value("email", "alice@test.org")
                .value("department_id", deptId)
                .value("age", 22)
                .build());
        engine.executeInsert(InsertQuery.into("students")
                .value("name", "Bob")
                .value("email", "bob@test.org")
                .value("department_id", deptId)
                .value("age", 25)
                .build());
        engine.executeInsert(InsertQuery.into("students")
                .value("name", "Charlie")
                .value("email", "charlie@test.org")
                .value("department_id", deptId)
                .value("age", 19)
                .build());

        // Select with WHERE filter (department_id = deptId AND age >= 20)
        SelectQuery filterQuery = SelectQuery.from("students")
                .columns("id", "name", "email", "age")
                .where(WherePredicate.and(
                        WherePredicate.eq("department_id", deptId),
                        WherePredicate.gte("age", 20)
                ))
                .orderBy(com.graphorm.core.query.OrderByClause.asc("age"))
                .build();

        List<Map<String, Object>> filtered = engine.executeSelect(filterQuery);
        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).get("name")).isEqualTo("Alice");
        assertThat(filtered.get(1).get("name")).isEqualTo("Bob");

        // Select with Pagination (LIMIT 1 OFFSET 1)
        SelectQuery pagedQuery = SelectQuery.from("students")
                .columns("name")
                .where(WherePredicate.and(
                        WherePredicate.eq("department_id", deptId),
                        WherePredicate.gte("age", 20)
                ))
                .orderBy(com.graphorm.core.query.OrderByClause.asc("age"))
                .limit(1)
                .offset(1)
                .build();

        List<Map<String, Object>> paged = engine.executeSelect(pagedQuery);
        assertThat(paged).hasSize(1);
        assertThat(paged.get(0).get("name")).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Should perform JOIN between students and departments")
    void shouldPerformJoinQuery() {
        Optional<Map<String, Object>> dept = engine.executeInsertAndReturn(
                InsertQuery.into("departments").value("name", "Physics").value("code", "PHYS").build()
        );
        Number deptId = (Number) dept.orElseThrow().get("id");

        engine.executeInsert(InsertQuery.into("students")
                .value("name", "David")
                .value("email", "david@phys.org")
                .value("department_id", deptId)
                .value("age", 21)
                .build());

        SelectQuery joinQuery = SelectQuery.from("students")
                .as("s")
                .column(ProjectedColumn.of("s.name", "student_name"))
                .column(ProjectedColumn.of("d.name", "dept_name"))
                .join(JoinClause.left("departments", "d", WherePredicate.raw("\"d\".\"id\" = \"s\".\"department_id\"")))
                .where(WherePredicate.eq("s.name", "David"))
                .build();

        List<Map<String, Object>> results = engine.executeSelect(joinQuery);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("student_name")).isEqualTo("David");
        assertThat(results.get(0).get("dept_name")).isEqualTo("Physics");
    }

    @Test
    @DisplayName("Should update records and return updated values")
    void shouldUpdateAndReturn() {
        Optional<Map<String, Object>> created = engine.executeInsertAndReturn(
                InsertQuery.into("departments").value("name", "Biology").value("code", "BIO").build()
        );
        Number id = (Number) created.orElseThrow().get("id");

        UpdateQuery update = UpdateQuery.table("departments")
                .set("name", "Biological Sciences")
                .where(WherePredicate.eq("id", id))
                .build();

        Optional<Map<String, Object>> updated = engine.executeUpdateAndReturn(update);
        assertThat(updated).isPresent();
        assertThat(updated.get().get("name")).isEqualTo("Biological Sciences");
    }

    @Test
    @DisplayName("Should batch insert records into SQLite")
    void shouldBatchInsertRecords() {
        Optional<Map<String, Object>> dept = engine.executeInsertAndReturn(
                InsertQuery.into("departments").value("name", "Chemistry").value("code", "CHEM").build()
        );
        Number deptId = (Number) dept.orElseThrow().get("id");

        BatchInsertQuery batchQuery = BatchInsertQuery.into("students")
                .columns("name", "email", "department_id", "age")
                .addRow("Student Batch 1", "batch1@chem.org", deptId, 20)
                .addRow("Student Batch 2", "batch2@chem.org", deptId, 21)
                .addRow("Student Batch 3", "batch3@chem.org", deptId, 22)
                .build();

        int insertedCount = engine.executeBatchInsert(batchQuery);
        assertThat(insertedCount).isEqualTo(3);

        SelectQuery countQuery = SelectQuery.from("students")
                .columns("id")
                .where(WherePredicate.in("email", List.of("batch1@chem.org", "batch2@chem.org", "batch3@chem.org")))
                .build();

        List<Map<String, Object>> inserted = engine.executeSelect(countQuery);
        assertThat(inserted).hasSize(3);
    }

    @Test
    @DisplayName("Should delete record by predicate")
    void shouldDeleteRecord() {
        Optional<Map<String, Object>> dept = engine.executeInsertAndReturn(
                InsertQuery.into("departments").value("name", "Economics").value("code", "ECON").build()
        );
        Number id = (Number) dept.orElseThrow().get("id");

        DeleteQuery deleteQuery = DeleteQuery.from("departments")
                .where(WherePredicate.eq("id", id))
                .build();
        int affected = engine.executeDelete(deleteQuery);
        assertThat(affected).isEqualTo(1);

        Optional<Map<String, Object>> found = engine.executeSelectOne(
                SelectQuery.from("departments").columns("id").where(WherePredicate.eq("id", id)).build()
        );
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should translate duplicate key constraint error into UniqueConstraintViolationException")
    void shouldTranslateUniqueConstraintViolation() {
        engine.executeInsert(InsertQuery.into("departments").value("name", "History").value("code", "HIST").build());

        InsertQuery duplicate = InsertQuery.into("departments").value("name", "History Duplicate").value("code", "HIST").build();
        assertThatThrownBy(() -> engine.executeInsert(duplicate))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("UNIQUE constraint failed");
    }

    @Test
    @DisplayName("Should execute asynchronously via CompletableFuture")
    void shouldExecuteAsynchronously() throws Exception {
        SelectQuery query = SelectQuery.from("departments").columns("id", "name").limit(5).build();
        CompletableFuture<List<Map<String, Object>>> future = engine.executeSelectAsync(query);

        List<Map<String, Object>> results = future.get();
        assertThat(results).isNotNull();
    }
}
