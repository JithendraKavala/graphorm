package com.graphorm.graphql.loader;

import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
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

/**
 * Factory for creating configured per-request {@link DataLoaderRegistry} instances
 * pre-wired with {@link BatchRelationDataLoader} instances for every entity relationship (TASK-11.4).
 */
public class GraphOrmDataLoaderRegistryFactory {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmDataLoaderRegistryFactory.class);

    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final BatchRelationLoader batchRelationLoader;
    private DataLoaderMetricsListener metricsListener = DataLoaderMetricsListener.NO_OP;
    private int maxBatchSize = 1000;

    public GraphOrmDataLoaderRegistryFactory(
            ModelRegistry registry,
            JdbcExecutor executor,
            SqlDialect dialect
    ) {
        this(registry, executor, dialect, DataLoaderMetricsListener.NO_OP);
    }

    public GraphOrmDataLoaderRegistryFactory(
            ModelRegistry registry,
            JdbcExecutor executor,
            SqlDialect dialect,
            DataLoaderMetricsListener metricsListener
    ) {
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = dialect != null ? dialect : executor.dialect();
        this.batchRelationLoader = new BatchRelationLoader(executor, registry);
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
                            metricsListener
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

    public JdbcExecutor executor() {
        return executor;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public DataLoaderMetricsListener metricsListener() {
        return metricsListener;
    }
}
