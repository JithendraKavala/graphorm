package com.graphorm.graphql.engine;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.loader.GraphOrmDataLoaderRegistryFactory;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
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
 * and DataLoader relationship batching (TASK-11.1 - TASK-11.4).
 */
public final class GraphOrmGraphQlEngine {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmGraphQlEngine.class);

    private final GraphQL graphQL;
    private final GraphQLSchema graphQLSchema;
    private final GraphOrmGraphQlRegistrar registrar;
    private final GraphOrmDataLoaderRegistryFactory dataLoaderRegistryFactory;

    private GraphOrmGraphQlEngine(
            GraphQL graphQL,
            GraphQLSchema graphQLSchema,
            GraphOrmGraphQlRegistrar registrar,
            GraphOrmDataLoaderRegistryFactory dataLoaderRegistryFactory
    ) {
        this.graphQL = graphQL;
        this.graphQLSchema = graphQLSchema;
        this.registrar = registrar;
        this.dataLoaderRegistryFactory = dataLoaderRegistryFactory;
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
        ExecutionInput input = executionInput.transform(b -> b.dataLoaderRegistry(createDataLoaderRegistry()));
        return graphQL.execute(input);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ModelRegistry modelRegistry;
        private JdbcExecutor executor;
        private DataSource dataSource;
        private SqlDialect dialect = new PostgreSqlDialect();
        private ArgumentBinder argumentBinder = new ArgumentBinder();
        private TypeDefinitionRegistry typeRegistry;
        private String sdlString;
        private boolean failFastOnViewMutation = true;
        private Consumer<RuntimeWiring.Builder> customWiringConsumer;
        private Consumer<GraphQL.Builder> customGraphQLBuilderConsumer;

        private Builder() {}

        public Builder modelRegistry(ModelRegistry modelRegistry) {
            this.modelRegistry = modelRegistry;
            return this;
        }

        public Builder executor(JdbcExecutor executor) {
            this.executor = executor;
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

            if (executor == null) {
                if (dataSource == null) {
                    throw new IllegalStateException("Either JdbcExecutor or DataSource must be provided");
                }
                executor = new JdbcExecutor(dataSource, dialect);
            }

            if (typeRegistry == null) {
                if (sdlString == null || sdlString.isBlank()) {
                    throw new IllegalStateException("GraphQL schema definition (SDL) must be provided");
                }
                SchemaParser parser = new SchemaParser();
                typeRegistry = parser.parse(new StringReader(sdlString));
            }

            GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(modelRegistry, executor, dialect, argumentBinder)
                    .failFastOnViewMutation(failFastOnViewMutation);

            RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();

            // Auto-register GraphORM DataFetchers
            registrar.register(wiringBuilder, typeRegistry);

            // Allow custom wiring hooks (e.g. for custom resolvers, directives, or scalar types)
            if (customWiringConsumer != null) {
                customWiringConsumer.accept(wiringBuilder);
            }

            RuntimeWiring runtimeWiring = wiringBuilder.build();
            SchemaGenerator schemaGenerator = new SchemaGenerator();
            GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

            GraphOrmDataLoaderRegistryFactory dataLoaderRegistryFactory = new GraphOrmDataLoaderRegistryFactory(
                    modelRegistry,
                    executor,
                    dialect
            );

            List<Instrumentation> instrumentations = new ArrayList<>();
            instrumentations.add(new GraphOrmDataLoaderInstrumentation(dataLoaderRegistryFactory));
            instrumentations.add(new DataLoaderDispatcherInstrumentation());

            GraphQL.Builder graphQLBuilder = GraphQL.newGraphQL(graphQLSchema)
                    .instrumentation(new ChainedInstrumentation(instrumentations));

            if (customGraphQLBuilderConsumer != null) {
                customGraphQLBuilderConsumer.accept(graphQLBuilder);
            }

            GraphQL graphQL = graphQLBuilder.build();

            log.info("GraphOrmGraphQlEngine successfully initialized with executable schema and DataLoader instrumentation");
            return new GraphOrmGraphQlEngine(graphQL, graphQLSchema, registrar, dataLoaderRegistryFactory);
        }
    }
}
