package com.graphorm.jdbc.relation;

import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.query.relation.RelationPlan;
import com.graphorm.core.query.relation.RelationPlanner;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.mapper.DynamicRowMapper;
import com.graphorm.jdbc.mapper.RowMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Batched relationship loading engine executing {@code WHERE target_col IN (?, ...)} queries
 * and correlating child entities back to their corresponding parents in memory with zero N+1 queries.
 * Supports selective column projection, hidden dependency foreign key injection, and decoupled
 * {@link QueryExecutionEngine} execution.
 * Hardened for Virtual Threads (Project Loom) using {@link ReentrantLock} to avoid carrier thread pinning.
 */
public class BatchRelationLoader {

    private final QueryExecutionEngine executionEngine;
    private final RelationPlanner relationPlanner;

    @FunctionalInterface
    private interface PropertyAccessor {
        Object get(Object target) throws Exception;
    }

    private record AccessorKey(Class<?> targetClass, String fieldName, String dbColumnName) {}

    private static final Map<AccessorKey, PropertyAccessor> ACCESSOR_CACHE = new HashMap<>();
    private static final ReentrantLock ACCESSOR_LOCK = new ReentrantLock();

    public BatchRelationLoader(QueryExecutionEngine executionEngine, RelationPlanner relationPlanner) {
        this.executionEngine = Objects.requireNonNull(executionEngine, "QueryExecutionEngine must not be null");
        this.relationPlanner = Objects.requireNonNull(relationPlanner, "RelationPlanner must not be null");
    }

    public BatchRelationLoader(QueryExecutionEngine executionEngine, ModelRegistry modelRegistry) {
        this(executionEngine, new RelationPlanner(modelRegistry));
    }

    public BatchRelationLoader(JdbcExecutor jdbcExecutor, RelationPlanner relationPlanner) {
        this((QueryExecutionEngine) jdbcExecutor, relationPlanner);
    }

    public BatchRelationLoader(JdbcExecutor jdbcExecutor, ModelRegistry modelRegistry) {
        this((QueryExecutionEngine) jdbcExecutor, new RelationPlanner(modelRegistry));
    }

    public JdbcExecutor jdbcExecutor() {
        return executionEngine instanceof JdbcExecutor je ? je : null;
    }

    public QueryExecutionEngine executionEngine() {
        return executionEngine;
    }

    public RelationPlanner relationPlanner() {
        return relationPlanner;
    }

    /**
     * Loads related child entities for a list of parent entities using a child class.
     *
     * @param sourceModelName declared name of the source parent model
     * @param relationName    name of the relation on the source model
     * @param parents         collection of parent entities
     * @param childClass      the Java record, class, or Map type for children
     * @param <P>             parent type
     * @param <C>             child type
     * @return batch load result with mapped associations
     */
    public <P, C> BatchLoadResult<P, C> load(
            String sourceModelName,
            String relationName,
            List<P> parents,
            Class<C> childClass
    ) {
        return load(sourceModelName, relationName, parents, childClass, null);
    }

    /**
     * Loads related child entities for a list of parent entities using a child class with selective projection.
     *
     * @param sourceModelName declared name of the source parent model
     * @param relationName    name of the relation on the source model
     * @param parents         collection of parent entities
     * @param childClass      the Java record, class, or Map type for children
     * @param selectedFields  selective field names to project (null for all fields)
     * @param <P>             parent type
     * @param <C>             child type
     * @return batch load result with mapped associations
     */
    @SuppressWarnings("unchecked")
    public <P, C> BatchLoadResult<P, C> load(
            String sourceModelName,
            String relationName,
            List<P> parents,
            Class<C> childClass,
            Collection<String> selectedFields
    ) {
        Objects.requireNonNull(sourceModelName, "Source model name must not be null");
        Objects.requireNonNull(relationName, "Relation name must not be null");
        Objects.requireNonNull(childClass, "Child class must not be null");

        if (parents == null || parents.isEmpty()) {
            return BatchLoadResult.empty();
        }

        ModelMetadata sourceModel = relationPlanner.modelRegistry().getModel(sourceModelName);
        RelationMetadata relation = sourceModel.getRelation(relationName);

        RowMapper<C> childMapper = Map.class.isAssignableFrom(childClass)
                ? (RowMapper<C>) DynamicRowMapper.mapToMap()
                : DynamicRowMapper.mapToRecord(childClass);

        return load(sourceModel, relation, parents, childMapper, selectedFields);
    }

