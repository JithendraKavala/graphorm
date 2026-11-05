package com.graphorm.core.query.relation;

import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.SelectQuery;

import java.util.List;
import java.util.Objects;

/**
 * Immutable execution plan for loading related entities in a batched SQL query.
 *
 * @param relation         the relationship metadata being traversed
 * @param sourceModel      the source model originating the relation
 * @param targetModel      the target model being fetched
 * @param sourceJoinField  the field on the source model providing the join key values
 * @param targetJoinField  the field on the target model matching the join key
 * @param targetJoinColumn the database column name on the target table used in the WHERE IN predicate
 * @param keys             the distinct, non-null batch join keys extracted from parent entities
 * @param selectQuery      the unified SelectQuery AST targeting the related table
 * @param relationType     the cardinality type (1:1, 1:N, N:1, N:M)
 */
public record RelationPlan(
        RelationMetadata relation,
        ModelMetadata sourceModel,
        ModelMetadata targetModel,
        String sourceJoinField,
        String targetJoinField,
        String targetJoinColumn,
        List<Object> keys,
        SelectQuery selectQuery,
        RelationType relationType
) {

    public RelationPlan {
        Objects.requireNonNull(relation, "Relation metadata must not be null");
        Objects.requireNonNull(sourceModel, "Source model must not be null");
        Objects.requireNonNull(targetModel, "Target model must not be null");
        Objects.requireNonNull(sourceJoinField, "Source join field must not be null");
        Objects.requireNonNull(targetJoinField, "Target join field must not be null");
        Objects.requireNonNull(targetJoinColumn, "Target join column must not be null");
        Objects.requireNonNull(selectQuery, "SelectQuery must not be null");
        Objects.requireNonNull(relationType, "RelationType must not be null");

        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    /**
     * Checks if this relation resolves to at most one target entity (1:1 or N:1).
     *
     * @return true if to-one relation, false if to-many
     */
    public boolean isToOne() {
        return relationType == RelationType.ONE_TO_ONE || relationType == RelationType.MANY_TO_ONE;
    }

    /**
     * Checks if this relation resolves to a list of target entities (1:N or M:N).
     *
     * @return true if to-many relation, false if to-one
     */
    public boolean isToMany() {
        return relationType == RelationType.ONE_TO_MANY || relationType == RelationType.MANY_TO_MANY;
    }

    /**
     * Returns the declared relation name.
     *
     * @return relation name
     */
    public String relationName() {
        return relation.relationName();
    }

    /**
     * Returns the declared name of the source model.
     *
     * @return source model name
     */
    public String sourceModelName() {
        return sourceModel.name();
    }

    /**
     * Returns the declared name of the target model.
     *
     * @return target model name
     */
    public String targetModelName() {
        return targetModel.name();
    }

    /**
     * Returns the database table name of the target model.
     *
     * @return target table name
     */
    public String targetTableName() {
        return targetModel.tableName();
    }
}
