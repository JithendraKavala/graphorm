package com.graphorm.core.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable metadata record representing a regular model mapped 1:1 to a physical database table.
 * Supports full CRUD operations.
 *
 * @param name              the declared model name (e.g. "Student")
 * @param tableName         the physical database table name (e.g. "students")
 * @param fields            the immutable list of declared field metadata
 * @param primaryKeyFields  the field names forming the primary key
 * @param uniqueConstraints the list of unique constraint column sets
 * @param relations         the declared relationships originating from this model
 */
public record ModelMetadata(
        String name,
        String tableName,
        List<FieldMetadata> fields,
        List<String> primaryKeyFields,
        List<List<String>> uniqueConstraints,
        List<RelationMetadata> relations
) implements EntityMetadata {

    /**
     * Compact constructor enforcing immutability and defensive copying.
     */
    public ModelMetadata {
        Objects.requireNonNull(name, "Model name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Model name must not be blank");
        }
        if (tableName == null || tableName.isBlank()) {
            tableName = name.toLowerCase();
        }

        fields = fields == null ? List.of() : List.copyOf(fields);
        primaryKeyFields = primaryKeyFields == null ? List.of() : List.copyOf(primaryKeyFields);
        uniqueConstraints = uniqueConstraints == null
                ? List.of()
                : List.copyOf(uniqueConstraints.stream().map(List::copyOf).toList());
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    @Override
    public EntityType entityType() {
        return EntityType.MODEL;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Checks if the given field name is part of the primary key.
     *
     * @param fieldName the field name to check
     * @return true if the field is a primary key component
     */
    public boolean isPrimaryKey(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return primaryKeyFields.contains(fieldName);
    }

    /**
     * Looks up a relation by its relation name.
     *
     * @param relationName the name of the relation
     * @return an {@link Optional} containing the relation metadata if found, or empty
     */
    public Optional<RelationMetadata> findRelation(String relationName) {
        if (relationName == null) {
            return Optional.empty();
        }
        return relations.stream()
                .filter(r -> r.relationName().equals(relationName))
                .findFirst();
    }

    /**
     * Retrieves a relation by its relation name, or throws {@link IllegalArgumentException} if not found.
     *
     * @param relationName the name of the relation
     * @return the relation metadata
     * @throws IllegalArgumentException if no relation with the given name exists
     */
    public RelationMetadata getRelation(String relationName) {
        return findRelation(relationName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Relation '" + relationName + "' not found on model '" + name + "'"));
    }

    /**
     * Checks if a relation with the specified name exists on this model.
     *
     * @param relationName the relation name to check
     * @return true if the relation exists, false otherwise
     */
    public boolean hasRelation(String relationName) {
        return findRelation(relationName).isPresent();
    }

    /**
     * Creates a new builder for {@link ModelMetadata}.
     *
     * @param name the model name
     * @return a new Builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Fluent builder for {@link ModelMetadata}.
     */
    public static final class Builder {
        private final String name;
        private String tableName;
        private final List<FieldMetadata> fields = new ArrayList<>();
        private final List<String> primaryKeyFields = new ArrayList<>();
        private final List<List<String>> uniqueConstraints = new ArrayList<>();
        private final List<RelationMetadata> relations = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Model name must not be null");
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder addField(FieldMetadata field) {
            this.fields.add(Objects.requireNonNull(field, "Field must not be null"));
            return this;
        }

        public Builder fields(List<FieldMetadata> fields) {
            this.fields.clear();
            if (fields != null) {
                this.fields.addAll(fields);
            }
            return this;
        }

        public Builder addPrimaryKeyField(String fieldName) {
            this.primaryKeyFields.add(Objects.requireNonNull(fieldName, "Primary key field must not be null"));
            return this;
        }

        public Builder primaryKeyFields(List<String> primaryKeyFields) {
            this.primaryKeyFields.clear();
            if (primaryKeyFields != null) {
                this.primaryKeyFields.addAll(primaryKeyFields);
            }
            return this;
        }

        public Builder addUniqueConstraint(List<String> constraintFields) {
            Objects.requireNonNull(constraintFields, "Constraint fields must not be null");
            this.uniqueConstraints.add(List.copyOf(constraintFields));
            return this;
        }

        public Builder addUniqueConstraint(String... constraintFields) {
            Objects.requireNonNull(constraintFields, "Constraint fields must not be null");
            this.uniqueConstraints.add(List.of(constraintFields));
            return this;
        }

        public Builder uniqueConstraints(List<List<String>> uniqueConstraints) {
            this.uniqueConstraints.clear();
            if (uniqueConstraints != null) {
                for (List<String> constraint : uniqueConstraints) {
                    this.uniqueConstraints.add(List.copyOf(constraint));
                }
            }
            return this;
        }

        public Builder addRelation(RelationMetadata relation) {
            this.relations.add(Objects.requireNonNull(relation, "Relation must not be null"));
            return this;
        }

        public Builder relations(List<RelationMetadata> relations) {
            this.relations.clear();
            if (relations != null) {
                this.relations.addAll(relations);
            }
            return this;
        }

        public ModelMetadata build() {
            return new ModelMetadata(
                    name,
                    tableName,
                    fields,
                    primaryKeyFields,
                    uniqueConstraints,
                    relations
            );
        }
    }
}