    /**
     * Loads related child entities using a custom RowMapper.
     *
     * @param sourceModel the source model metadata
     * @param relation    the relation metadata
     * @param parents     collection of parent entities
     * @param childMapper custom row mapper for children
     * @param <P>         parent type
     * @param <C>         child type
     * @return batch load result with mapped associations
     */
    public <P, C> BatchLoadResult<P, C> load(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            List<P> parents,
            RowMapper<C> childMapper
    ) {
        return load(sourceModel, relation, parents, childMapper, null);
    }

    /**
     * Loads related child entities using a custom RowMapper with selective column projection.
     *
     * @param sourceModel    the source model metadata
     * @param relation       the relation metadata
     * @param parents        collection of parent entities
     * @param childMapper    custom row mapper for children
     * @param selectedFields selective target field names to project (null for all fields)
     * @param <P>            parent type
     * @param <C>            child type
     * @return batch load result with mapped associations
     */
    @SuppressWarnings("unchecked")
    public <P, C> BatchLoadResult<P, C> load(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            List<P> parents,
            RowMapper<C> childMapper,
            Collection<String> selectedFields
    ) {
        Objects.requireNonNull(sourceModel, "Source model must not be null");
        Objects.requireNonNull(relation, "Relation metadata must not be null");
        Objects.requireNonNull(childMapper, "Child row mapper must not be null");

        if (parents == null || parents.isEmpty()) {
            return BatchLoadResult.empty();
        }

        // Determine source join field preliminary to extract parent keys
        String sourceJoinField = determineSourceJoinField(sourceModel, relation);
        FieldMetadata sourceFieldMeta = sourceModel.hasField(sourceJoinField) ? sourceModel.getField(sourceJoinField) : null;
        String sourceColName = sourceFieldMeta != null ? sourceFieldMeta.dbColumnName() : sourceJoinField;

        // Extract and deduplicate parent keys
        List<Object> parentKeys = new ArrayList<>();
        for (P parent : parents) {
            Object key = extractFieldValue(parent, sourceJoinField, sourceColName);
            if (key != null) {
                parentKeys.add(key);
            }
        }

        if (parentKeys.isEmpty()) {
            return BatchLoadResult.empty();
        }

        // Generate batched RelationPlan
        RelationPlan plan = relationPlanner.plan(sourceModel, relation, parentKeys, selectedFields);
        if (plan.keys().isEmpty()) {
            return BatchLoadResult.empty();
        }

        // Execute batched SQL query
        List<C> children;
        if (executionEngine instanceof JdbcExecutor je) {
            children = je.queryForList(plan.selectQuery(), childMapper);
        } else {
            List<Map<String, Object>> rows = executionEngine.queryForList(plan.selectQuery());
            children = (List<C>) rows;
        }

        // Group children by normalized join key
        Map<Object, List<C>> childrenByKey = new LinkedHashMap<>();
        for (C child : children) {
            Object childKey = extractFieldValue(child, plan.targetJoinField(), plan.targetJoinColumn());
            Object normKey = normalizeKey(childKey);
            if (normKey != null) {
                childrenByKey.computeIfAbsent(normKey, k -> new ArrayList<>()).add(child);
            }
        }

        // Correlate children back to parent instances
        Map<P, C> oneToOne = new LinkedHashMap<>();
        Map<P, List<C>> oneToMany = new LinkedHashMap<>();

        for (P parent : parents) {
            Object parentKey = extractFieldValue(parent, plan.sourceJoinField(), sourceColName);
            Object normParentKey = normalizeKey(parentKey);
            List<C> matching = normParentKey != null
                    ? childrenByKey.getOrDefault(normParentKey, List.of())
                    : List.of();

            if (plan.isToOne()) {
                if (!matching.isEmpty()) {
                    oneToOne.put(parent, matching.getFirst());
                }
            } else {
                oneToMany.put(parent, matching);
            }
        }

        return new BatchLoadResult<>(oneToOne, oneToMany, childrenByKey);
    }

