package com.graphorm.graphql.planner;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.ProjectedColumn;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Traverses GraphQL {@link DataFetchingFieldSelectionSet} to construct an AST-level field tree,
 * project only requested database columns (TASK-11.2), and auto-inject hidden foreign key dependencies
 * required for downstream relationship fetching (TASK-11.3).
 */
public final class SelectionSetAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SelectionSetAnalyzer.class);

    private static final Set<String> META_FIELDS = Set.of(
            "__typename",
            "__schema",
            "__type"
    );

    private SelectionSetAnalyzer() {}

    /**
     * Traverses the GraphQL {@link DataFetchingFieldSelectionSet} and builds a clean field tree,
     * stripping introspection meta-fields.
     *
     * @param selectionSet the GraphQL Java selection set (may be null)
     * @return map of field name to {@link FieldTreeNode}
     */
    public static Map<String, FieldTreeNode> extractTree(DataFetchingFieldSelectionSet selectionSet) {
        if (selectionSet == null) {
            return Collections.emptyMap();
        }

        Map<String, FieldTreeNode> tree = new LinkedHashMap<>();
        List<SelectedField> fields = selectionSet.getImmediateFields();
        if (fields == null || fields.isEmpty()) {
            fields = selectionSet.getFields();
        }

        if (fields != null) {
            for (SelectedField field : fields) {
                String fieldName = field.getName();
                if (META_FIELDS.contains(fieldName)) {
                    continue;
                }

                String alias = field.getAlias();
                Map<String, FieldTreeNode> children = Collections.emptyMap();
                if (field.getSelectionSet() != null) {
                    children = extractTree(field.getSelectionSet());
                }

                tree.put(fieldName, FieldTreeNode.of(fieldName, alias, field.getArguments(), children));
            }
        }

        return Collections.unmodifiableMap(tree);
    }

    /**
     * Convenience overload accepting {@link DataFetchingEnvironment}.
     */
    public static AnalyzedSelectionSet analyze(
            DataFetchingEnvironment env,
            EntityMetadata entity,
            ModelRegistry registry
    ) {
        return analyze(env != null ? env.getSelectionSet() : null, entity, registry);
    }

    /**
     * Analyzes the selection set against the entity metadata and model registry,
     * determining requested scalar fields, requested relations, and injecting required hidden FK dependencies.
     *
     * @param selectionSet the selection set from {@link graphql.schema.DataFetchingEnvironment}
     * @param entity       the target model or view model metadata
     * @param registry     the model registry (for relation key lookups)
     * @return analyzed selection set containing projection columns and hidden dependencies
     */
    public static AnalyzedSelectionSet analyze(
            DataFetchingFieldSelectionSet selectionSet,
            EntityMetadata entity,
            ModelRegistry registry
    ) {
        if (entity == null) {
            Map<String, FieldTreeNode> tree = extractTree(selectionSet);
            return new AnalyzedSelectionSet(null, tree, Set.of(), Map.of(), Set.of(), List.of());
        }

        Map<String, FieldTreeNode> tree = extractTree(selectionSet);

        Set<String> requestedScalarFields = new LinkedHashSet<>();
        Map<String, FieldTreeNode> requestedRelations = new LinkedHashMap<>();
        List<ProjectedColumn> projectedColumns = new ArrayList<>();
        Set<String> hiddenDependencyFields = new LinkedHashSet<>();

        // If selection set is empty or unavailable, fall back to all non-relation entity fields
        if (tree.isEmpty()) {
            for (FieldMetadata field : entity.fields()) {
                if (!field.isRelation()) {
                    requestedScalarFields.add(field.name());
                    projectedColumns.add(ProjectedColumn.of(field.dbColumnName(), null));
                }
            }
            return new AnalyzedSelectionSet(
                    entity,
                    tree,
                    requestedScalarFields,
                    requestedRelations,
                    hiddenDependencyFields,
                    projectedColumns
            );
        }

        // 1. Separate direct scalar fields and relations
        for (Map.Entry<String, FieldTreeNode> entry : tree.entrySet()) {
            String fieldName = entry.getKey();
            FieldTreeNode node = entry.getValue();

            if (isRelationField(entity, fieldName)) {
                requestedRelations.put(fieldName, node);
            } else if (entity.hasField(fieldName)) {
                FieldMetadata field = entity.getField(fieldName);
                requestedScalarFields.add(fieldName);

                // If user provided a client alias, preserve it
                String alias = node.alias() != null && !node.alias().equals(field.dbColumnName())
                        ? node.alias()
                        : null;
                projectedColumns.add(ProjectedColumn.of(field.dbColumnName(), alias));
            }
        }

        // If no direct scalar fields were found in the tree (e.g. only relations or aliases that match fields)
        if (requestedScalarFields.isEmpty() && requestedRelations.isEmpty()) {
            for (FieldMetadata field : entity.fields()) {
                if (!field.isRelation()) {
                    requestedScalarFields.add(field.name());
                    projectedColumns.add(ProjectedColumn.of(field.dbColumnName(), null));
                }
            }
        }

        // 2. Hidden Dependency Foreign Key Injection (TASK-11.3)
        for (String relationName : requestedRelations.keySet()) {
            RelationMetadata relation = findRelation(entity, relationName);
            if (relation == null) {
                continue;
            }

            String sourceJoinField = determineSourceJoinField(entity, relation, registry);
            if (sourceJoinField != null && !requestedScalarFields.contains(sourceJoinField)) {
                if (entity.hasField(sourceJoinField)) {
                    FieldMetadata joinField = entity.getField(sourceJoinField);
                    String dbCol = joinField.dbColumnName();

                    // Check if already in projected columns (e.g. via column name)
                    boolean alreadyProjected = projectedColumns.stream()
                            .anyMatch(c -> c.expression().equalsIgnoreCase(dbCol));

                    if (!alreadyProjected) {
                        log.debug("Auto-injecting hidden dependency column '{}' ({}) for downstream relation '{}'",
                                dbCol, sourceJoinField, relationName);
                        projectedColumns.add(ProjectedColumn.hidden(dbCol, null));
                        hiddenDependencyFields.add(sourceJoinField);
                        if (!sourceJoinField.equalsIgnoreCase(dbCol)) {
                            hiddenDependencyFields.add(dbCol);
                        }
                    }
                }
            }
        }

        return new AnalyzedSelectionSet(
                entity,
                tree,
                requestedScalarFields,
                requestedRelations,
                hiddenDependencyFields,
                projectedColumns
        );
    }

    private static boolean isRelationField(EntityMetadata entity, String fieldName) {
        if (entity.hasField(fieldName) && entity.getField(fieldName).isRelation()) {
            return true;
        }
        if (entity instanceof ModelMetadata model) {
            return model.relations().stream().anyMatch(r -> r.relationName().equalsIgnoreCase(fieldName));
        }
        return false;
    }

    private static RelationMetadata findRelation(EntityMetadata entity, String relationName) {
        if (entity instanceof ModelMetadata model) {
            return model.relations().stream()
                    .filter(r -> r.relationName().equalsIgnoreCase(relationName))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static String determineSourceJoinField(EntityMetadata entity, RelationMetadata relation, ModelRegistry registry) {
        // Direct foreign key fields on relation (only if present on source entity)
        if (!relation.foreignKeyFields().isEmpty() && entity.hasField(relation.foreignKeyFields().getFirst())) {
            return relation.foreignKeyFields().getFirst();
        }

        if (registry != null) {
            Optional<ModelMetadata> targetOpt = registry.findModel(relation.targetModel());
            if (targetOpt.isPresent()) {
                ModelMetadata target = targetOpt.get();
                for (RelationMetadata inv : target.relations()) {
                    if (inv.targetModel().equalsIgnoreCase(entity.name()) && !inv.foreignKeyFields().isEmpty()) {
                        return !inv.referenceFields().isEmpty()
                                ? inv.referenceFields().getFirst()
                                : (entity instanceof ModelMetadata m && !m.primaryKeyFields().isEmpty() ? m.primaryKeyFields().getFirst() : "id");
                    }
                }

                // Check conventional field name on target: e.g. <entityNameLower>Id
                String entityFkOnTarget = entity.name().toLowerCase(Locale.ROOT) + "Id";
                if (target.hasField(entityFkOnTarget)) {
                    return entity instanceof ModelMetadata m && !m.primaryKeyFields().isEmpty() ? m.primaryKeyFields().getFirst() : "id";
                }

                // Check conventional field name on source: e.g. <targetNameLower>Id
                String targetFkOnSource = target.name().toLowerCase(Locale.ROOT) + "Id";
                if (entity.hasField(targetFkOnSource)) {
                    return targetFkOnSource;
                }
            }
        }

        // Default to primary key
        return entity instanceof ModelMetadata m && !m.primaryKeyFields().isEmpty() ? m.primaryKeyFields().getFirst() : "id";
    }
}
