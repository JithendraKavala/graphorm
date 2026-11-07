package com.graphorm.graphql.engine;

import com.graphorm.graphql.loader.GraphOrmDataLoaderRegistryFactory;
import graphql.ExecutionInput;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * GraphQL Instrumentation ensuring that a fresh, relationship-wired {@link DataLoaderRegistry}
 * is attached to every execution (TASK-11.4).
 */
public class GraphOrmDataLoaderInstrumentation extends SimplePerformantInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmDataLoaderInstrumentation.class);

    private final GraphOrmDataLoaderRegistryFactory registryFactory;

    public GraphOrmDataLoaderInstrumentation(GraphOrmDataLoaderRegistryFactory registryFactory) {
        this.registryFactory = Objects.requireNonNull(registryFactory, "GraphOrmDataLoaderRegistryFactory must not be null");
    }

    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        log.trace("Injecting fresh GraphORM DataLoaderRegistry into ExecutionInput");
        DataLoaderRegistry fresh = registryFactory.create();
        return executionInput.transform(b -> b.dataLoaderRegistry(fresh));
    }

    public GraphOrmDataLoaderRegistryFactory registryFactory() {
        return registryFactory;
    }
}
