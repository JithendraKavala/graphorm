package com.graphorm.core.query.relation;

import com.graphorm.core.exception.CircularRelationException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.QueryGuards;
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
 * Plans efficient batch queries for entity relationships, eliminating N+1 query bottlenecks.
 * Generates SQL queries using {@code WHERE target_column IN (?, ?, ...)} with cycle detection
 * and support for selective column projection and hidden join-key injection.
 */
public class RelationPlanner {

    private final ModelRegistry modelRegistry;

    /**
     * Creates a new RelationPlanner with the provided model registry.
     *
     * @param modelRegistry the model registry
     */
    public RelationPlanner(ModelRegistry modelRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "ModelRegistry must not be null");
    }

    /**
     * Factory method creating a new RelationPlanner instance.
     *
     * @param modelRegistry the model registry
     * @return a new RelationPlanner
     */
    public static RelationPlanner of(ModelRegistry modelRegistry) {
        return new RelationPlanner(modelRegistry);
    }

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
        return plan(sourceModelName, relationName, parentKeys, null);
    }

    /**
     * Plans a batched query for the specified relation on a source model with selective column projection.
     *
     * @param sourceModelName the declared source model name
     * @param relationName    the relation name on the source model
     * @param parentKeys      the collection of join keys extracted from parent entities
     * @param selectedFields  the subset of target model field names to project (null or empty for all fields)
     * @return the execution plan
     */
    public RelationPlan plan(String sourceModelName, String relationName, Collection<?> parentKeys, Collection<String> selectedFields) {
        Objects.requireNonNull(sourceModelName, "Source model name must not be null");
        Objects.requireNonNull(relationName, "Relation name must not be null");
        ModelMetadata sourceModel = modelRegistry.getModel(sourceModelName);
        RelationMetadata relation = sourceModel.getRelation(relationName);
        return plan(sourceModel, relation, parentKeys, selectedFields, null);
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
        return plan(sourceModel, relation, parentKeys, null, null);
    }

    /**
     * Plans a batched query for the specified relation metadata with selective column projection.
     *
     * @param sourceModel    the source model metadata
     * @param relation       the relation metadata
     * @param parentKeys     the collection of join keys extracted from parent entities
     * @param selectedFields the subset of target model field names to project (null or empty for all fields)
     * @return the execution plan
     */
    public RelationPlan plan(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            Collection<?> parentKeys,
            Collection<String> selectedFields
    ) {
        return plan(sourceModel, relation, parentKeys, selectedFields, null);
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
        return plan(sourceModel, relation, parentKeys, null, activePath);
    }

    /**
     * Plans a batched query with selective column projection, hidden dependency injection,
     * and circular traversal defense using an active visited path.
     *
     * @param sourceModel    the source model metadata
     * @param relation       the relation metadata
     * @param parentKeys     the collection of join keys extracted from parent entities
     * @param selectedFields the subset of target model field names to project (null or empty for all fields)
     * @param activePath     the set of entity names or relation tokens currently in the traversal stack
     * @return the execution plan
     * @throws CircularRelationException if a circular relation loop is detected
     */
    public RelationPlan plan(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            Collection<?> parentKeys,
            Collection<String> selectedFields,
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

        if (selectedFields != null && !selectedFields.isEmpty()) {
            boolean targetJoinProjected = false;
            for (String fieldName : selectedFields) {
                if (targetModel.hasField(fieldName)) {
                    FieldMetadata field = targetModel.getField(fieldName);
                    if (!field.isRelation()) {
                        queryBuilder.column(field.dbColumnName(), field.name());
                        if (field.name().equals(targetField.name()) || field.dbColumnName().equals(targetJoinColumn)) {
                            targetJoinProjected = true;
                        }
                    }
                }
            }
            // Auto-inject hidden target join column if not explicitly requested
            if (!targetJoinProjected) {
                queryBuilder.hiddenColumn(targetJoinColumn, targetField.name());
            }
        } else {
            for (FieldMetadata field : targetModel.fields()) {
                if (!field.isRelation()) {
                    queryBuilder.column(field.dbColumnName(), field.name());
                }
            }
        }

        queryBuilder.where(WherePredicate.in(targetJoinColumn, distinctKeys));

        if (targetModel.isMultiTenant()) {
            String tenantId = QueryGuards.requireTenantContext(targetModel, "SELECT");
            if (tenantId != null) {
                queryBuilder.tenantWhere(WherePredicate.equal(targetModel.tenantColumn(), tenantId));
            }
        }

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

            RelationPlan plan = plan(currentModel, relation, currentKeys, null, activePath);
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
        // Direct foreign key fields defined on relation (only if field exists on source model)
        if (!relation.foreignKeyFields().isEmpty() && sourceModel.hasField(relation.foreignKeyFields().getFirst())) {
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
