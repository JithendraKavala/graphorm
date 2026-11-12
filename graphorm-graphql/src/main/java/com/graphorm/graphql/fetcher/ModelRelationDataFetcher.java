package com.graphorm.graphql.fetcher;

import com.graphorm.jdbc.dialect.PostgreSqlDialect;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.core.tenant.TenantPartitionKey;
import com.graphorm.graphql.loader.GraphOrmDataLoaderRegistryFactory;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.jdbc.relation.BatchRelationLoader;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Generic GraphQL {@link DataFetcher} for relationship fields on models and view models (TASK-11.4).
 * Delegates to GraphQL Java {@link DataLoader} for batching foreign key / primary key lookups,
 * eliminating N+1 query bottlenecks.
 */
public class ModelRelationDataFetcher implements DataFetcher<Object> {

    private static final Logger log = LoggerFactory.getLogger(ModelRelationDataFetcher.class);

    private final ModelMetadata entity;
    private final RelationMetadata relation;
    private final ModelRegistry registry;
    private final BatchRelationLoader batchRelationLoader;
    private final String loaderKey;
    private final String sourceJoinField;
    private final String sourceDbColumn;

    public ModelRelationDataFetcher(
            ModelMetadata entity,
            RelationMetadata relation,
            ModelRegistry registry,
            BatchRelationLoader batchRelationLoader
    ) {
        this.entity = Objects.requireNonNull(entity, "Entity metadata must not be null");
        this.relation = Objects.requireNonNull(relation, "Relation metadata must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.batchRelationLoader = Objects.requireNonNull(batchRelationLoader, "BatchRelationLoader must not be null");
        this.loaderKey = GraphOrmDataLoaderRegistryFactory.loaderKey(entity.name(), relation.relationName());

        this.sourceJoinField = determineSourceJoinField(entity, relation, registry);
        FieldMetadata fm = entity.hasField(this.sourceJoinField) ? entity.getField(this.sourceJoinField) : null;
        this.sourceDbColumn = fm != null ? fm.dbColumnName() : this.sourceJoinField;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        // Enforce target entity model-level authorization (P0.3)
        EntityMetadata targetEntity = registry.findEntity(relation.targetModel()).orElse(null);
        if (targetEntity != null && targetEntity.hasAuthRoles()) {
            FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
            if (!authorizer.isAuthorized(targetEntity.authRoles())) {
                log.warn("Access denied to relation target entity '{}' via '{}.{}'. Required roles: {}, user roles: {}",
                        targetEntity.name(), entity.name(), relation.relationName(), targetEntity.authRoles(), authorizer.getCurrentRoles());
                throw new AccessDeniedException(
                        "ACCESS_DENIED",
                        "Access denied to model '" + targetEntity.name() + "' via relation '" + entity.name() + "." + relation.relationName()
                                + "'. Required roles: " + targetEntity.authRoles()
                );
            }
        }

        Object source = env.getSource();
        if (source == null) {
            return relation.relationType().isToOne()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.completedFuture(Collections.emptyList());
        }

        Object key = extractJoinKey(source);
        if (key == null) {
            log.trace("Parent join key is null for relation '{}.{}'", entity.name(), relation.relationName());
            return relation.relationType().isToOne()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.completedFuture(Collections.emptyList());
        }

        Object effectiveKey = key;
        boolean isMultiTenantRelation = entity.isMultiTenant() ||
                registry.findModel(relation.targetModel()).map(ModelMetadata::isMultiTenant).orElse(false);
        if (isMultiTenantRelation) {
            String tenantId = extractTenantId(source, entity);
            if (tenantId == null) {
                tenantId = TenantContext.getCurrentTenantId().orElse(null);
            }
            if (tenantId != null) {
                effectiveKey = TenantPartitionKey.of(tenantId, key);
            }
        }

        DataLoader<Object, Object> dataLoader = env.getDataLoader(loaderKey);
        if (dataLoader != null) {
            log.trace("Queuing DataLoader request for '{}.{}' with key: {}", entity.name(), relation.relationName(), effectiveKey);
            return dataLoader.load(effectiveKey, env.getSelectionSet());
        }

        log.debug("DataLoader '{}' not found in execution context; executing synchronously", loaderKey);
        Map<Object, Object> results = batchRelationLoader.loadByKeys(entity, relation, List.of(effectiveKey), null);
        return CompletableFuture.completedFuture(results.get(effectiveKey));
    }

    private String extractTenantId(Object source, ModelMetadata entity) {
        if (source instanceof Map<?, ?> map) {
            if (entity.tenantField() != null && map.get(entity.tenantField()) != null) {
                return String.valueOf(map.get(entity.tenantField()));
            }
            if (entity.tenantColumn() != null && map.get(entity.tenantColumn()) != null) {
                return String.valueOf(map.get(entity.tenantColumn()));
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String k) {
                    if (k.equalsIgnoreCase("tenantId") || k.equalsIgnoreCase("tenant_id")) {
                        Object val = entry.getValue();
                        if (val != null) {
                            return String.valueOf(val);
                        }
                    }
                }
            }
        }
        return null;
    }

    private Object extractJoinKey(Object source) {
        if (source instanceof Map<?, ?> map) {
            if (map.containsKey(sourceJoinField)) {
                return map.get(sourceJoinField);
            }
            if (sourceDbColumn != null && map.containsKey(sourceDbColumn)) {
                return map.get(sourceDbColumn);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String k) {
                    if (k.equalsIgnoreCase(sourceJoinField) || (sourceDbColumn != null && k.equalsIgnoreCase(sourceDbColumn))) {
                        return entry.getValue();
                    }
                }
            }
        }
        return null;
    }

    private static String determineSourceJoinField(ModelMetadata entity, RelationMetadata relation, ModelRegistry registry) {
        if (!relation.foreignKeyFields().isEmpty() && entity.hasField(relation.foreignKeyFields().getFirst())) {
            return relation.foreignKeyFields().getFirst();
        }

        if (registry != null) {
            ModelMetadata target = registry.findModel(relation.targetModel()).orElse(null);
            if (target != null) {
                for (RelationMetadata inv : target.relations()) {
                    if (inv.targetModel().equalsIgnoreCase(entity.name()) && !inv.foreignKeyFields().isEmpty()) {
                        return !inv.referenceFields().isEmpty()
                                ? inv.referenceFields().getFirst()
                                : (entity.primaryKeyFields().isEmpty() ? "id" : entity.primaryKeyFields().getFirst());
                    }
                }
            }
        }

        return entity.primaryKeyFields().isEmpty() ? "id" : entity.primaryKeyFields().getFirst();
    }

    public ModelMetadata entity() {
        return entity;
    }

    public RelationMetadata relation() {
        return relation;
    }

    public String loaderKey() {
        return loaderKey;
    }
}
