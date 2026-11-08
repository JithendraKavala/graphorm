package com.graphorm.graphql.engine;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.fetcher.ModelCreateDataFetcher;
import com.graphorm.graphql.fetcher.ModelDeleteDataFetcher;
import com.graphorm.graphql.fetcher.ModelListDataFetcher;
import com.graphorm.graphql.fetcher.ModelRelationDataFetcher;
import com.graphorm.graphql.fetcher.ModelSingleDataFetcher;
import com.graphorm.graphql.fetcher.ModelUpdateDataFetcher;
import com.graphorm.graphql.fetcher.ViewListDataFetcher;
import com.graphorm.graphql.fetcher.ViewSingleDataFetcher;
import com.graphorm.graphql.schema.GraphQlOperationBinding;
import com.graphorm.graphql.schema.GraphQlOperationKind;
import com.graphorm.graphql.schema.GraphQlTypeInspector;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.relation.BatchRelationLoader;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Automatically discovers, binds, and registers generic {@link DataFetcher} instances
 * for models, view models, and nested relationships in GraphQL Java runtime wiring or code registry.
 * <p>
 * Enforces:
 * <ul>
 *     <li>Auto-registration of {@code Query.<model>s} and {@code Query.<model>}.</li>
 *     <li>Auto-registration of {@code Mutation.create<Model>}, {@code update<Model>}, {@code delete<Model>} (regular models only).</li>
 *     <li>Auto-registration of {@code Query.<view>s} and {@code Query.<view>}.</li>
 *     <li>Auto-registration of relationship {@code <Model>.<relation>} DataFetchers backed by {@link BatchRelationLoader} and DataLoader (TASK-11.4).</li>
 *     <li>Strict read-only invariant: NEVER registers any mutations for view models.</li>
 *     <li>Custom resolver coexistence: skips registration if a custom fetcher is already registered.</li>
 * </ul>
 */
