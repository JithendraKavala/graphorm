package com.graphorm.jdbc.executor;

import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.exception.QueryExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JdbcExecutor Unit & Resource Management Tests")
class JdbcExecutorTest {

    record SimpleUser(Long id, String name, String email) {}

    private MockDbState mockDbState;
    private DataSource mockDataSource;
    private JdbcExecutor executor;

    @BeforeEach
    void setUp() {
        mockDbState = new MockDbState();
        mockDataSource = createMockDataSource(mockDbState);
        executor = new JdbcExecutor(mockDataSource, new PostgreSqlDialect());
    }

    @Nested
    @DisplayName("Query Execution Tests")
    class QueryExecutionTests {

        @Test
        @DisplayName("Should execute queryForList and map rows to Map")
        void shouldQueryForListOfMaps() {
            mockDbState.stubRows = List.of(
                    Map.of("id", 1L, "name", "Alice", "email", "alice@example.com"),
                    Map.of("id", 2L, "name", "Bob", "email", "bob@example.com")
            );

            SelectQuery query = SelectQuery.from("users").build();
            List<Map<String, Object>> rows = executor.queryForList(query);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("name", "Alice");
            assertThat(rows.get(1)).containsEntry("name", "Bob");

            assertThat(mockDbState.connectionClosed.get()).isTrue();
            assertThat(mockDbState.statementClosed.get()).isTrue();
            assertThat(mockDbState.resultSetClosed.get()).isTrue();
        }

        @Test
        @DisplayName("Should execute queryForList and map rows to Java records")
        void shouldQueryForListOfRecords() {
            mockDbState.stubRows = List.of(
                    Map.of("id", 10L, "name", "Charlie", "email", "charlie@example.com")
            );

            BoundSql boundSql = BoundSql.of("SELECT id, name, email FROM users WHERE id = ?", List.of(10L));
            List<SimpleUser> users = executor.queryForList(boundSql, SimpleUser.class);

            assertThat(users).hasSize(1);
            assertThat(users.getFirst()).isEqualTo(new SimpleUser(10L, "Charlie", "charlie@example.com"));
        }

        @Test
        @DisplayName("Should execute queryForObject returning single item")
        void shouldQueryForSingleObject() {
            mockDbState.stubRows = List.of(
                    Map.of("id", 5L, "name", "Diana", "email", "diana@example.com")
            );

            Optional<SimpleUser> user = executor.queryForObject(
                    SelectQuery.from("users").where(WherePredicate.eq("id", 5L)).build(),
                    SimpleUser.class
            );

            assertThat(user).isPresent();
            assertThat(user.get().name()).isEqualTo("Diana");
        }

        @Test
        @DisplayName("Should return Optional.empty when queryForObject finds zero rows")
        void shouldReturnEmptyWhenNotFound() {
            mockDbState.stubRows = List.of();

            Optional<SimpleUser> user = executor.queryForObject(
                    SelectQuery.from("users").where(WherePredicate.eq("id", 999L)).build(),
                    SimpleUser.class
            );

            assertThat(user).isEmpty();
        }

        @Test
        @DisplayName("Should throw QueryExecutionException when queryForObject returns more than one row")
        void shouldThrowWhenMultipleRowsReturned() {
            mockDbState.stubRows = List.of(
                    Map.of("id", 1L, "name", "A"),
                    Map.of("id", 2L, "name", "B")
            );

            assertThatThrownBy(() -> executor.queryForObject(BoundSql.of("SELECT * FROM users")))
                    .isInstanceOf(QueryExecutionException.class)
                    .hasMessageContaining("Expected single row result but found 2");
        }
    }

    @Nested
    @DisplayName("Mutation Execution Tests")
    class MutationExecutionTests {

