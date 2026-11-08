package com.graphorm.jdbc.cursor;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A forward-only, streaming database cursor backed by JDBC fetch size.
 * Allows memory-efficient iteration or streaming of large result sets without buffering all rows in heap memory.
 * <p>
 * Implements {@link AutoCloseable} to guarantee deterministic resource cleanup (ResultSet, PreparedStatement, Connection).
 *
 * @param <T> the mapped entity or record type
 */
public interface Cursor<T> extends Iterator<T>, Iterable<T>, AutoCloseable {

    @Override
    void close();

    @Override
    default Iterator<T> iterator() {
        return this;
    }

    /**
     * Converts this cursor into a lazy {@link Stream}.
     * When the stream is closed via {@link Stream#close()} or try-with-resources,
     * the underlying cursor and database resources are automatically closed.
     *
     * @return a streaming view of the query results
     */
    default Stream<T> stream() {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED | Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false).onClose(this::close);
    }
}
