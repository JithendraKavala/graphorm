package com.graphorm.graphql.loader;

import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.graphql.planner.FieldTreeNode;
import com.graphorm.graphql.planner.SelectionSetAnalyzer;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.relation.BatchRelationLoader;
import graphql.schema.DataFetchingFieldSelectionSet;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.MappedBatchLoaderWithContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Mapped batch loader integrating GraphQL Java DataLoader with GraphORM's {@link BatchRelationLoader} (TASK-11.4).
 * Supports selective column projection for related models based on child selection sets across the batch
 * and records batch size and execution duration via {@link DataLoaderMetricsListener}.
 * Concurrency hardened with optional virtual thread async dispatching.
 */
public class BatchRelationDataLoader implements MappedBatchLoaderWithContext<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(BatchRelationDataLoader.class);

    private final ModelMetadata sourceModel;
    private final RelationMetadata relation;
    private final ModelRegistry registry;
    private final BatchRelationLoader batchRelationLoader;
    private final DataLoaderMetricsListener metricsListener;
    private final Executor asyncExecutor;

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            QueryExecutionEngine executionEngine,
            SqlDialect dialect
    ) {
        this(sourceModel, relation, registry, executionEngine, dialect, DataLoaderMetricsListener.NO_OP, null);
    }

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            QueryExecutionEngine executionEngine,
            SqlDialect dialect,
            DataLoaderMetricsListener metricsListener
    ) {
        this(sourceModel, relation, registry, executionEngine, dialect, metricsListener, null);
    }

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            QueryExecutionEngine executionEngine,
            SqlDialect dialect,
            DataLoaderMetricsListener metricsListener,
            Executor asyncExecutor
    ) {
        this(sourceModel, relation, registry, new BatchRelationLoader(executionEngine, registry), metricsListener, asyncExecutor);
    }

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            BatchRelationLoader batchRelationLoader
    ) {
        this(sourceModel, relation, registry, batchRelationLoader, DataLoaderMetricsListener.NO_OP, null);
    }

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            BatchRelationLoader batchRelationLoader,
            DataLoaderMetricsListener metricsListener
    ) {
        this(sourceModel, relation, registry, batchRelationLoader, metricsListener, null);
    }

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            BatchRelationLoader batchRelationLoader,
            DataLoaderMetricsListener metricsListener,
            Executor asyncExecutor
    ) {
        this.sourceModel = Objects.requireNonNull(sourceModel, "Source model must not be null");
        this.relation = Objects.requireNonNull(relation, "Relation metadata must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.batchRelationLoader = Objects.requireNonNull(batchRelationLoader, "BatchRelationLoader must not be null");
        this.metricsListener = metricsListener != null ? metricsListener : DataLoaderMetricsListener.NO_OP;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public CompletionStage<Map<Object, Object>> load(Set<Object> keys, BatchLoaderEnvironment environment) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        log.debug("Batch loading relation '{}.{}' for {} keys", sourceModel.name(), relation.relationName(), keys.size());

        Set<String> selectedFields = extractSelectedFields(environment);

        if (asyncExecutor != null) {
            return CompletableFuture.supplyAsync(() -> doBatchLoad(keys, selectedFields), asyncExecutor);
        } else {
            try {
                return CompletableFuture.completedFuture(doBatchLoad(keys, selectedFields));
            } catch (Exception e) {
                CompletableFuture<Map<Object, Object>> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(e);
                return failedFuture;
            }
        }
    }

    private Map<Object, Object> doBatchLoad(Set<Object> keys, Set<String> selectedFields) {
        long start = System.nanoTime();
        try {
            Map<Object, Object> results = batchRelationLoader.loadByKeys(
                    sourceModel,
                    relation,
                    keys,
                    selectedFields
            );
            long duration = System.nanoTime() - start;
            metricsListener.onBatchLoaded(sourceModel.name(), relation.relationName(), keys.size(), duration);
            return results;
        } catch (Exception e) {
            long duration = System.nanoTime() - start;
            metricsListener.onBatchLoaded(sourceModel.name(), relation.relationName(), keys.size(), duration);
            log.error("Failed to batch load relation '{}.{}': {}", sourceModel.name(), relation.relationName(), e.getMessage(), e);
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }

    private Set<String> extractSelectedFields(BatchLoaderEnvironment environment) {
        if (environment == null || environment.getKeyContexts() == null || environment.getKeyContexts().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> fields = new LinkedHashSet<>();
        for (Object context : environment.getKeyContexts().values()) {
            if (context instanceof DataFetchingFieldSelectionSet selectionSet) {
                Map<String, FieldTreeNode> tree = SelectionSetAnalyzer.extractTree(selectionSet);
                fields.addAll(tree.keySet());
            }
        }
        return fields;
    }

    public ModelMetadata sourceModel() {
        return sourceModel;
    }

    public RelationMetadata relation() {
        return relation;
    }

    public Executor asyncExecutor() {
        return asyncExecutor;
    }
}