        @Test
        @DisplayName("Should execute INSERT update query and bind parameters")
        void shouldExecuteInsert() {
            mockDbState.affectedRows = 1;

            InsertQuery query = InsertQuery.into("users")
                    .value("name", "Eve")
                    .value("email", "eve@example.com")
                    .build();

            int rows = executor.executeUpdate(query);
            assertThat(rows).isEqualTo(1);
            assertThat(mockDbState.boundParams).containsExactly("Eve", "eve@example.com");

            assertThat(mockDbState.connectionClosed.get()).isTrue();
            assertThat(mockDbState.statementClosed.get()).isTrue();
        }

        @Test
        @DisplayName("Should execute UPDATE query and bind parameters in order")
        void shouldExecuteUpdate() {
            mockDbState.affectedRows = 1;

            UpdateQuery query = UpdateQuery.table("users")
                    .set("name", "Eve Updated")
                    .where(WherePredicate.eq("id", 1L))
                    .build();

            int rows = executor.executeUpdate(query);
            assertThat(rows).isEqualTo(1);
            assertThat(mockDbState.boundParams).containsExactly("Eve Updated", 1L);
        }

        @Test
        @DisplayName("Should execute DELETE query and bind parameters")
        void shouldExecuteDelete() {
            mockDbState.affectedRows = 1;

            DeleteQuery query = DeleteQuery.from("users")
                    .where(WherePredicate.eq("id", 1L))
                    .build();

            int rows = executor.executeUpdate(query);
            assertThat(rows).isEqualTo(1);
            assertThat(mockDbState.boundParams).containsExactly(1L);
        }

        @Test
        @DisplayName("Should execute insert and return mapped record")
        void shouldExecuteInsertAndReturn() {
            mockDbState.stubRows = List.of(
                    Map.of("id", 77L, "name", "Frank", "email", "frank@example.com")
            );

            InsertQuery query = InsertQuery.into("users")
                    .value("name", "Frank")
                    .value("email", "frank@example.com")
                    .build();

            Optional<SimpleUser> inserted = executor.executeInsertAndReturn(query, SimpleUser.class);
            assertThat(inserted).isPresent();
            assertThat(inserted.get().id()).isEqualTo(77L);
            assertThat(inserted.get().name()).isEqualTo("Frank");
        }
    }

    @Nested
    @DisplayName("Parameter Binding & Error Handling Tests")
    class ParameterBindingAndErrorTests {

        @Test
        @DisplayName("Should bind various types including null, UUID, LocalDate, LocalDateTime, and Enum")
        void shouldBindVariousTypes() {
            mockDbState.stubRows = List.of(Map.of("cnt", 1));

            UUID uuid = UUID.randomUUID();
            LocalDate date = LocalDate.of(2024, 6, 1);
            LocalDateTime dt = LocalDateTime.of(2024, 6, 1, 10, 0, 0);

            BoundSql boundSql = BoundSql.of(
                    "SELECT 1 WHERE col1 = ? AND col2 = ? AND col3 = ? AND col4 = ? AND col5 = ?",
                    List.of("text", uuid, date, dt, com.graphorm.jdbc.dialect.AggregateType.COUNT)
            );

            executor.queryForList(boundSql);

            assertThat(mockDbState.boundParams).hasSize(5);
            assertThat(mockDbState.boundParams.get(0)).isEqualTo("text");
            assertThat(mockDbState.boundParams.get(1)).isEqualTo(uuid);
            assertThat(mockDbState.boundParams.get(4)).isEqualTo("COUNT");
        }

        @Test
        @DisplayName("Should wrap SQLException into QueryExecutionException with SQL context")
        void shouldWrapSqlException() {
            mockDbState.throwOnExecute = true;

            BoundSql boundSql = BoundSql.of("SELECT * FROM invalid_table");
            assertThatThrownBy(() -> executor.queryForList(boundSql))
                    .isInstanceOf(QueryExecutionException.class)
                    .hasMessageContaining("Simulated DB error")
                    .satisfies(ex -> {
                        QueryExecutionException qe = (QueryExecutionException) ex;
                        assertThat(qe.sql()).isEqualTo("SELECT * FROM invalid_table");
                    });

            assertThat(mockDbState.connectionClosed.get()).isTrue();
            assertThat(mockDbState.statementClosed.get()).isTrue();
        }
    }

