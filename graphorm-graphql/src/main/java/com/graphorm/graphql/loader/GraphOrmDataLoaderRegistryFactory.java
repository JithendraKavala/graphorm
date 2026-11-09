package com.graphorm.graphql.loader;

import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.relation.BatchRelationLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Factory for creating configured {@link DataLoaderRegistry} instances with auto-registered
 * relationship {@link BatchRelationDataLoader} instances for every relation across all schema models (TASK-11.4).
 * Supports optional virtual thread execution for DataLoader batch hydration.
 */
public class GraphOrmDataLoaderRegistryFactory {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmDataLoaderRegistryFactory.class);

    private final ModelRegistry registry;
    private final QueryExecutionEngine executionEngine;
    private final SqlDialect dialect;
    private final BatchRelationLoader batchRelationLoader;
    private DataLoaderMetricsListener metricsListener = DataLoaderMetricsListener.NO_OP;
    private int maxBatchSize = 1000;
    private Executor asyncExecutor;

    public GraphOrmDataLoaderRegistryFactory(
            ModelRegistry registry,
            QueryExecutionEngine executionEngine,
            SqlDialect dialect
    ) {
        this(registry, executionEngine, dialect, DataLoaderMetricsListener.NO_OP);
    }

    public GraphOrmDataLoaderRegistryFactory(
            ModelRegistry registry,
            QueryExecutionEngine executionEngine,
            SqlDialect dialect,
            DataLoaderMetricsListener metricsListener
    ) {
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.executionEngine = Objects.requireNonNull(executionEngine, "QueryExecutionEngine must not be null");
        this.dialect = dialect != null ? dialect : (executionEngine instanceof JdbcExecutor je ? je.dialect() : new PostgreSqlDialect());
        this.batchRelationLoader = new BatchRelationLoader(executionEngine, registry);
        this.metricsListener = metricsListener != null ? metricsListener : DataLoaderMetricsListener.NO_OP;
    }

    public GraphOrmDataLoaderRegistryFactory maxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
        return this;
    }

    public GraphOrmDataLoaderRegistryFactory metricsListener(DataLoaderMetricsListener metricsListener) {
        this.metricsListener = metricsListener != null ? metricsListener : DataLoaderMetricsListener.NO_OP;
        return this;
    }

    public GraphOrmDataLoaderRegistryFactory asyncExecutor(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
        return this;
    }

    public Executor asyncExecutor() {
        return asyncExecutor;
    }

    /**
     * Standard naming format for relationship DataLoaders.
     *
     * @param entityName   the declaring model or view name (e.g. "Student")
     * @param relationName the relationship field name (e.g. "department")
     * @return the loader key string (e.g. "Student.department")
     */
    public static String loaderKey(String entityName, String relationName) {
        return entityName + "." + relationName;
    }

    /**
     * Creates a new, isolated {@link DataLoaderRegistry} instance suitable for a single GraphQL execution.
     *
     * @return fresh DataLoaderRegistry populated with relationship batch loaders
     */
    public DataLoaderRegistry create() {
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        registerInto(dataLoaderRegistry);
        return dataLoaderRegistry;
    }

    /**
     * Registers relationship {@link DataLoader} instances for all models into an existing {@link DataLoaderRegistry}.
     *
     * @param targetRegistry the target registry
     */
    public void registerInto(DataLoaderRegistry targetRegistry) {
        Objects.requireNonNull(targetRegistry, "Target DataLoaderRegistry must not be null");

        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setBatchingEnabled(true)
                .setCachingEnabled(true)
                .setMaxBatchSize(maxBatchSize);

        for (ModelMetadata model : registry.getAllModels()) {
            for (RelationMetadata relation : model.relations()) {
                String key = loaderKey(model.name(), relation.relationName());
                if (targetRegistry.getDataLoader(key) == null) {
                    BatchRelationDataLoader batchLoader = new BatchRelationDataLoader(
                            model,
                            relation,
                            registry,
                            batchRelationLoader,
                            metricsListener,
                            asyncExecutor
                    );
                    DataLoader<Object, Object> dataLoader = DataLoaderFactory.newMappedDataLoader(batchLoader, options);
                    targetRegistry.register(key, dataLoader);
                    log.debug("Registered DataLoader '{}' for relation {}.{}", key, model.name(), relation.relationName());
                }
            }
        }
    }

    public ModelRegistry registry() {
        return registry;
    }

    public QueryExecutionEngine executionEngine() {
        return executionEngine;
    }

    public JdbcExecutor executor() {
        return executionEngine instanceof JdbcExecutor je ? je : null;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public DataLoaderMetricsListener metricsListener() {
        return metricsListener;
    }

    public int maxBatchSize() {
        return maxBatchSize;
    }

    public BatchRelationLoader batchRelationLoader() {
        return batchRelationLoader;
    }
}
