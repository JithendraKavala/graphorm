package com.graphorm.jdbc.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SqlStatementCounter Unit Tests (TASK-6.3)")
class SqlStatementCounterTest {

    @Test
    @DisplayName("Should track queries by classification and total count")
    void shouldTrackQueriesByClassification() {
        SqlStatementCounter counter = new SqlStatementCounter();
        assertThat(counter.totalCount()).isZero();
        assertThat(counter.selectCount()).isZero();
        assertThat(counter.insertCount()).isZero();
        assertThat(counter.updateCount()).isZero();
        assertThat(counter.deleteCount()).isZero();
        assertThat(counter.statements()).isEmpty();

        counter.record("SELECT id, name FROM students");
        counter.record("  select * from departments  ");
        counter.record("WITH cte AS (SELECT 1) SELECT * FROM cte");
        counter.record("INSERT INTO students (name) VALUES (?)");
        counter.record("UPDATE students SET name = ? WHERE id = ?");
        counter.record("DELETE FROM students WHERE id = ?");

        assertThat(counter.totalCount()).isEqualTo(6);
        assertThat(counter.selectCount()).isEqualTo(3);
        assertThat(counter.insertCount()).isEqualTo(1);
        assertThat(counter.updateCount()).isEqualTo(1);
        assertThat(counter.deleteCount()).isEqualTo(1);
        assertThat(counter.statements()).hasSize(6);

        // Assert helpers
        counter.assertTotalCount(6);
        counter.assertSelectCount(3);
        counter.assertInsertCount(1);
        counter.assertUpdateCount(1);
        counter.assertDeleteCount(1);

        // Reset
        counter.reset();
        assertThat(counter.totalCount()).isZero();
        assertThat(counter.selectCount()).isZero();
        assertThat(counter.statements()).isEmpty();
    }

    @Test
    @DisplayName("Should throw AssertionError on failed count assertions with formatted statement log")
    void shouldThrowAssertionErrorOnMismatch() {
        SqlStatementCounter counter = new SqlStatementCounter();
        counter.record("SELECT 1");

        assertThatThrownBy(() -> counter.assertTotalCount(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected exactly 2 SQL statements, but got 1")
                .hasMessageContaining("[1] SELECT 1");

        assertThatThrownBy(() -> counter.assertSelectCount(0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected exactly 0 SELECT statements, but got 1");
    }

    @Test
    @DisplayName("Should intercept statement execution via DataSource dynamic proxy")
    void shouldInterceptViaDataSourceProxy() throws Exception {
        DataSource mockDs = createMockDataSource();

        SqlStatementCounter counter = new SqlStatementCounter();
        DataSource proxyDs = counter.wrap(mockDs);

        // Execute prepared statement via proxy
        try (Connection conn = proxyDs.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM students WHERE id = ?")) {
            ps.executeQuery();
        }

        // Execute statement via proxy
        try (Connection conn = proxyDs.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM students WHERE id = 1");
        }

        counter.assertTotalCount(2);
        counter.assertSelectCount(1);
        counter.assertDeleteCount(1);
        assertThat(counter.statements()).contains(
                "SELECT * FROM students WHERE id = ?",
                "DELETE FROM students WHERE id = 1"
        );
    }

    private static DataSource createMockDataSource() {
        InvocationHandler dsHandler = (proxy, method, args) -> {
            if ("getConnection".equals(method.getName())) {
                return createMockConnection();
            }
            return null;
        };
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                dsHandler
        );
    }

    private static Connection createMockConnection() {
        InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                return createMockPreparedStatement();
            }
            if ("createStatement".equals(method.getName())) {
                return createMockStatement();
            }
            return null;
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                connHandler
        );
    }

    private static PreparedStatement createMockPreparedStatement() {
        InvocationHandler psHandler = (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName())) {
                return createMockResultSet();
            }
            if ("executeUpdate".equals(method.getName())) {
                return 1;
            }
            return null;
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                psHandler
        );
    }

    private static Statement createMockStatement() {
        InvocationHandler stmtHandler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) {
                return 1;
            }
            if ("executeQuery".equals(method.getName())) {
                return createMockResultSet();
            }
            return null;
        };
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                stmtHandler
        );
    }

    private static ResultSet createMockResultSet() {
        InvocationHandler rsHandler = (proxy, method, args) -> false;
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                rsHandler
        );
    }
}
