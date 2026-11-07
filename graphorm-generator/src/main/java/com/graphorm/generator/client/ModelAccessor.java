package com.graphorm.generator.client;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Full CRUD accessor contract for regular entity models.
 * <p>
 * Extends {@link ReadOnlyModelAccessor} to add type-safe creation, update, and deletion methods.
 *
 * @param <T> the model entity record type
 * @param <F> the filter criteria type
 * @param <C> the create input DTO type
 * @param <U> the update input DTO type
 */
public interface ModelAccessor<T, F, C, U> extends ReadOnlyModelAccessor<T, F> {

    /**
     * Creates a new record in the database using the supplied input DTO.
     *
     * @param createInput the create input payload
     * @return the created and populated entity record
     */
    T create(C createInput);

    /**
     * Updates an existing record identified by primary key.
     *
     * @param id          primary key value
     * @param updateInput the update input payload
     * @return the updated entity record
     */
    T update(Object id, U updateInput);

    /**
     * Deletes a record by its primary key.
     *
     * @param id primary key value
     * @return true if a record was deleted, false otherwise
     */
    boolean delete(Object id);

    /**
     * Deletes multiple records matching the query specification.
     *
     * @param querySpec query specification consumer
     * @return count of deleted records
     */
    long deleteMany(Consumer<QuerySpec<F>> querySpec);

    /**
     * Finds a record by its primary key.
     *
     * @param id primary key value
     * @return Optional containing the entity record if found
     */
    Optional<T> findById(Object id);
}
