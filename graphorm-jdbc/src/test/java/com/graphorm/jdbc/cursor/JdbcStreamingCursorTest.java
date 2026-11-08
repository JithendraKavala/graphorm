package com.graphorm.jdbc.cursor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcStreamingCursorTest {

    @Test
    @DisplayName("DefaultCursor iterates through rows and cleans up resources on close")
    void cursorIteratesAndCloses() {
        AtomicBoolean rsClosed = new AtomicBoolean(false);
        AtomicBoolean stmtClosed = new AtomicBoolean(false);
        AtomicBoolean connClosed = new AtomicBoolean(false);
        AtomicInteger nextCalls = new AtomicInteger(0);

        List<String> names = List.of("Alice", "Bob");

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if ("next".equals(mName)) {
                        return nextCalls.getAndIncrement() < names.size();
                    }
                    if ("getString".equals(mName)) {
                        return names.get(nextCalls.get() - 1);
                    }
                    if ("isClosed".equals(mName)) {
                        return rsClosed.get();
                    }
                    if ("close".equals(mName)) {
                        rsClosed.set(true);
                        return null;
                    }
                    return null;
                }
        );

        PreparedStatement stmt = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) return stmtClosed.get();
                    if ("close".equals(method.getName())) { stmtClosed.set(true); return null; }
                    return null;
                }
        );

        Connection conn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) return connClosed.get();
                    if ("close".equals(method.getName())) { connClosed.set(true); return null; }
                    if ("getAutoCommit".equals(method.getName())) return true;
                    return null;
                }
        );

        DefaultCursor<String> cursor = new DefaultCursor<>(conn, stmt, rs, (r, rowNum) -> r.getString("name"));

        assertThat(cursor.hasNext()).isTrue();
        assertThat(cursor.next()).isEqualTo("Alice");
        assertThat(cursor.hasNext()).isTrue();
        assertThat(cursor.next()).isEqualTo("Bob");
        assertThat(cursor.hasNext()).isFalse();

        assertThatThrownBy(cursor::next).isInstanceOf(NoSuchElementException.class);

        cursor.close();
        assertThat(rsClosed.get()).isTrue();
        assertThat(stmtClosed.get()).isTrue();
        assertThat(connClosed.get()).isTrue();
    }

    @Test
    @DisplayName("DefaultCursor stream() correctly processes elements and auto-closes on completion")
    void cursorStream() {
        AtomicBoolean rsClosed = new AtomicBoolean(false);
        AtomicBoolean stmtClosed = new AtomicBoolean(false);
        AtomicBoolean connClosed = new AtomicBoolean(false);
        AtomicInteger nextCalls = new AtomicInteger(0);

        List<Integer> ids = List.of(10, 20, 30);

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if ("next".equals(mName)) {
                        return nextCalls.getAndIncrement() < ids.size();
                    }
                    if ("getInt".equals(mName)) {
                        return ids.get(nextCalls.get() - 1);
                    }
                    if ("isClosed".equals(mName)) {
                        return rsClosed.get();
                    }
                    if ("close".equals(mName)) {
                        rsClosed.set(true);
                        return null;
                    }
                    return null;
                }
        );

        PreparedStatement stmt = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) return stmtClosed.get();
                    if ("close".equals(method.getName())) { stmtClosed.set(true); return null; }
                    return null;
                }
        );

        Connection conn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) return connClosed.get();
                    if ("close".equals(method.getName())) { connClosed.set(true); return null; }
                    if ("getAutoCommit".equals(method.getName())) return true;
                    return null;
                }
        );

        DefaultCursor<Integer> cursor = new DefaultCursor<>(conn, stmt, rs, (r, rowNum) -> r.getInt("id"));

        try (Stream<Integer> stream = cursor.stream()) {
            List<Integer> list = stream.map(x -> x * 2).toList();
            assertThat(list).containsExactly(20, 40, 60);
        }

        assertThat(rsClosed.get()).isTrue();
        assertThat(stmtClosed.get()).isTrue();
        assertThat(connClosed.get()).isTrue();
    }
}
