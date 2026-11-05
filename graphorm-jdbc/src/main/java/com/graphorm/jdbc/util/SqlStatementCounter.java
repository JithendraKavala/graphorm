package com.graphorm.jdbc.util;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Statement execution tracker and assertion harness for verifying exact SQL query counts
 * and preventing N+1 query regressions.
 */
public class SqlStatementCounter implements StatementInterceptor {

    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger selectCount = new AtomicInteger(0);
    private final AtomicInteger insertCount = new AtomicInteger(0);
    private final AtomicInteger updateCount = new AtomicInteger(0);
    private final AtomicInteger deleteCount = new AtomicInteger(0);

    private final List<String> recordedStatements = new CopyOnWriteArrayList<>();

    public SqlStatementCounter() {}

    @Override
    public void beforeExecute(String sql, List<Object> parameters) {
        record(sql);
    }

    /**
     * Records a statement execution and increments specific query type counters.
     *
     * @param sql the SQL statement string
     */
    public void record(String sql) {
        if (sql == null || sql.isBlank()) {
            return;
        }
        totalCount.incrementAndGet();
        recordedStatements.add(sql.trim());

        String trimmed = sql.trim().toUpperCase(Locale.ROOT);
        if (trimmed.startsWith("SELECT") || trimmed.startsWith("WITH")) {
            selectCount.incrementAndGet();
        } else if (trimmed.startsWith("INSERT")) {
            insertCount.incrementAndGet();
        } else if (trimmed.startsWith("UPDATE")) {
            updateCount.incrementAndGet();
        } else if (trimmed.startsWith("DELETE")) {
            deleteCount.incrementAndGet();
        }
    }

    /**
     * Resets all statement counters and recorded statements list.
     */
    public void reset() {
        totalCount.set(0);
        selectCount.set(0);
        insertCount.set(0);
        updateCount.set(0);
        deleteCount.set(0);
        recordedStatements.clear();
    }

    public int totalCount() {
        return totalCount.get();
    }

    public int selectCount() {
        return selectCount.get();
    }

    public int insertCount() {
        return insertCount.get();
    }

    public int updateCount() {
        return updateCount.get();
    }

    public int deleteCount() {
        return deleteCount.get();
    }

    public List<String> statements() {
        return Collections.unmodifiableList(new ArrayList<>(recordedStatements));
    }

    /**
     * Asserts that total statements executed equals expected.
     *
     * @param expected expected statement count
     */
    public void assertTotalCount(int expected) {
        int actual = totalCount.get();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected exactly %d SQL statements, but got %d.\nRecorded statements:\n%s",
                    expected, actual, formatStatements()
            ));
        }
    }

    /**
     * Asserts that SELECT statements executed equals expected.
     *
     * @param expected expected select count
     */
    public void assertSelectCount(int expected) {
        int actual = selectCount.get();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected exactly %d SELECT statements, but got %d.\nRecorded statements:\n%s",
                    expected, actual, formatStatements()
            ));
        }
    }

    /**
     * Asserts that INSERT statements executed equals expected.
     *
     * @param expected expected insert count
     */
    public void assertInsertCount(int expected) {
        int actual = insertCount.get();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected exactly %d INSERT statements, but got %d.\nRecorded statements:\n%s",
                    expected, actual, formatStatements()
            ));
        }
    }

    /**
     * Asserts that UPDATE statements executed equals expected.
     *
     * @param expected expected update count
     */
    public void assertUpdateCount(int expected) {
        int actual = updateCount.get();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected exactly %d UPDATE statements, but got %d.\nRecorded statements:\n%s",
                    expected, actual, formatStatements()
            ));
        }
    }

    /**
     * Asserts that DELETE statements executed equals expected.
     *
     * @param expected expected delete count
     */
    public void assertDeleteCount(int expected) {
        int actual = deleteCount.get();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected exactly %d DELETE statements, but got %d.\nRecorded statements:\n%s",
                    expected, actual, formatStatements()
            ));
        }
    }

    private String formatStatements() {
        if (recordedStatements.isEmpty()) {
            return "  (none)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recordedStatements.size(); i++) {
            sb.append("  [").append(i + 1).append("] ").append(recordedStatements.get(i)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Wraps a {@link DataSource} in a dynamic proxy that intercepts and counts all statements.
     *
     * @param delegate the real DataSource
     * @return a proxy DataSource recording statements
     */
    public DataSource wrap(DataSource delegate) {
        Objects.requireNonNull(delegate, "Delegate DataSource must not be null");
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                new DataSourceInvocationHandler(delegate, this)
        );
    }

    private static class DataSourceInvocationHandler implements InvocationHandler {
        private final DataSource target;
        private final SqlStatementCounter counter;

        DataSourceInvocationHandler(DataSource target, SqlStatementCounter counter) {
            this.target = target;
            this.counter = counter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getConnection".equals(method.getName())) {
                Connection realConnection = (Connection) method.invoke(target, args);
                return wrapConnection(realConnection, counter);
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(target)) {
                    return target;
                }
            }
            if ("isWrapperFor".equals(method.getName()) && args != null && args.length == 1) {
                Class<?> iface = (Class<?>) args[0];
                return iface.isInstance(target);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(method.getName())) {
                return "CountingDataSourceProxy(" + target + ")";
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static Connection wrapConnection(Connection connection, SqlStatementCounter counter) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionInvocationHandler(connection, counter)
        );
    }

    private static class ConnectionInvocationHandler implements InvocationHandler {
        private final Connection target;
        private final SqlStatementCounter counter;

        ConnectionInvocationHandler(Connection target, SqlStatementCounter counter) {
            this.target = target;
            this.counter = counter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("prepareStatement".equals(methodName) && args != null && args.length > 0 && args[0] instanceof String sql) {
                PreparedStatement ps = (PreparedStatement) method.invoke(target, args);
                return wrapPreparedStatement(ps, sql, counter);
            }
            if ("createStatement".equals(methodName)) {
                Statement stmt = (Statement) method.invoke(target, args);
                return wrapStatement(stmt, counter);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName)) {
                return "CountingConnectionProxy(" + target + ")";
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static PreparedStatement wrapPreparedStatement(
            PreparedStatement ps,
            String sql,
            SqlStatementCounter counter
    ) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                new PreparedStatementInvocationHandler(ps, sql, counter)
        );
    }

    private static class PreparedStatementInvocationHandler implements InvocationHandler {
        private final PreparedStatement target;
        private final String sql;
        private final SqlStatementCounter counter;

        PreparedStatementInvocationHandler(PreparedStatement target, String sql, SqlStatementCounter counter) {
            this.target = target;
            this.sql = sql;
            this.counter = counter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("executeQuery".equals(methodName)
                    || "executeUpdate".equals(methodName)
                    || "execute".equals(methodName)
                    || "executeLargeUpdate".equals(methodName)
                    || "executeBatch".equals(methodName)) {
                counter.record(sql);
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static Statement wrapStatement(Statement stmt, SqlStatementCounter counter) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                new StatementInvocationHandler(stmt, counter)
        );
    }

    private static class StatementInvocationHandler implements InvocationHandler {
        private final Statement target;
        private final SqlStatementCounter counter;

        StatementInvocationHandler(Statement target, SqlStatementCounter counter) {
            this.target = target;
            this.counter = counter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (("executeQuery".equals(methodName) || "executeUpdate".equals(methodName) || "execute".equals(methodName))
                    && args != null && args.length > 0 && args[0] instanceof String sql) {
                counter.record(sql);
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
