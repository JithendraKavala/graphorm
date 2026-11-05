package com.graphorm.jdbc.transaction;

import com.graphorm.jdbc.exception.QueryExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JdbcTransactionManager Unit Tests")
class JdbcTransactionManagerTest {

    @Test
    @DisplayName("Should commit transaction and restore autoCommit on success")
    void shouldCommitOnSuccess() {
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicBoolean rolledBack = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);

        DataSource ds = createMockDataSource(committed, rolledBack, closed);
        JdbcTransactionManager txManager = new JdbcTransactionManager(ds);

        String result = txManager.executeInTransaction(conn -> "success");

        assertThat(result).isEqualTo("success");
        assertThat(committed.get()).isTrue();
        assertThat(rolledBack.get()).isFalse();
        assertThat(closed.get()).isTrue();
    }

    @Test
    @DisplayName("Should rollback transaction and close connection on error")
    void shouldRollbackOnError() {
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicBoolean rolledBack = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);

        DataSource ds = createMockDataSource(committed, rolledBack, closed);
        JdbcTransactionManager txManager = new JdbcTransactionManager(ds);

        assertThatThrownBy(() -> txManager.executeInTransaction(conn -> {
            throw new RuntimeException("DB transaction failure");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB transaction failure");

        assertThat(committed.get()).isFalse();
        assertThat(rolledBack.get()).isTrue();
        assertThat(closed.get()).isTrue();
    }

    private DataSource createMockDataSource(
            AtomicBoolean committed,
            AtomicBoolean rolledBack,
            AtomicBoolean closed
    ) {
        Connection conn = (Connection) Proxy.newProxyInstance(
                JdbcTransactionManagerTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("commit".equals(name)) {
                        committed.set(true);
                        return null;
                    }
                    if ("rollback".equals(name)) {
                        rolledBack.set(true);
                        return null;
                    }
                    if ("close".equals(name)) {
                        closed.set(true);
                        return null;
                    }
                    if ("getAutoCommit".equals(name)) {
                        return true;
                    }
                    if ("setAutoCommit".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );

        return (DataSource) Proxy.newProxyInstance(
                JdbcTransactionManagerTest.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return conn;
                    }
                    return null;
                }
        );
    }
}
