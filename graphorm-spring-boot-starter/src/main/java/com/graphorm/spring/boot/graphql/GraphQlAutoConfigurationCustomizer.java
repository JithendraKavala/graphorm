package com.graphorm.spring.boot.graphql;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.engine.GraphOrmDataLoaderInstrumentation;
import com.graphorm.graphql.engine.GraphOrmGraphQlRegistrar;
import com.graphorm.graphql.loader.GraphOrmDataLoaderRegistryFactory;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.graphql.execution.GraphQlSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Customizer hooking into Spring for GraphQL (Boot 3) auto-configuration to discover and register
 * GraphORM DataFetchers, DataLoaders, and type definitions without manual wiring (TASK-13.4).
 */
public class GraphQlAutoConfigurationCustomizer implements GraphQlSourceBuilderCustomizer {

    private static final Logger log = LoggerFactory.getLogger(GraphQlAutoConfigurationCustomizer.class);

    private final ModelRegistry modelRegistry;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final GraphOrmProperties properties;
    private final ArgumentBinder argumentBinder;

    public GraphQlAutoConfigurationCustomizer(
            ModelRegistry modelRegistry,
            JdbcExecutor executor,
            SqlDialect dialect,
            GraphOrmProperties properties,
            ArgumentBinder argumentBinder
    ) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "ModelRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.properties = Objects.requireNonNull(properties, "GraphOrmProperties must not be null");
        this.argumentBinder = argumentBinder != null ? argumentBinder : new ArgumentBinder();
    }

    public GraphQlAutoConfigurationCustomizer(
            ModelRegistry modelRegistry,
            JdbcExecutor executor,
            SqlDialect dialect,
            GraphOrmProperties properties
    ) {
        this(modelRegistry, executor, dialect, properties, new ArgumentBinder());
    }

    @Override
    public void customize(GraphQlSource.SchemaResourceBuilder builder) {
        log.info("Applying GraphORM GraphQL auto-configuration customizer");

        final AtomicReference<TypeDefinitionRegistry> registryRef = new AtomicReference<>();

        // 1. Configure Type Definitions (synthesizing missing schema types from ModelRegistry)
        builder.configureTypeDefinitions(typeRegistry -> {
            mergeGraphOrmTypeDefinitions(typeRegistry);
            registryRef.set(typeRegistry);
        });

        // 2. Configure Runtime Wiring (auto-registering generic DataFetchers)
        builder.configureRuntimeWiring(wiringBuilder -> {
            TypeDefinitionRegistry typeRegistry = registryRef.get();
            if (typeRegistry != null && properties.getGraphql().isAutoFetchers()) {
                GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(
                        modelRegistry,
                        executor,
                        dialect,
                        argumentBinder
                );
                registrar.failFastOnViewMutation(properties.getGraphql().isFailFastOnViewMutation());
                registrar.register(wiringBuilder, typeRegistry);
            }
        });

        // 3. Register DataLoader Instrumentation for relationship batching
        GraphOrmDataLoaderRegistryFactory factory = new GraphOrmDataLoaderRegistryFactory(modelRegistry, executor, dialect);
        builder.instrumentation(List.of(new GraphOrmDataLoaderInstrumentation(factory)));
    }

    private void mergeGraphOrmTypeDefinitions(TypeDefinitionRegistry target) {
        String generatedSdl = GraphQlSdlGenerator.generate(modelRegistry);
        if (generatedSdl.isBlank()) {
            return;
        }

        try {
            TypeDefinitionRegistry generatedRegistry = new SchemaParser().parse(new StringReader(generatedSdl));

            for (TypeDefinition<?> def : generatedRegistry.types().values()) {
                if (!target.hasType(new TypeName(def.getName()))) {
                    target.add(def);
                } else if (def instanceof ObjectTypeDefinition genObj && target.getType(def.getName()).orElse(null) instanceof ObjectTypeDefinition targetObj) {
                    // Merge fields for Query and Mutation if present
                    if ("Query".equals(def.getName()) || "Mutation".equals(def.getName())) {
                        mergeFields(target, targetObj, genObj);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not merge generated GraphQL type definitions: {}", e.getMessage(), e);
        }
    }

    private void mergeFields(TypeDefinitionRegistry target, ObjectTypeDefinition targetObj, ObjectTypeDefinition genObj) {
        List<FieldDefinition> targetFields = new ArrayList<>(targetObj.getFieldDefinitions());
        boolean modified = false;

        for (FieldDefinition genField : genObj.getFieldDefinitions()) {
            boolean alreadyPresent = targetFields.stream()
                    .anyMatch(f -> f.getName().equals(genField.getName()));
            if (!alreadyPresent) {
                targetFields.add(genField);
                modified = true;
            }
        }

        if (modified) {
            ObjectTypeDefinition newObj = targetObj.transform(b -> b.fieldDefinitions(targetFields));
            target.remove(targetObj);
            target.add(newObj);
        }
    }
}
