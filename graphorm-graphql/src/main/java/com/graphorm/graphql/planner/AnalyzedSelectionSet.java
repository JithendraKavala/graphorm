package com.graphorm.graphql.planner;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.query.ProjectedColumn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Result of analyzing a GraphQL selection set for a target {@link EntityMetadata}.
 * Encapsulates requested scalar fields, requested relationship trees, auto-injected
 * hidden dependency columns (for downstream DataLoader resolution), and the computed
 * SQL column projection.
 */
public class AnalyzedSelectionSet {

    private final EntityMetadata entity;
    private final Map<String, FieldTreeNode> fieldTree;
    private final Set<String> requestedScalarFields;
    private final Map<String, FieldTreeNode> requestedRelations;
    private final Set<String> hiddenDependencyFields;
    private final List<ProjectedColumn> projectedColumns;

    public AnalyzedSelectionSet(
            EntityMetadata entity,
            Map<String, FieldTreeNode> fieldTree,
            Set<String> requestedScalarFields,
            Map<String, FieldTreeNode> requestedRelations,
            Set<String> hiddenDependencyFields,
            List<ProjectedColumn> projectedColumns
    ) {
        this.entity = entity;
        this.fieldTree = fieldTree == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fieldTree));
        this.requestedScalarFields = requestedScalarFields == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(requestedScalarFields));
        this.requestedRelations = requestedRelations == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(requestedRelations));
        this.hiddenDependencyFields = hiddenDependencyFields == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(hiddenDependencyFields));
        this.projectedColumns = projectedColumns == null ? List.of() : List.copyOf(projectedColumns);
    }

    /**
     * The entity metadata being queried (Model or ViewModel), or null if analyzing untyped selection set.
     */
    public EntityMetadata entity() {
        return entity;
    }

    /**
     * All immediate fields selected at this level by field name.
     */
    public Map<String, FieldTreeNode> fieldTree() {
        return fieldTree;
    }

    /**
     * Declared field names of scalar (non-relation) fields on the entity requested by the client.
     */
    public Set<String> requestedScalarFields() {
        return requestedScalarFields;
    }

    /**
     * Requested relations on the entity, mapped to their nested {@link FieldTreeNode} trees.
     */
    public Map<String, FieldTreeNode> requestedRelations() {
        return requestedRelations;
    }

    /**
     * Field names auto-injected by the query planner as hidden foreign key or join key dependencies.
     */
    public Set<String> hiddenDependencyFields() {
        return hiddenDependencyFields;
    }

    /**
     * The exact SQL {@link ProjectedColumn} list built for the {@code SelectQuery}.
     */
    public List<ProjectedColumn> projectedColumns() {
        return projectedColumns;
    }

    /**
     * Checks if a scalar field on the entity was requested by the client.
     */
    public boolean isFieldRequested(String fieldName) {
        return requestedScalarFields.contains(fieldName);
    }

    /**
     * Checks if a relation on the entity was requested by the client.
     */
    public boolean isRelationRequested(String relationName) {
        return requestedRelations.containsKey(relationName);
    }

    /**
     * Retrieves the nested field tree for a requested relation.
     */
    public FieldTreeNode relation(String relationName) {
        return requestedRelations.get(relationName);
    }

    /**
     * Checks if any hidden dependencies were injected.
     */
    public boolean hasHiddenDependencies() {
        return !hiddenDependencyFields.isEmpty();
    }
}
