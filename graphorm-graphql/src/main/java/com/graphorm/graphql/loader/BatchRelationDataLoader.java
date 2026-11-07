package com.graphorm.graphql.loader;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.graphql.planner.FieldTreeNode;
import com.graphorm.graphql.planner.SelectionSetAnalyzer;
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

/**
 * Mapped batch loader integrating GraphQL Java DataLoader with GraphORM's {@link BatchRelationLoader} (TASK-11.4).
 * Supports selective column projection for related models based on child selection sets across the batch.
 */
public class BatchRelationDataLoader implements MappedBatchLoaderWithContext<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(BatchRelationDataLoader.class);

    private final ModelMetadata sourceModel;
    private final RelationMetadata relation;
    private final ModelRegistry registry;
    private final BatchRelationLoader batchRelationLoader;

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            JdbcExecutor executor,
            SqlDialect dialect
    ) {
        this.sourceModel = Objects.requireNonNull(sourceModel, "Source model must not be null");
        this.relation = Objects.requireNonNull(relation, "Relation metadata must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.batchRelationLoader = new BatchRelationLoader(executor, registry);
    }

    public BatchRelationDataLoader(
            ModelMetadata sourceModel,
            RelationMetadata relation,
            ModelRegistry registry,
            BatchRelationLoader batchRelationLoader
    ) {
        this.sourceModel = Objects.requireNonNull(sourceModel, "Source model must not be null");
        this.relation = Objects.requireNonNull(relation, "Relation metadata must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.batchRelationLoader = Objects.requireNonNull(batchRelationLoader, "BatchRelationLoader must not be null");
    }

    @Override
    public CompletionStage<Map<Object, Object>> load(Set<Object> keys, BatchLoaderEnvironment environment) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        log.debug("Batch loading relation '{}.{}' for {} keys", sourceModel.name(), relation.relationName(), keys.size());

        // Extract selective projection fields from child selection sets
        Set<String> selectedFields = extractSelectedFields(environment);

        try {
            Map<Object, Object> results = batchRelationLoader.loadByKeys(
                    sourceModel,
                    relation,
                    keys,
                    selectedFields
            );
            return CompletableFuture.completedFuture(results);
        } catch (Exception e) {
            log.error("Failed to batch load relation '{}.{}': {}", sourceModel.name(), relation.relationName(), e.getMessage(), e);
            CompletableFuture<Map<Object, Object>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
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
            } else if (context instanceof Collection<?> col) {
                for (Object item : col) {
                    if (item instanceof String s) {
                        fields.add(s);
                    }
                }
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
}
