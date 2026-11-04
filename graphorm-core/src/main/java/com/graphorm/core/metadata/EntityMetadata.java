package com.graphorm.core.metadata;

import java.util.List;
import java.util.Optional;

/**
 * Common contract for all top-level schema entity metadata: regular models,
 * view models, and composite types.
 */
public interface EntityMetadata {

    /**
     * Returns the declared name of this entity in the schema.
     *
     * @return the entity name
     */
    String name();

    /**
     * Returns the architectural entity primitive type.
     *
     * @return the entity type (MODEL, VIEW, or COMPOSITE_TYPE)
     */
    EntityType entityType();

    /**
     * Indicates whether this entity is strictly read-only.
     * View models must always return {@code true}.
     *
     * @return true if read-only, false if write operations are permitted
     */
    boolean isReadOnly();

    /**
     * Returns an unmodifiable list of all fields declared on this entity.
     *
     * @return immutable list of field metadata
     */
    List<FieldMetadata> fields();

    /**
     * Looks up a field by its logical name.
     *
     * @param fieldName the name of the field to find
     * @return an {@link Optional} containing the field metadata if found, or empty
     */
    default Optional<FieldMetadata> findField(String fieldName) {
        if (fieldName == null) {
            return Optional.empty();
        }
        return fields().stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst();
    }

    /**
     * Retrieves a field by its logical name, or throws {@link IllegalArgumentException} if not found.
     *
     * @param fieldName the name of the field to retrieve
     * @return the field metadata
     * @throws IllegalArgumentException if no field with the given name exists
     */
    default FieldMetadata getField(String fieldName) {
        return findField(fieldName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Field '" + fieldName + "' not found on entity '" + name() + "'"));
    }

    /**
     * Checks if a field with the specified name exists on this entity.
     *
     * @param fieldName the field name to check
     * @return true if the field exists, false otherwise
     */
    default boolean hasField(String fieldName) {
        return findField(fieldName).isPresent();
    }
}