    /**
     * Loads children directly by their join key values, returning a map of Key -> Result.
     * Ideal for DataLoader integration where parent join keys are collected in batches.
     *
     * @param sourceModel    the source model
     * @param relation       the relation metadata
     * @param parentKeys     the collection of parent join keys
     * @param selectedFields selective target field names to project (null for all fields)
     * @return map from original parent key to either a single child (for to-one) or a List of children (for to-many)
     */
    public Map<Object, Object> loadByKeys(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            Collection<?> parentKeys,
            Collection<String> selectedFields
    ) {
        Objects.requireNonNull(sourceModel, "Source model must not be null");
        Objects.requireNonNull(relation, "Relation metadata must not be null");

        if (parentKeys == null || parentKeys.isEmpty()) {
            return Collections.emptyMap();
        }

        RelationPlan plan = relationPlanner.plan(sourceModel, relation, parentKeys, selectedFields);
        if (plan.keys().isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> children = executionEngine.queryForList(plan.selectQuery());

        // Group children by normalized join key
        Map<Object, List<Map<String, Object>>> childrenByKey = new LinkedHashMap<>();
        for (Map<String, Object> child : children) {
            Object childKey = extractFieldValue(child, plan.targetJoinField(), plan.targetJoinColumn());
            Object normKey = normalizeKey(childKey);
            if (normKey != null) {
                childrenByKey.computeIfAbsent(normKey, k -> new ArrayList<>()).add(child);
            }
        }

        Map<Object, Object> resultMap = new LinkedHashMap<>();
        boolean isToOne = plan.isToOne();

        for (Object key : parentKeys) {
            if (key == null) {
                continue;
            }
            Object normKey = normalizeKey(key);
            List<Map<String, Object>> matching = childrenByKey.getOrDefault(normKey, List.of());
            if (isToOne) {
                resultMap.put(key, matching.isEmpty() ? null : matching.getFirst());
            } else {
                resultMap.put(key, matching);
            }
        }

        return resultMap;
    }

    /**
     * Convenience method loading a to-one relation (1:1 or N:1) returning a Map of Parent -> Child.
     *
     * @param sourceModelName source model name
     * @param relationName    relation name
     * @param parents         list of parent instances
     * @param childClass      child class
     * @param <P>             parent type
     * @param <C>             child type
     * @return map of parent to single child entity
     */
    public <P, C> Map<P, C> loadOneToOne(
            String sourceModelName,
            String relationName,
            List<P> parents,
            Class<C> childClass
    ) {
        return load(sourceModelName, relationName, parents, childClass).oneToOne();
    }

    /**
     * Convenience method loading a to-many relation (1:N or M:N) returning a Map of Parent -> List of Children.
     *
     * @param sourceModelName source model name
     * @param relationName    relation name
     * @param parents         list of parent instances
     * @param childClass      child class
     * @param <P>             parent type
     * @param <C>             child type
     * @return map of parent to list of child entities
     */
    public <P, C> Map<P, List<C>> loadOneToMany(
            String sourceModelName,
            String relationName,
            List<P> parents,
            Class<C> childClass
    ) {
        return load(sourceModelName, relationName, parents, childClass).oneToMany();
    }

    /**
     * Loads related child maps and attaches them directly into mutable parent maps under {@code relationName}.
     *
     * @param sourceModelName source model name
     * @param relationName    relation name
     * @param parents         list of parent maps
     */
    @SuppressWarnings("unchecked")
    public void loadAndAttach(
            String sourceModelName,
            String relationName,
            List<Map<String, Object>> parents
    ) {
        if (parents == null || parents.isEmpty()) {
            return;
        }

        ModelMetadata sourceModel = relationPlanner.modelRegistry().getModel(sourceModelName);
        RelationMetadata relation = sourceModel.getRelation(relationName);

        BatchLoadResult<Map<String, Object>, Map<String, Object>> result =
                (BatchLoadResult<Map<String, Object>, Map<String, Object>>) (BatchLoadResult<?, ?>)
                        load(sourceModelName, relationName, parents, Map.class);

        boolean isToOne = relation.relationType().isToOne();
        for (Map<String, Object> parent : parents) {
            if (isToOne) {
                parent.put(relationName, result.getOne(parent).orElse(null));
            } else {
                parent.put(relationName, result.getMany(parent));
            }
        }
    }

    private String determineSourceJoinField(ModelMetadata sourceModel, RelationMetadata relation) {
        if (!relation.foreignKeyFields().isEmpty() && sourceModel.hasField(relation.foreignKeyFields().getFirst())) {
            return relation.foreignKeyFields().getFirst();
        }
        ModelMetadata targetModel = relationPlanner.modelRegistry().getModel(relation.targetModel());
        var inverseRelation = targetModel.relations().stream()
                .filter(r -> r.targetModel().equals(sourceModel.name()))
                .findFirst();

        if (inverseRelation.isPresent() && !inverseRelation.get().referenceFields().isEmpty()) {
            return inverseRelation.get().referenceFields().getFirst();
        }
        return sourceModel.primaryKeyFields().getFirst();
    }

    private static Object normalizeKey(Object key) {
        if (key == null) {
            return null;
        }
        if (key instanceof Number num) {
            return num.longValue();
        }
        if (key instanceof UUID uuid) {
            return uuid.toString().toLowerCase(Locale.ROOT);
        }
        if (key instanceof CharSequence cs) {
            String str = cs.toString().trim();
            try {
                return UUID.fromString(str).toString().toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException ignored) {
                return str;
            }
        }
        return key;
    }

    private static PropertyAccessor getAccessor(Class<?> clazz, String fieldName, String dbColumnName) {
        AccessorKey key = new AccessorKey(clazz, fieldName, dbColumnName);
        ACCESSOR_LOCK.lock();
        try {
            PropertyAccessor existing = ACCESSOR_CACHE.get(key);
            if (existing != null) {
                return existing;
            }
            PropertyAccessor resolved = resolveAccessor(clazz, fieldName, dbColumnName);
            ACCESSOR_CACHE.put(key, resolved);
            return resolved;
        } finally {
            ACCESSOR_LOCK.unlock();
        }
    }

    private static PropertyAccessor resolveAccessor(Class<?> clazz, String fieldName, String dbColumnName) {
        if (clazz.isRecord()) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                if (rc.getName().equals(fieldName) || (dbColumnName != null && rc.getName().equalsIgnoreCase(dbColumnName))) {
                    Method accessor = rc.getAccessor();
                    accessor.setAccessible(true);
                    return accessor::invoke;
                }
            }
        }

