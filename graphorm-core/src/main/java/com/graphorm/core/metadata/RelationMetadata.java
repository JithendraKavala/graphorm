package com.graphorm.core.metadata;

import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata record defining a relationship between two entities.
 *
 * @param relationName     the name of the relationship
 * @param relationType     the cardinality type (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY)
 * @param sourceModel      the source model declaring the relation
 * @param targetModel      the target model referenced by the relation
 * @param foreignKeyFields the foreign key field names in the source model (or join table)
 * @param referenceFields  the primary/unique key field names in the target model referenced by the FK
 * @param joinTable        the optional join table name (primarily for MANY_TO_MANY relationships)
 */
public record RelationMetadata(
        String relationName,
        RelationType relationType,
        String sourceModel,
        String targetModel,
        List<String> foreignKeyFields,
        List<String> referenceFields,
        String joinTable
) {

    /**
     * Compact constructor enforcing immutability and defensive copying.
     */
    public RelationMetadata {
        Objects.requireNonNull(relationName, "Relation name must not be null");
        if (relationName.isBlank()) {
            throw new IllegalArgumentException("Relation name must not be blank");
        }
        Objects.requireNonNull(relationType, "Relation type must not be null");
        Objects.requireNonNull(sourceModel, "Source model must not be null");
        if (sourceModel.isBlank()) {
            throw new IllegalArgumentException("Source model must not be blank");
        }
        Objects.requireNonNull(targetModel, "Target model must not be null");
        if (targetModel.isBlank()) {
            throw new IllegalArgumentException("Target model must not be blank");
        }

        foreignKeyFields = foreignKeyFields == null ? List.of() : List.copyOf(foreignKeyFields);
        referenceFields = referenceFields == null ? List.of() : List.copyOf(referenceFields);
    }

    /**
     * Convenience constructor without joinTable.
     */
    public RelationMetadata(
            String relationName,
            RelationType relationType,
            String sourceModel,
            String targetModel,
            List<String> foreignKeyFields,
            List<String> referenceFields
    ) {
        this(relationName, relationType, sourceModel, targetModel, foreignKeyFields, referenceFields, null);
    }

    /**
     * Creates a new builder for {@link RelationMetadata}.
     *
     * @param relationName the relation name
     * @return a new Builder instance
     */
    public static Builder builder(String relationName) {
        return new Builder(relationName);
    }

    /**
     * Fluent builder for {@link RelationMetadata}.
     */
    public static final class Builder {
        private final String relationName;
        private RelationType relationType;
        private String sourceModel;
        private String targetModel;
        private List<String> foreignKeyFields = List.of();
        private List<String> referenceFields = List.of();
        private String joinTable;

        private Builder(String relationName) {
            this.relationName = Objects.requireNonNull(relationName, "Relation name must not be null");
        }

        public Builder relationType(RelationType relationType) {
            this.relationType = relationType;
            return this;
        }

        public Builder sourceModel(String sourceModel) {
            this.sourceModel = sourceModel;
            return this;
        }

        public Builder targetModel(String targetModel) {
            this.targetModel = targetModel;
            return this;
        }

        public Builder foreignKeyFields(List<String> foreignKeyFields) {
            this.foreignKeyFields = foreignKeyFields != null ? List.copyOf(foreignKeyFields) : List.of();
            return this;
        }

        public Builder foreignKeyField(String field) {
            this.foreignKeyFields = List.of(Objects.requireNonNull(field, "Foreign key field must not be null"));
            return this;
        }

        public Builder referenceFields(List<String> referenceFields) {
            this.referenceFields = referenceFields != null ? List.copyOf(referenceFields) : List.of();
            return this;
        }

        public Builder referenceField(String field) {
            this.referenceFields = List.of(Objects.requireNonNull(field, "Reference field must not be null"));
            return this;
        }

        public Builder joinTable(String joinTable) {
            this.joinTable = joinTable;
            return this;
        }

        public RelationMetadata build() {
            return new RelationMetadata(
                    relationName,
                    relationType,
                    sourceModel,
                    targetModel,
                    foreignKeyFields,
                    referenceFields,
                    joinTable
            );
        }
    }
}
