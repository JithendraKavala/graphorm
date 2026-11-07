package com.graphorm.generator.client;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Read-only accessor contract for views and read-only entities.
 * <p>
 * Strictly omits any write, insert, update, or delete operations.
 *
 * @param <T> the entity or projection record type
 * @param <F> the filter criteria type
 */
public interface ReadOnlyModelAccessor<T, F> {

    /**
     * Finds all records matching no filters.
     *
     * @return list of records
     */
    List<T> findMany();

    /**
     * Finds records matching the specified query specification.
     *
     * @param querySpec query specification consumer
     * @return list of matching records
     */
    List<T> findMany(Consumer<QuerySpec<F>> querySpec);

    /**
     * Finds a single unique record matching the specified query specification.
     *
     * @param querySpec query specification consumer
     * @return Optional containing the record if found
     */
    Optional<T> findUnique(Consumer<QuerySpec<F>> querySpec);

    /**
     * Finds the first record matching the specified query specification.
     *
     * @param querySpec query specification consumer
     * @return Optional containing the first record if found
     */
    Optional<T> findFirst(Consumer<QuerySpec<F>> querySpec);

    /**
     * Counts all records in the model or view.
     *
     * @return total count
     */
    long count();

    /**
     * Counts records matching the specified query specification.
     *
     * @param querySpec query specification consumer
     * @return matching count
     */
    long count(Consumer<QuerySpec<F>> querySpec);

    /**
     * Checks if at least one record matches the specified query specification.
     *
     * @param querySpec query specification consumer
     * @return true if at least one matching record exists
     */
    boolean exists(Consumer<QuerySpec<F>> querySpec);
}