    // --- Mock Database State & Helpers ---

    private static class MockDbState {
        List<Map<String, Object>> stubRows = List.of();
        int affectedRows = 0;
        boolean throwOnExecute = false;
        List<Object> boundParams = new ArrayList<>();
        AtomicBoolean connectionClosed = new AtomicBoolean(false);
        AtomicBoolean statementClosed = new AtomicBoolean(false);
        AtomicBoolean resultSetClosed = new AtomicBoolean(false);
    }

    private static DataSource createMockDataSource(MockDbState state) {
        return (DataSource) Proxy.newProxyInstance(
                JdbcExecutorTest.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return createMockConnection(state);
                    }
                    return null;
                }
        );
    }

    private static Connection createMockConnection(MockDbState state) {
        return (Connection) Proxy.newProxyInstance(
                JdbcExecutorTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("prepareStatement".equals(method.getName())) {
                        return createMockPreparedStatement(state, (String) args[0]);
                    }
                    if ("close".equals(methodName)) {
                        state.connectionClosed.set(true);
                        return null;
                    }
                    if ("setAutoCommit".equals(methodName) || "getAutoCommit".equals(methodName)) {
                        return true;
                    }
                    return null;
                }
        );
    }

    private static PreparedStatement createMockPreparedStatement(MockDbState state, String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(
                JdbcExecutorTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("setNull".equals(methodName)) {
                        state.boundParams.add(null);
                        return null;
                    }
                    if ("setObject".equals(methodName) || "setString".equals(methodName) ||
                            "setDate".equals(methodName) || "setTime".equals(methodName) ||
                            "setTimestamp".equals(methodName)) {
                        state.boundParams.add(args[1]);
                        return null;
                    }
                    if ("executeQuery".equals(methodName)) {
                        if (state.throwOnExecute) {
                            throw new SQLException("Simulated DB error");
                        }
                        return createMockResultSet(state);
                    }
                    if ("executeUpdate".equals(methodName)) {
                        if (state.throwOnExecute) {
                            throw new SQLException("Simulated DB error");
                        }
                        return state.affectedRows;
                    }
                    if ("close".equals(methodName)) {
                        state.statementClosed.set(true);
                        return null;
                    }
                    return null;
                }
        );
    }

    private static ResultSet createMockResultSet(MockDbState state) {
        AtomicInteger cursor = new AtomicInteger(-1);

        ResultSetMetaData meta = (ResultSetMetaData) Proxy.newProxyInstance(
                JdbcExecutorTest.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class},
                (proxy, method, args) -> {
                    if ("getColumnCount".equals(method.getName())) {
                        return state.stubRows.isEmpty() ? 0 : state.stubRows.getFirst().size();
                    }
                    if ("getColumnLabel".equals(method.getName()) || "getColumnName".equals(method.getName())) {
                        int index = (Integer) args[0] - 1;
                        return new ArrayList<>(state.stubRows.getFirst().keySet()).get(index);
                    }
                    return null;
                }
        );

        return (ResultSet) Proxy.newProxyInstance(
                JdbcExecutorTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("next".equals(methodName)) {
                        int next = cursor.incrementAndGet();
                        return next < state.stubRows.size();
                    }
                    if ("getMetaData".equals(methodName)) {
                        return meta;
                    }
                    if ("getObject".equals(methodName)) {
                        Map<String, Object> currentRow = state.stubRows.get(cursor.get());
                        if (args[0] instanceof Integer idx) {
                            return new ArrayList<>(currentRow.values()).get(idx - 1);
                        }
                        return currentRow.get(args[0]);
                    }
                    if ("close".equals(methodName)) {
                        state.resultSetClosed.set(true);
                        return null;
                    }
                    return null;
                }
        );
    }
}