public class GraphOrmGraphQlRegistrar {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmGraphQlRegistrar.class);

    private final ModelRegistry modelRegistry;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final ArgumentBinder argumentBinder;
    private final BatchRelationLoader batchRelationLoader;
    private boolean failFastOnViewMutation = true;
    private final Set<FieldCoordinates> customResolverCoordinates = new HashSet<>();
    private Predicate<FieldCoordinates> customResolverPredicate = coords -> false;

    public GraphOrmGraphQlRegistrar(
            ModelRegistry modelRegistry,
            JdbcExecutor executor,
            SqlDialect dialect,
            ArgumentBinder argumentBinder
    ) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "ModelRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = dialect != null ? dialect : executor.dialect();
        this.argumentBinder = argumentBinder != null ? argumentBinder : new ArgumentBinder();
        this.batchRelationLoader = new BatchRelationLoader(this.executor, this.modelRegistry);
    }

    public GraphOrmGraphQlRegistrar(ModelRegistry modelRegistry, JdbcExecutor executor) {
        this(modelRegistry, executor, executor.dialect(), new ArgumentBinder());
    }

    public GraphOrmGraphQlRegistrar(ModelRegistry modelRegistry, DataSource dataSource) {
        this(modelRegistry, new JdbcExecutor(dataSource, new PostgreSqlDialect()), new PostgreSqlDialect(), new ArgumentBinder());
    }

    public GraphOrmGraphQlRegistrar failFastOnViewMutation(boolean failFast) {
        this.failFastOnViewMutation = failFast;
        return this;
    }

    public GraphOrmGraphQlRegistrar withCustomResolver(String parentType, String fieldName) {
        this.customResolverCoordinates.add(FieldCoordinates.coordinates(parentType, fieldName));
        return this;
    }

    public GraphOrmGraphQlRegistrar withCustomResolverPredicate(Predicate<FieldCoordinates> predicate) {
        this.customResolverPredicate = Objects.requireNonNull(predicate, "Predicate must not be null");
        return this;
    }

    /**
     * Registers all generic DataFetchers into the specified {@link RuntimeWiring.Builder}.
     *
     * @param wiringBuilder the runtime wiring builder
     * @param typeRegistry  the parsed SDL type registry
     * @return the list of registered operation bindings
     */
    public List<GraphQlOperationBinding> register(RuntimeWiring.Builder wiringBuilder, TypeDefinitionRegistry typeRegistry) {
        Objects.requireNonNull(wiringBuilder, "RuntimeWiring.Builder must not be null");
        Objects.requireNonNull(typeRegistry, "TypeDefinitionRegistry must not be null");

        GraphQlTypeInspector inspector = new GraphQlTypeInspector(typeRegistry, modelRegistry);
        List<GraphQlOperationBinding> bindings = inspector.inspect();
        List<GraphQlOperationBinding> rejected = inspector.getRejectedViewMutations();

        handleRejectedViewMutations(rejected);

        TypeRuntimeWiring.Builder queryWiring = TypeRuntimeWiring.newTypeWiring("Query");
        TypeRuntimeWiring.Builder mutationWiring = TypeRuntimeWiring.newTypeWiring("Mutation");

        int queryCount = 0;
        int mutationCount = 0;

        for (GraphQlOperationBinding binding : bindings) {
            FieldCoordinates coords = FieldCoordinates.coordinates(binding.parentType(), binding.fieldName());
            if (isCustomResolverPresent(coords)) {
                log.info("Custom resolver detected for {}; skipping GraphORM auto-wiring", coords);
                continue;
            }

            DataFetcher<?> fetcher = createDataFetcher(binding);
            if (binding.isQuery()) {
                queryWiring.dataFetcher(binding.fieldName(), fetcher);
                queryCount++;
            } else if (binding.isMutation()) {
                mutationWiring.dataFetcher(binding.fieldName(), fetcher);
                mutationCount++;
            }
        }

        if (queryCount > 0) {
            wiringBuilder.type(queryWiring);
        }
        if (mutationCount > 0) {
            wiringBuilder.type(mutationWiring);
        }

        // Auto-register relationship DataFetchers on Model and View types
        int relCount = registerRelationshipFetchers(wiringBuilder);

        log.info("GraphORM registered {} queries, {} mutations, and {} relation DataFetchers into GraphQL RuntimeWiring",
                queryCount, mutationCount, relCount);
        return bindings;
    }

    /**
     * Registers all generic DataFetchers into the specified {@link GraphQLCodeRegistry.Builder}.
     *
     * @param codeRegistryBuilder the code registry builder
     * @param typeRegistry        the parsed SDL type registry
     * @return the list of registered operation bindings
     */
    public List<GraphQlOperationBinding> register(GraphQLCodeRegistry.Builder codeRegistryBuilder, TypeDefinitionRegistry typeRegistry) {
        Objects.requireNonNull(codeRegistryBuilder, "GraphQLCodeRegistry.Builder must not be null");
        Objects.requireNonNull(typeRegistry, "TypeDefinitionRegistry must not be null");

        GraphQlTypeInspector inspector = new GraphQlTypeInspector(typeRegistry, modelRegistry);
        List<GraphQlOperationBinding> bindings = inspector.inspect();
        List<GraphQlOperationBinding> rejected = inspector.getRejectedViewMutations();

        handleRejectedViewMutations(rejected);

        for (GraphQlOperationBinding binding : bindings) {
            FieldCoordinates coords = FieldCoordinates.coordinates(binding.parentType(), binding.fieldName());
            if (isCustomResolverPresent(coords) || codeRegistryBuilder.hasDataFetcher(coords)) {
                log.info("Custom resolver detected for {}; skipping GraphORM auto-wiring", coords);
                continue;
            }

            DataFetcher<?> fetcher = createDataFetcher(binding);
            codeRegistryBuilder.dataFetcher(coords, fetcher);
        }

        // Auto-register relationship DataFetchers on Model and View types
        registerRelationshipFetchers(codeRegistryBuilder);

        log.info("GraphORM registered {} DataFetchers into GraphQLCodeRegistry", bindings.size());
        return bindings;
    }

    private int registerRelationshipFetchers(RuntimeWiring.Builder wiringBuilder) {
        int count = 0;
        for (ModelMetadata model : modelRegistry.getAllModels()) {
            if (model.relations().isEmpty()) {
                continue;
            }
            TypeRuntimeWiring.Builder typeWiring = TypeRuntimeWiring.newTypeWiring(model.name());
            int modelRelCount = 0;
            for (RelationMetadata relation : model.relations()) {
                FieldCoordinates coords = FieldCoordinates.coordinates(model.name(), relation.relationName());
                if (isCustomResolverPresent(coords)) {
                    log.info("Custom resolver detected for {}; skipping GraphORM auto-wiring", coords);
                    continue;
                }
                ModelRelationDataFetcher fetcher = new ModelRelationDataFetcher(model, relation, modelRegistry, batchRelationLoader);
                typeWiring.dataFetcher(relation.relationName(), fetcher);
                modelRelCount++;
                count++;
            }
            if (modelRelCount > 0) {
                wiringBuilder.type(typeWiring);
            }
        }
        return count;
    }

    private int registerRelationshipFetchers(GraphQLCodeRegistry.Builder codeRegistryBuilder) {
        int count = 0;
        for (ModelMetadata model : modelRegistry.getAllModels()) {
            for (RelationMetadata relation : model.relations()) {
                FieldCoordinates coords = FieldCoordinates.coordinates(model.name(), relation.relationName());
                if (isCustomResolverPresent(coords) || codeRegistryBuilder.hasDataFetcher(coords)) {
                    log.info("Custom resolver detected for {}; skipping GraphORM auto-wiring", coords);
                    continue;
                }
                ModelRelationDataFetcher fetcher = new ModelRelationDataFetcher(model, relation, modelRegistry, batchRelationLoader);
                codeRegistryBuilder.dataFetcher(coords, fetcher);
                count++;
            }
        }
        return count;
    }

    /**
     * Convenience method to register via raw SDL string.
     */
    public List<GraphQlOperationBinding> register(RuntimeWiring.Builder wiringBuilder, String sdl) {
        GraphQlTypeInspector inspector = GraphQlTypeInspector.parse(sdl, modelRegistry);
        return register(wiringBuilder, inspector.typeRegistry());
    }

    /**
     * Creates the appropriate generic {@link DataFetcher} for the given operation binding.
     */
    public DataFetcher<?> createDataFetcher(GraphQlOperationBinding binding) {
        Objects.requireNonNull(binding, "GraphQlOperationBinding must not be null");

        return switch (binding.kind()) {
            case MODEL_LIST_QUERY -> new ModelListDataFetcher(
                    (ModelMetadata) binding.entity(),
                    modelRegistry,
                    executor,
                    argumentBinder,
                    dialect
            );
            case MODEL_SINGLE_QUERY -> new ModelSingleDataFetcher(
                    (ModelMetadata) binding.entity(),
                    modelRegistry,
                    executor,
                    dialect,
                    binding.idArgumentName()
            );
            case MODEL_CREATE_MUTATION -> new ModelCreateDataFetcher(
                    (ModelMetadata) binding.entity(),
                    executor,
                    dialect,
                    binding.inputArgumentName()
            );
            case MODEL_UPDATE_MUTATION -> new ModelUpdateDataFetcher(
                    (ModelMetadata) binding.entity(),
                    modelRegistry,
                    executor,
                    dialect,
                    binding.idArgumentName(),
                    binding.inputArgumentName()
            );
            case MODEL_DELETE_MUTATION -> new ModelDeleteDataFetcher(
                    (ModelMetadata) binding.entity(),
                    executor,
                    dialect,
                    binding.idArgumentName()
            );
            case VIEW_LIST_QUERY -> binding.entity() instanceof ViewModelMetadata vm
                    ? new ViewListDataFetcher(vm, modelRegistry, executor, argumentBinder, dialect)
                    : new ModelListDataFetcher((ModelMetadata) binding.entity(), modelRegistry, executor, argumentBinder, dialect);
            case VIEW_SINGLE_QUERY -> binding.entity() instanceof ViewModelMetadata vm
                    ? new ViewSingleDataFetcher(vm, modelRegistry, executor, dialect, binding.idArgumentName())
                    : new ModelSingleDataFetcher((ModelMetadata) binding.entity(), modelRegistry, executor, dialect, binding.idArgumentName());
            case VIEW_MUTATION_REJECTED -> throw new ReadOnlyViewException(
                    binding.entity().name(),
                    "Attempted to register write mutation '" + binding.fieldName()
                            + "' for read-only view '" + binding.entity().name()
                            + "'. Views are strictly read-only and cannot have mutations."
            );
        };
    }

    private void handleRejectedViewMutations(List<GraphQlOperationBinding> rejected) {
        if (rejected.isEmpty()) {
            return;
        }

        for (GraphQlOperationBinding b : rejected) {
            String msg = "Read-only view model invariant violation: Mutation '" + b.fieldName()
                    + "' declared for view '" + b.entity().name()
                    + "'. View models are strictly read-only.";
            if (failFastOnViewMutation) {
                throw new ReadOnlyViewException(b.entity().name(), msg);
            } else {
                log.warn("STRICT READ-ONLY WARNING: {}", msg);
            }
        }
    }

    private boolean isCustomResolverPresent(FieldCoordinates coords) {
        return customResolverCoordinates.contains(coords) || customResolverPredicate.test(coords);
    }

    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }

    public JdbcExecutor executor() {
        return executor;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public ArgumentBinder argumentBinder() {
        return argumentBinder;
    }

    public BatchRelationLoader batchRelationLoader() {
        return batchRelationLoader;
    }
}
