package com.graphorm.spring.boot.graphql;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.engine.GraphOrmDataLoaderInstrumentation;
import com.graphorm.graphql.engine.GraphOrmGraphQlRegistrar;
import com.graphorm.graphql.loader.DataLoaderMetricsListener;
import com.graphorm.graphql.loader.GraphOrmDataLoaderRegistryFactory;
import com.graphorm.graphql.security.AuthDirectiveWiring;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.graphql.security.MaxQueryComplexityInstrumentation;
import com.graphorm.graphql.security.MaxQueryDepthInstrumentation;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import graphql.execution.instrumentation.Instrumentation;
import graphql.language.DirectiveDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Customizer hooking into Spring for GraphQL (Boot 3) auto-configuration to discover and register
 * GraphORM DataFetchers, DataLoaders, security instrumentation, and type definitions without manual wiring.
 */
public class GraphQlAutoConfigurationCustomizer implements GraphQlSourceBuilderCustomizer {

    private static final Logger log = LoggerFactory.getLogger(GraphQlAutoConfigurationCustomizer.class);

    private final ModelRegistry modelRegistry;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final GraphOrmProperties properties;
    private final ArgumentBinder argumentBinder;
    private final FieldAuthorizer fieldAuthorizer;
    private final DataLoaderMetricsListener metricsListener;

    public GraphQlAutoConfigurationCustomizer(
            ModelRegistry modelRegistry,
            JdbcExecutor executor,
            SqlDialect dialect,
            GraphOrmProperties properties,
            ArgumentBinder argumentBinder,
            FieldAuthorizer fieldAuthorizer,
            DataLoaderMetricsListener metricsListener
    ) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "ModelRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.properties = Objects.requireNonNull(properties, "GraphOrmProperties must not be null");
        this.argumentBinder = argumentBinder != null ? argumentBinder : new ArgumentBinder();
        this.fieldAuthorizer = fieldAuthorizer;
        this.metricsListener = metricsListener;
    }

    public static boolean hasAuthDirectives(ModelRegistry modelRegistry) {
        if (modelRegistry == null) {
            return false;
        }
        boolean inModelFields = modelRegistry.getAllModels().stream()
                .flatMap(m -> m.fields().stream())
                .anyMatch(f -> f.authRoles() != null && !f.authRoles().isEmpty());
        boolean inViewFields = modelRegistry.getAllViewModels().stream()
                .flatMap(v -> v.fields().stream())
                .anyMatch(f -> f.authRoles() != null && !f.authRoles().isEmpty());
        boolean inModels = modelRegistry.getAllModels().stream()
                .anyMatch(m -> m.authRoles() != null && !m.authRoles().isEmpty());
        boolean inViews = modelRegistry.getAllViewModels().stream()
                .anyMatch(v -> v.authRoles() != null && !v.authRoles().isEmpty());
        return inModelFields || inViewFields || inModels || inViews;
    }

    public static boolean hasAuthDirectives(TypeDefinitionRegistry registry) {
        if (registry == null) {
            return false;
        }
        for (TypeDefinition<?> typeDef : registry.types().values()) {
            if (typeDef instanceof ObjectTypeDefinition otd) {
                for (FieldDefinition fd : otd.getFieldDefinitions()) {
                    if (fd.hasDirective("auth")) {
                        return true;
                    }
                }
            } else if (typeDef instanceof InterfaceTypeDefinition itd) {
                for (FieldDefinition fd : itd.getFieldDefinitions()) {
                    if (fd.hasDirective("auth")) {
                        return true;
                    }
                }
            }
        }
        return false;
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

        // 2. Configure Runtime Wiring (auto-registering generic DataFetchers and directive wirings)
        builder.configureRuntimeWiring(wiringBuilder -> {
            TypeDefinitionRegistry typeRegistry = registryRef.get();
            boolean hasAuth = hasAuthDirectives(modelRegistry) || hasAuthDirectives(typeRegistry);
            if (hasAuth && (fieldAuthorizer == null || fieldAuthorizer == FieldAuthorizer.PERMIT_ALL)) {
                throw new IllegalStateException(
                        "GraphORM schema defines @auth directives, but no non-default FieldAuthorizer bean is configured. " +
                        "Configure a custom FieldAuthorizer bean or include spring-boot-starter-security to prevent unauthorized access."
                );
            }

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

            if (properties.getSecurity().isFieldAuthorizationEnabled() && fieldAuthorizer != null) {
                wiringBuilder.directive("auth", new AuthDirectiveWiring(fieldAuthorizer));
                FieldAuthorizerContext.setDefaultAuthorizer(fieldAuthorizer);
            }
        });

        // 3. Register DataLoaders and Security Instrumentation
        GraphOrmDataLoaderRegistryFactory factory = metricsListener != null
                ? new GraphOrmDataLoaderRegistryFactory(modelRegistry, executor, dialect, metricsListener)
                : new GraphOrmDataLoaderRegistryFactory(modelRegistry, executor, dialect);

        if (properties.isVirtualThreads() || properties.getGraphql().isVirtualThreads()) {
            factory.asyncExecutor(Executors.newVirtualThreadPerTaskExecutor());
            log.info("Enabled Project Loom virtual-thread executor for DataLoader batch hydration");
        }

        List<Instrumentation> instrumentations = new ArrayList<>();
        instrumentations.add(new GraphOrmDataLoaderInstrumentation(factory));

        if (properties.getSecurity().isDepthLimitEnabled()) {
            instrumentations.add(new MaxQueryDepthInstrumentation(properties.getSecurity().getMaxQueryDepth()));
        }
        if (properties.getSecurity().isComplexityLimitEnabled()) {
            instrumentations.add(new MaxQueryComplexityInstrumentation(properties.getSecurity().getMaxQueryComplexity()));
        }

        builder.instrumentation(instrumentations);
    }

    private void mergeGraphOrmTypeDefinitions(TypeDefinitionRegistry target) {
        String generatedSdl = GraphQlSdlGenerator.generate(modelRegistry);
        if (generatedSdl.isBlank()) {
            return;
        }

        try {
            TypeDefinitionRegistry generatedRegistry = new SchemaParser().parse(new StringReader(generatedSdl));

            for (DirectiveDefinition dir : generatedRegistry.getDirectiveDefinitions().values()) {
                if (target.getDirectiveDefinition(dir.getName()).isEmpty()) {
                    target.add(dir);
                }
            }

            for (TypeDefinition<?> def : generatedRegistry.types().values()) {
                if (!target.hasType(new TypeName(def.getName()))) {
                    target.add(def);
                } else if (def instanceof ObjectTypeDefinition genObj && target.getType(def.getName()).orElse(null) instanceof ObjectTypeDefinition targetObj) {
                    // Merge fields and directives for ObjectTypes
                    mergeFields(target, targetObj, genObj);
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
            int existingIdx = -1;
            for (int i = 0; i < targetFields.size(); i++) {
                if (targetFields.get(i).getName().equals(genField.getName())) {
                    existingIdx = i;
                    break;
                }
            }

            if (existingIdx == -1) {
                targetFields.add(genField);
                modified = true;
            } else if (!genField.getDirectives().isEmpty()) {
                FieldDefinition existing = targetFields.get(existingIdx);
                if (existing.getDirectives().isEmpty()) {
                    targetFields.set(existingIdx, genField);
                    modified = true;
                }
            }
        }

        if (modified) {
            ObjectTypeDefinition newObj = targetObj.transform(b -> b.fieldDefinitions(targetFields));
            target.remove(targetObj);
            target.add(newObj);
        }
    }
}
