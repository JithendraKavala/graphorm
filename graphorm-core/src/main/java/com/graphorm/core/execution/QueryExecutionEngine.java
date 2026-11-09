package com.graphorm.core.execution;

import com.graphorm.core.cache.GraphOrmCacheManager;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Service Provider Interface (SPI) for decoupled database query execution.
 * Isolates high-level query orchestration (such as GraphQL DataFetchers and client APIs)
 * from underlying database driver primitives and JDBC transport.
 */
public interface QueryExecutionEngine {

    /**
     * Returns the optional {@link GraphOrmCacheManager} configured on this execution engine.
     *
     * @return an Optional containing the cache manager, or empty if caching is disabled
     */
    default Optional<GraphOrmCacheManager> cacheManager() {
        return Optional.empty();
    }

    /**
     * Returns the optional {@link com.graphorm.core.event.EntityEventBroker} configured on this execution engine.
     *
     * @return an Optional containing the event broker, or empty if event broadcasting is disabled
     */
    default Optional<com.graphorm.core.event.EntityEventBroker> eventBroker() {
        return Optional.empty();
    }

    /**
     * Executes a SELECT query AST and returns the resulting rows as maps.
     *
     * @param query the select query AST
     * @return list of rows where each row is a column-to-value map
     */
    List<Map<String, Object>> executeSelect(SelectQuery query);

    /**
     * Executes a SELECT query AST expecting at most one row.
     *
     * @param query the select query AST
     * @return optional containing the single row map, or empty if no rows found
     */
    default Optional<Map<String, Object>> executeSelectOne(SelectQuery query) {
        Objects.requireNonNull(query, "SelectQuery must not be null");
        List<Map<String, Object>> rows = executeSelect(query);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.getFirst());
    }

    /**
     * Convenience alias for {@link #executeSelect(SelectQuery)}.
     */
    default List<Map<String, Object>> queryForList(SelectQuery query) {
        return executeSelect(query);
    }

    /**
     * Convenience alias for {@link #executeSelectOne(SelectQuery)}.
     */
    default Optional<Map<String, Object>> queryForObject(SelectQuery query) {
        return executeSelectOne(query);
    }

    /**
     * Executes an INSERT query AST returning the number of affected rows.
     *
     * @param query the insert query AST
     * @return the number of affected rows
     */
    int executeInsert(InsertQuery query);

    /**
     * Executes an INSERT query AST and returns the newly inserted row map (via RETURNING or generated keys).
     *
     * @param query the insert query AST
     * @return optional containing the inserted row map
     */
    Optional<Map<String, Object>> executeInsertAndReturn(InsertQuery query);

    /**
     * Executes an UPDATE query AST returning the number of affected rows.
     *
     * @param query the update query AST
     * @return the number of affected rows
     */
    int executeUpdate(UpdateQuery query);

    /**
     * Executes an UPDATE query AST and returns the updated row map.
     *
     * @param query the update query AST
     * @return optional containing the updated row map
     */
    Optional<Map<String, Object>> executeUpdateAndReturn(UpdateQuery query);

    /**
     * Executes a DELETE query AST returning the number of affected rows.
     *
     * @param query the delete query AST
     * @return the number of affected rows
     */
    int executeDelete(DeleteQuery query);

    /**
     * Convenience alias for {@link #executeDelete(DeleteQuery)}.
     */
    default int executeUpdate(DeleteQuery query) {
        return executeDelete(query);
    }

    /**
     * Executes a batch INSERT query AST returning total inserted rows.
     *
     * @param query the batch insert query AST
     * @return total inserted rows
     */
    int executeBatchInsert(BatchInsertQuery query);

    /**
     * Executes a batch INSERT query AST and returns the inserted row maps.
     *
     * @param query the batch insert query AST
     * @return list of inserted row maps
     */
    List<Map<String, Object>> executeBatchInsertAndReturn(BatchInsertQuery query);

    // =========================================================================
    // Asynchronous Execution Methods
    // =========================================================================

    default CompletableFuture<List<Map<String, Object>>> executeSelectAsync(SelectQuery query) {
        return CompletableFuture.supplyAsync(() -> executeSelect(query));
    }

    default CompletableFuture<List<Map<String, Object>>> executeSelectAsync(SelectQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeSelect(query), executor);
    }

    default CompletableFuture<Optional<Map<String, Object>>> executeSelectOneAsync(SelectQuery query) {
        return CompletableFuture.supplyAsync(() -> executeSelectOne(query));
    }

    default CompletableFuture<Optional<Map<String, Object>>> executeSelectOneAsync(SelectQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeSelectOne(query), executor);
    }

    default CompletableFuture<Integer> executeInsertAsync(InsertQuery query) {
        return CompletableFuture.supplyAsync(() -> executeInsert(query));
    }

    default CompletableFuture<Integer> executeInsertAsync(InsertQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeInsert(query), executor);
    }

    default CompletableFuture<Optional<Map<String, Object>>> executeInsertAndReturnAsync(InsertQuery query) {
        return CompletableFuture.supplyAsync(() -> executeInsertAndReturn(query));
    }

    default CompletableFuture<Optional<Map<String, Object>>> executeInsertAndReturnAsync(InsertQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeInsertAndReturn(query), executor);
    }

    default CompletableFuture<Integer> executeUpdateAsync(UpdateQuery query) {
        return CompletableFuture.supplyAsync(() -> executeUpdate(query));
    }

    default CompletableFuture<Integer> executeUpdateAsync(UpdateQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeUpdate(query), executor);
    }

    default CompletableFuture<Optional<Map<String, Object>>> executeUpdateAndReturnAsync(UpdateQuery query) {
        return CompletableFuture.supplyAsync(() -> executeUpdateAndReturn(query));
    }

    default CompletableFuture<Optional<Map<String, Object>>> executeUpdateAndReturnAsync(UpdateQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeUpdateAndReturn(query), executor);
    }

    default CompletableFuture<Integer> executeDeleteAsync(DeleteQuery query) {
        return CompletableFuture.supplyAsync(() -> executeDelete(query));
    }

    default CompletableFuture<Integer> executeDeleteAsync(DeleteQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeDelete(query), executor);
    }

    default CompletableFuture<Integer> executeBatchInsertAsync(BatchInsertQuery query) {
        return CompletableFuture.supplyAsync(() -> executeBatchInsert(query));
    }

    default CompletableFuture<Integer> executeBatchInsertAsync(BatchInsertQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeBatchInsert(query), executor);
    }

    default CompletableFuture<List<Map<String, Object>>> executeBatchInsertAndReturnAsync(BatchInsertQuery query) {
        return CompletableFuture.supplyAsync(() -> executeBatchInsertAndReturn(query));
    }

    default CompletableFuture<List<Map<String, Object>>> executeBatchInsertAndReturnAsync(BatchInsertQuery query, Executor executor) {
        return CompletableFuture.supplyAsync(() -> executeBatchInsertAndReturn(query), executor);
    }
}
