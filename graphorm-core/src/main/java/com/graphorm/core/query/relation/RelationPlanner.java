package com.graphorm.core.query.relation;

import com.graphorm.core.exception.CircularRelationException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Plans batched relationship queries for 1:1, 1:N, and N:1 relations across entities.
 * Generates unified {@link SelectQuery} ASTs targeting related tables using parameterized
 * {@code WHERE target_col IN (?, ...)} predicates and prevents infinite circular loops.
 */
public class RelationPlanner {

    private final ModelRegistry modelRegistry;

    public RelationPlanner(ModelRegistry modelRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "ModelRegistry must not be null");
    }

    /**
     * Factory method creating a new {@link RelationPlanner}.
     *
     * @param modelRegistry the model registry
     * @return a new RelationPlanner instance
     */
    public static RelationPlanner of(ModelRegistry modelRegistry) {
        return new RelationPlanner(modelRegistry);
    }

    /**
     * Returns the underlying model registry.
     *
     * @return model registry
     */
    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }

    /**
     * Plans a batched query for the specified relation on a source model.
     *
     * @param sourceModelName the declared source model name
     * @param relationName    the relation name on the source model
     * @param parentKeys      the collection of join keys extracted from parent entities
     * @return the execution plan
     */
    public RelationPlan plan(String sourceModelName, String relationName, Collection<?> parentKeys) {
        Objects.requireNonNull(sourceModelName, "Source model name must not be null");
        Objects.requireNonNull(relationName, "Relation name must not be null");
        ModelMetadata sourceModel = modelRegistry.getModel(sourceModelName);
        RelationMetadata relation = sourceModel.getRelation(relationName);
        return plan(sourceModel, relation, parentKeys, null);
    }

    /**
     * Plans a batched query for the specified relation metadata.
     *
     * @param sourceModel the source model metadata
     * @param relation    the relation metadata
     * @param parentKeys  the collection of join keys extracted from parent entities
     * @return the execution plan
     */
    public RelationPlan plan(ModelMetadata sourceModel, RelationMetadata relation, Collection<?> parentKeys) {
        return plan(sourceModel, relation, parentKeys, null);
    }

    /**
     * Plans a batched query with circular traversal defense using an active visited path.
     *
     * @param sourceModel the source model metadata
     * @param relation    the relation metadata
     * @param parentKeys  the collection of join keys extracted from parent entities
     * @param activePath  the set of entity names or relation tokens currently in the traversal stack
     * @return the execution plan
     * @throws CircularRelationException if a circular relation loop is detected
     */
    public RelationPlan plan(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            Collection<?> parentKeys,
            Set<String> activePath
    ) {
        Objects.requireNonNull(sourceModel, "Source model must not be null");
        Objects.requireNonNull(relation, "Relation metadata must not be null");

        String targetModelName = relation.targetModel();
        ModelMetadata targetModel = modelRegistry.getModel(targetModelName);

        // Circular traversal detection
        if (activePath != null && !activePath.isEmpty()) {
            String edgeToken = sourceModel.name() + "." + relation.relationName();
            if (activePath.contains(edgeToken) || activePath.contains(targetModelName)) {
                List<String> cycle = new ArrayList<>(activePath);
                cycle.add(targetModelName);
                throw new CircularRelationException(
                        "Circular relation loop detected: " + String.join(" -> ", cycle),
                        cycle
                );
            }
        }

        // Determine join field mappings
        ResolvedJoinFields joinFields = resolveJoinFields(sourceModel, relation, targetModel);

        // Deduplicate and filter null keys
        List<Object> distinctKeys = parentKeys == null
                ? List.of()
                : parentKeys.stream()
                .filter(Objects::nonNull)
                .<Object>map(k -> k)
                .distinct()
                .toList();

        // Target database column
        FieldMetadata targetField = targetModel.getField(joinFields.targetJoinField());
        String targetJoinColumn = targetField.dbColumnName();

        // Build SelectQuery AST targeting the related table
        SelectQuery.Builder queryBuilder = SelectQuery.from(targetModel.tableName());
        for (FieldMetadata field : targetModel.fields()) {
            if (!field.isRelation()) {
                queryBuilder.column(field.dbColumnName(), field.name());
            }
        }
        queryBuilder.where(WherePredicate.in(targetJoinColumn, distinctKeys));
        SelectQuery selectQuery = queryBuilder.build();

        return new RelationPlan(
                relation,
                sourceModel,
                targetModel,
                joinFields.sourceJoinField(),
                joinFields.targetJoinField(),
                targetJoinColumn,
                distinctKeys,
                selectQuery,
                relation.relationType()
        );
    }

    /**
     * Plans a multi-hop traversal path across relations, ensuring no circular loops occur.
     *
     * @param startModelName the starting model name
     * @param relationPath   the ordered list of relation names to traverse
     * @param initialKeys    initial keys on the start model
     * @return the list of sequential relation plans
     */
    public List<RelationPlan> planPath(
            String startModelName,
            List<String> relationPath,
            Collection<?> initialKeys
    ) {
        Objects.requireNonNull(startModelName, "Start model name must not be null");
        Objects.requireNonNull(relationPath, "Relation path must not be null");

        List<RelationPlan> plans = new ArrayList<>();
        Set<String> activePath = new LinkedHashSet<>();
        activePath.add(startModelName);

        ModelMetadata currentModel = modelRegistry.getModel(startModelName);
        Collection<?> currentKeys = initialKeys;

        for (String relName : relationPath) {
            RelationMetadata relation = currentModel.getRelation(relName);
            String edgeToken = currentModel.name() + "." + relation.relationName();

            if (activePath.contains(relation.targetModel()) || activePath.contains(edgeToken)) {
                List<String> cycle = new ArrayList<>(activePath);
                cycle.add(relation.targetModel());
                throw new CircularRelationException(
                        "Circular relation loop detected: " + String.join(" -> ", cycle),
                        cycle
                );
            }

            RelationPlan plan = plan(currentModel, relation, currentKeys, activePath);
            plans.add(plan);

            activePath.add(edgeToken);
            activePath.add(relation.targetModel());

            currentModel = plan.targetModel();
            currentKeys = Collections.emptyList(); // Downstream keys populated during execution
        }

        return List.copyOf(plans);
    }

    private ResolvedJoinFields resolveJoinFields(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelMetadata targetModel
    ) {
        // Direct foreign key fields defined on relation
        if (!relation.foreignKeyFields().isEmpty()) {
            String sourceFk = relation.foreignKeyFields().getFirst();
            String targetRef = !relation.referenceFields().isEmpty()
                    ? relation.referenceFields().getFirst()
                    : targetModel.primaryKeyFields().getFirst();
            return new ResolvedJoinFields(sourceFk, targetRef);
        }

        // Inverse relation on target model (common for 1:N and inverse 1:1)
        Optional<RelationMetadata> inverseRelation = targetModel.relations().stream()
                .filter(r -> r.targetModel().equals(sourceModel.name()))
                .findFirst();

        if (inverseRelation.isPresent() && !inverseRelation.get().foreignKeyFields().isEmpty()) {
            RelationMetadata inv = inverseRelation.get();
            String targetFk = inv.foreignKeyFields().getFirst();
            String sourceRef = !inv.referenceFields().isEmpty()
                    ? inv.referenceFields().getFirst()
                    : sourceModel.primaryKeyFields().getFirst();
            return new ResolvedJoinFields(sourceRef, targetFk);
        }

        // Fallback convention by field name: e.g. <sourceModelLower>Id on target model
        String sourceModelIdField = sourceModel.name().toLowerCase() + "Id";
        if (targetModel.hasField(sourceModelIdField)) {
            String sourcePk = sourceModel.primaryKeyFields().getFirst();
            return new ResolvedJoinFields(sourcePk, sourceModelIdField);
        }

        String targetModelIdField = targetModel.name().toLowerCase() + "Id";
        if (sourceModel.hasField(targetModelIdField)) {
            String targetPk = targetModel.primaryKeyFields().getFirst();
            return new ResolvedJoinFields(targetModelIdField, targetPk);
        }

        throw new IllegalArgumentException(String.format(
                "Unable to resolve join keys for relation '%s' between '%s' and '%s'.",
                relation.relationName(), sourceModel.name(), targetModel.name()
        ));
    }

    private record ResolvedJoinFields(String sourceJoinField, String targetJoinField) {}
}
