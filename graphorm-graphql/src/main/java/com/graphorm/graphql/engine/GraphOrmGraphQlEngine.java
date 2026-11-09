package com.graphorm.graphql.engine;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.loader.DataLoaderMetricsListener;
import com.graphorm.graphql.loader.GraphOrmDataLoaderRegistryFactory;
import com.graphorm.graphql.security.AuthDirectiveWiring;
import com.graphorm.graphql.security.DefaultFieldComplexityCalculator;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.GraphOrmDataFetcherExceptionHandler;
import com.graphorm.graphql.security.MaxQueryComplexityInstrumentation;
import com.graphorm.graphql.security.MaxQueryDepthInstrumentation;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.core.execution.QueryExecutionEngine;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.analysis.FieldComplexityCalculator;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.TypeName;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * High-level engine and builder for booting an executable {@link GraphQL} instance
 * directly from GraphORM's {@link ModelRegistry} and GraphQL SDL definitions with zero boilerplate.
 * Features automatic SelectionSet query planning, hidden foreign key dependency injection,
 * DataLoader relationship batching, DoS attack hardening (depth and complexity limiting),
 * field-level authorization (@auth), and sanitized error handling (TASK-15.1 - TASK-15.4).
 */
public final class GraphOrmGraphQlEngine {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmGraphQlEngine.class);

    private final GraphQL graphQL;
    private final GraphQLSchema graphQLSchema;
    private final GraphOrmGraphQlRegistrar registrar;
    private final GraphOrmDataLoaderRegistryFactory dataLoaderRegistryFactory;
    private final FieldAuthorizer fieldAuthorizer;

    private GraphOrmGraphQlEngine(
            GraphQL graphQL,
            GraphQLSchema graphQLSchema,
            GraphOrmGraphQlRegistrar registrar,
            GraphOrmDataLoaderRegistryFactory dataLoaderRegistryFactory,
            FieldAuthorizer fieldAuthorizer
    ) {
        this.graphQL = graphQL;
        this.graphQLSchema = graphQLSchema;
        this.registrar = registrar;
        this.dataLoaderRegistryFactory = dataLoaderRegistryFactory;
        this.fieldAuthorizer = fieldAuthorizer;
    }

    public GraphQL graphQL() {
        return graphQL;
    }

    public GraphQLSchema graphQLSchema() {
        return graphQLSchema;
    }

    public GraphOrmGraphQlRegistrar registrar() {
        return registrar;
    }

    public GraphOrmDataLoaderRegistryFactory dataLoaderRegistryFactory() {
        return dataLoaderRegistryFactory;
    }

    public FieldAuthorizer fieldAuthorizer() {
        return fieldAuthorizer;
    }

    /**
     * Creates a fresh {@link DataLoaderRegistry} pre-wired for relationship batching.
     */
    public DataLoaderRegistry createDataLoaderRegistry() {
        return dataLoaderRegistryFactory.create();
    }

    /**
     * Executes a GraphQL query string, ensuring DataLoaderRegistry is initialized.
     *
     * @param query the GraphQL query string
     * @return execution result
     */
    public ExecutionResult execute(String query) {
        return execute(ExecutionInput.newExecutionInput().query(query).build());
    }

    /**
     * Executes a GraphQL {@link ExecutionInput}, ensuring a relationship-wired {@link DataLoaderRegistry}
     * is attached.
     *
     * @param executionInput the GraphQL execution input
     * @return execution result
     */
    public ExecutionResult execute(ExecutionInput executionInput) {
        Objects.requireNonNull(executionInput, "ExecutionInput must not be null");
        ExecutionInput input = executionInput.transform(b -> {
            b.dataLoaderRegistry(createDataLoaderRegistry());
            if (fieldAuthorizer != null) {
                b.graphQLContext(ctx -> ctx.of(FieldAuthorizer.class, fieldAuthorizer));
            }
        });
        return graphQL.execute(input);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ModelRegistry modelRegistry;
        private QueryExecutionEngine executionEngine;
        private JdbcExecutor executor;
        private DataSource dataSource;
        private SqlDialect dialect = new PostgreSqlDialect();
        private ArgumentBinder argumentBinder = new ArgumentBinder();
        private TypeDefinitionRegistry typeRegistry;
        private String sdlString;
        private boolean failFastOnViewMutation = true;
        private Integer maxQueryDepth;
        private Integer maxQueryComplexity;
        private FieldComplexityCalculator fieldComplexityCalculator = new DefaultFieldComplexityCalculator();
        private FieldAuthorizer fieldAuthorizer = FieldAuthorizer.PERMIT_ALL;
        private DataLoaderMetricsListener dataLoaderMetricsListener = DataLoaderMetricsListener.NO_OP;
        private Consumer<RuntimeWiring.Builder> customWiringConsumer;
        private Consumer<GraphQL.Builder> customGraphQLBuilderConsumer;

        private Builder() {}

        public Builder modelRegistry(ModelRegistry modelRegistry) {
            this.modelRegistry = modelRegistry;
            return this;
        }

        public Builder executionEngine(QueryExecutionEngine executionEngine) {
            this.executionEngine = executionEngine;
            if (executionEngine instanceof JdbcExecutor je) {
                this.executor = je;
            }
            return this;
        }

        public Builder executor(JdbcExecutor executor) {
            this.executor = executor;
            this.executionEngine = executor;
            return this;
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder dialect(SqlDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder argumentBinder(ArgumentBinder argumentBinder) {
            this.argumentBinder = argumentBinder;
            return this;
        }

        public Builder schema(String sdl) {
            this.sdlString = sdl;
            return this;
        }

        public Builder schema(Reader reader) {
            Objects.requireNonNull(reader, "Reader must not be null");
            SchemaParser parser = new SchemaParser();
            this.typeRegistry = parser.parse(reader);
            return this;
        }

        public Builder schema(InputStream inputStream) {
            Objects.requireNonNull(inputStream, "InputStream must not be null");
            return schema(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }

        public Builder typeDefinitionRegistry(TypeDefinitionRegistry typeRegistry) {
            this.typeRegistry = typeRegistry;
            return this;
        }

        public Builder failFastOnViewMutation(boolean failFast) {
            this.failFastOnViewMutation = failFast;
            return this;
        }

        public Builder maxQueryDepth(int maxDepth) {
            this.maxQueryDepth = maxDepth;
            return this;
        }

        public Builder maxQueryComplexity(int maxComplexity) {
            this.maxQueryComplexity = maxComplexity;
            return this;
        }

        public Builder fieldComplexityCalculator(FieldComplexityCalculator calculator) {
            this.fieldComplexityCalculator = calculator;
            return this;
        }

        public Builder fieldAuthorizer(FieldAuthorizer fieldAuthorizer) {
            this.fieldAuthorizer = fieldAuthorizer;
            return this;
        }

        public Builder dataLoaderMetricsListener(DataLoaderMetricsListener listener) {
            this.dataLoaderMetricsListener = listener;
            return this;
        }

        public Builder customWiring(Consumer<RuntimeWiring.Builder> customWiringConsumer) {
            this.customWiringConsumer = customWiringConsumer;
            return this;
        }

        public Builder customGraphQLBuilder(Consumer<GraphQL.Builder> customGraphQLBuilderConsumer) {
            this.customGraphQLBuilderConsumer = customGraphQLBuilderConsumer;
            return this;
        }

        public GraphOrmGraphQlEngine build() {
            Objects.requireNonNull(modelRegistry, "ModelRegistry must not be null");

            if (executionEngine == null) {
                if (executor != null) {
                    executionEngine = executor;
                } else if (dataSource != null) {
                    executor = new JdbcExecutor(dataSource, dialect);
                    executionEngine = executor;
                } else {
                    throw new IllegalStateException("Either QueryExecutionEngine, JdbcExecutor or DataSource must be provided");
                }
            }

            if (typeRegistry == null) {
                if (sdlString == null || sdlString.isBlank()) {
                    throw new IllegalStateException("GraphQL schema definition (SDL) must be provided");
                }
                SchemaParser parser = new SchemaParser();
                typeRegistry = parser.parse(new StringReader(sdlString));
            }

            // Ensure directive @auth is declared in TypeDefinitionRegistry if not present
            if (typeRegistry.getDirectiveDefinition("auth").isEmpty()) {
                DirectiveDefinition authDirective = DirectiveDefinition.newDirectiveDefinition()
                        .name("auth")
                        .directiveLocation(DirectiveLocation.newDirectiveLocation().name("FIELD_DEFINITION").build())
                        .inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                                .name("roles")
                                .type(new NonNullType(new ListType(new NonNullType(new TypeName("String")))))
                                .build())
                        .build();
                typeRegistry.add(authDirective);
            }

            GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(modelRegistry, executionEngine, dialect, argumentBinder)
                    .failFastOnViewMutation(failFastOnViewMutation);

            RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();

            // Auto-register GraphORM DataFetchers
            registrar.register(wiringBuilder, typeRegistry);

            // Wire @auth directive
            wiringBuilder.directive("auth", new AuthDirectiveWiring(fieldAuthorizer));

            // Allow custom wiring hooks (e.g. for custom resolvers, directives, or scalar types)
            if (customWiringConsumer != null) {
                customWiringConsumer.accept(wiringBuilder);
            }

            RuntimeWiring runtimeWiring = wiringBuilder.build();
            SchemaGenerator schemaGenerator = new SchemaGenerator();
            GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

            GraphOrmDataLoaderRegistryFactory dataLoaderRegistryFactory = new GraphOrmDataLoaderRegistryFactory(
                    modelRegistry,
                    executionEngine,
                    dialect,
                    dataLoaderMetricsListener
            );

            List<Instrumentation> instrumentations = new ArrayList<>();
            instrumentations.add(new GraphOrmDataLoaderInstrumentation(dataLoaderRegistryFactory));
            instrumentations.add(new DataLoaderDispatcherInstrumentation());

            // TASK-15.1: Depth limit instrumentation
            if (maxQueryDepth != null && maxQueryDepth > 0) {
                instrumentations.add(new MaxQueryDepthInstrumentation(maxQueryDepth));
                log.info("Registered MaxQueryDepthInstrumentation with limit: {}", maxQueryDepth);
            }

            // TASK-15.1: Complexity limit instrumentation
            if (maxQueryComplexity != null && maxQueryComplexity > 0) {
                instrumentations.add(new MaxQueryComplexityInstrumentation(maxQueryComplexity, fieldComplexityCalculator));
                log.info("Registered MaxQueryComplexityInstrumentation with limit: {}", maxQueryComplexity);
            }

            GraphQL.Builder graphQLBuilder = GraphQL.newGraphQL(graphQLSchema)
                    .instrumentation(new ChainedInstrumentation(instrumentations))
                    .defaultDataFetcherExceptionHandler(new GraphOrmDataFetcherExceptionHandler());

            if (customGraphQLBuilderConsumer != null) {
                customGraphQLBuilderConsumer.accept(graphQLBuilder);
            }

            GraphQL graphQL = graphQLBuilder.build();

            log.info("GraphOrmGraphQlEngine successfully initialized with executable schema, security limits, and instrumentation");
            return new GraphOrmGraphQlEngine(graphQL, graphQLSchema, registrar, dataLoaderRegistryFactory, fieldAuthorizer);
        }
    }
}