        String capitalized = fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1);
        String[] candidateMethodNames = {"get" + capitalized, fieldName, "is" + capitalized};
        for (String mName : candidateMethodNames) {
            try {
                Method m = clazz.getMethod(mName);
                m.setAccessible(true);
                return m::invoke;
            } catch (NoSuchMethodException ignored) {
            }
        }

        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field::get;
        } catch (NoSuchFieldException ignored) {
        }

        return target -> null;
    }

    private static Object extractFieldValue(Object entity, String fieldName, String dbColumnName) {
        if (entity == null) {
            return null;
        }

        // 1. If Map
        if (entity instanceof Map<?, ?> map) {
            if (map.containsKey(fieldName)) {
                return map.get(fieldName);
            }
            if (dbColumnName != null && map.containsKey(dbColumnName)) {
                return map.get(dbColumnName);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String k) {
                    if (k.equalsIgnoreCase(fieldName) || (dbColumnName != null && k.equalsIgnoreCase(dbColumnName))) {
                        return entry.getValue();
                    }
                }
            }
            return null;
        }

        // 2. Cached POJO / Record Accessor (Hardened against carrier thread pinning)
        try {
            PropertyAccessor accessor = getAccessor(entity.getClass(), fieldName, dbColumnName);
            return accessor.get(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access property '" + fieldName + "' on " + entity.getClass().getName(), e);
        }
    }
}
