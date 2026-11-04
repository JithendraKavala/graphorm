package com.graphorm.core.query;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;

import java.util.Map;
import java.util.Objects;

/**
 * Defensive query guards and factories enforcing the architectural read-only invariant.
 * Ensures view models are never targeted by write queries (INSERT, UPDATE, DELETE).
 */
public final class QueryGuards {

    private QueryGuards() {}

    /**
     * Asserts that the given entity metadata is writable.
     *
     * @param entity    the entity to check
     * @param operation the attempted write operation (e.g. "INSERT", "UPDATE", "DELETE")
     * @throws ReadOnlyViewException if the entity is read-only
     */
    public static void assertWritable(EntityMetadata entity, String operation) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(operation, "Operation must not be null");
        if (entity.isReadOnly()) {
            throw new ReadOnlyViewException(entity.name(), operation);
        }
    }

    /**
     * Asserts that an entity with the given properties is writable.
     *
     * @param entityName the entity name
     * @param isReadOnly whether the entity is marked read-only
     * @param operation  the attempted write operation
     * @throws ReadOnlyViewException if isReadOnly is true
     */
    public static void assertWritable(String entityName, boolean isReadOnly, String operation) {
        Objects.requireNonNull(entityName, "Entity name must not be null");
        Objects.requireNonNull(operation, "Operation must not be null");
        if (isReadOnly) {
            throw new ReadOnlyViewException(entityName, operation);
        }
    }

    /**
     * Asserts that the given source table is not a database view.
     *
     * @param source    the source table
     * @param operation the attempted write operation
     * @throws ReadOnlyViewException if the source is a view
     */
    public static void assertNotView(SourceTable source, String operation) {
        Objects.requireNonNull(source, "Source table must not be null");
        Objects.requireNonNull(operation, "Operation must not be null");
        if (source.isView()) {
            throw new ReadOnlyViewException(source.identifier(), operation);
        }
    }

    /**
     * Safely constructs an {@link InsertQuery} for an entity after asserting writability.
     *
     * @param entity the entity metadata (must be a regular ModelMetadata)
     * @param values column-to-value map
     * @return the constructed InsertQuery
     * @throws ReadOnlyViewException if the entity is a read-only view
     */
    public static InsertQuery createInsert(EntityMetadata entity, Map<String, Object> values) {
        assertWritable(entity, "INSERT");
        if (entity instanceof ModelMetadata model) {
            return InsertQuery.into(model.tableName()).values(values).build();
        }
        return InsertQuery.into(entity.name().toLowerCase()).values(values).build();
    }

    /**
     * Safely constructs an {@link UpdateQuery} for an entity after asserting writability.
     *
     * @param entity the entity metadata (must be a regular ModelMetadata)
     * @param values column-to-value map
     * @param where  the filter condition
     * @return the constructed UpdateQuery
     * @throws ReadOnlyViewException if the entity is a read-only view
     */
    public static UpdateQuery createUpdate(EntityMetadata entity, Map<String, Object> values, WherePredicate where) {
        assertWritable(entity, "UPDATE");
        if (entity instanceof ModelMetadata model) {
            return UpdateQuery.table(model.tableName()).values(values).where(where).build();
        }
        return UpdateQuery.table(entity.name().toLowerCase()).values(values).where(where).build();
    }

    /**
     * Safely constructs a {@link DeleteQuery} for an entity after asserting writability.
     *
     * @param entity the entity metadata (must be a regular ModelMetadata)
     * @param where  the filter condition
     * @return the constructed DeleteQuery
     * @throws ReadOnlyViewException if the entity is a read-only view
     */
    public static DeleteQuery createDelete(EntityMetadata entity, WherePredicate where) {
        assertWritable(entity, "DELETE");
        if (entity instanceof ModelMetadata model) {
            return DeleteQuery.from(model.tableName()).where(where).build();
        }
        return DeleteQuery.from(entity.name().toLowerCase()).where(where).build();
    }
}
