package com.graphorm.graphql.fetcher;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.GraphQLContext;
import graphql.Scalars;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.SelectedField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModelConnection and Mutation @auth and Optimistic Locking Tests")
class ModelConnectionAndMutationAuthTest {

    private final DataSource dummyDataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{Connection.class},
                            (cProxy, cMethod, cArgs) -> null
                    );
                }
                return null;
            }
    );

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    private final ModelMetadata userModel = ModelMetadata.builder("UserProfile")
            .tableName("user_profiles")
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("username", String.class).build())
            .addField(FieldMetadata.builder("salary", Double.class).authRoles(List.of("HR_ADMIN")).build())
            .addField(FieldMetadata.builder("version", Integer.class).version(true).build())
            .addPrimaryKeyField("id")
            .build();

    private final ModelRegistry registry = ModelRegistry.builder()
            .addModel(userModel)
            .build();

    private DataFetchingEnvironment createMockEnv(
            Map<String, Object> arguments,
            FieldAuthorizer authorizer,
            List<String> edgeNodeFields
    ) {
        return (DataFetchingEnvironment) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingEnvironment.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getArguments".equals(name)) {
                        return arguments != null ? arguments : Collections.emptyMap();
                    }
                    if ("getArgument".equals(name)) {
                        String argName = (String) args[0];
                        return arguments != null ? arguments.get(argName) : null;
                    }
                    if ("containsArgument".equals(name)) {
                        String argName = (String) args[0];
                        return arguments != null && arguments.containsKey(argName);
                    }
                    if ("getGraphQlContext".equals(name)) {
                        GraphQLContext ctx = GraphQLContext.newContext().build();
                        if (authorizer != null) {
                            return ctx.put(FieldAuthorizer.class, authorizer);
                        }
                        return ctx;
                    }
                    if ("getSelectionSet".equals(name)) {
                        return mockSelectionSet(edgeNodeFields);
                    }
                    if ("getField".equals(name)) {
                        return Field.newField("test").selectionSet(SelectionSet.newSelectionSet().build()).build();
                    }
                    if ("getFieldDefinition".equals(name)) {
                        return GraphQLFieldDefinition.newFieldDefinition()
                                .name("test")
                                .type(Scalars.GraphQLBoolean)
                                .build();
                    }
                    return null;
                }
        );
    }

    private DataFetchingFieldSelectionSet mockSelectionSet(List<String> edgeNodeFields) {
        return (DataFetchingFieldSelectionSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingFieldSelectionSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getImmediateFields" -> List.of(mockSelectedField("edges"), mockSelectedField("pageInfo"));
                    case "getFields" -> {
                        if (args != null && args.length > 0 && "edges/node/*".equals(args[0])) {
                            yield edgeNodeFields.stream().map(this::mockSelectedField).toList();
                        }
                        yield Collections.emptyList();
                    }
                    case "contains" -> false;
                    default -> Collections.emptyList();
                }
        );
    }

    private SelectedField mockSelectedField(String name) {
        return (SelectedField) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{SelectedField.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("ModelConnectionDataFetcher: Denies access when querying edges/node/* field protected by @auth")
    void shouldDenyUnauthorizedFieldInEdgesNode() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelConnectionDataFetcher fetcher = new ModelConnectionDataFetcher(userModel, registry, executor, new ArgumentBinder(), dialect);

        FieldAuthorizer regularUser = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Collections.emptyMap(), regularUser, List.of("id", "username", "salary"));

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("salary");
    }

    @Test
    @DisplayName("ModelConnectionDataFetcher: Allows access when querying authorized fields in edges/node/*")
    void shouldAllowAuthorizedFieldsInEdgesNode() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public List<Map<String, Object>> queryForList(SelectQuery query) {
                return List.of(Map.of("id", 1L, "username", "alice", "salary", 100000.0));
            }
        };
        ModelConnectionDataFetcher fetcher = new ModelConnectionDataFetcher(userModel, registry, executor, new ArgumentBinder(), dialect);

        FieldAuthorizer hrAdmin = roles -> roles.contains("HR_ADMIN");
        DataFetchingEnvironment env = createMockEnv(Collections.emptyMap(), hrAdmin, List.of("id", "username", "salary"));

        var connection = fetcher.get(env);
        assertThat(connection).isNotNull();
        assertThat(connection.edges()).hasSize(1);
    }

    @Test
    @DisplayName("ModelCreateDataFetcher: Denies access when mutation input contains field protected by @auth")
    void shouldDenyUnauthorizedFieldInCreateInput() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelCreateDataFetcher fetcher = new ModelCreateDataFetcher(userModel, executor);

        FieldAuthorizer regularUser = roles -> false;
        Map<String, Object> input = Map.of("username", "bob", "salary", 95000.0);
        DataFetchingEnvironment env = createMockEnv(Map.of("input", input), regularUser, List.of());

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("salary");
    }

    @Test
    @DisplayName("ModelUpdateDataFetcher: Denies access when mutation input contains field protected by @auth")
    void shouldDenyUnauthorizedFieldInUpdateInput() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelUpdateDataFetcher fetcher = new ModelUpdateDataFetcher(userModel, registry, executor);

        FieldAuthorizer regularUser = roles -> false;
        Map<String, Object> input = Map.of("salary", 120000.0);
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L, "input", input), regularUser, List.of());

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("salary");
    }

    @Test
    @DisplayName("ModelUpdateDataFetcher: Throws OptimisticLockException when rows updated is 0 on versioned model")
    void shouldThrowOptimisticLockExceptionOnVersionConflict() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public Optional<Map<String, Object>> queryForObject(BoundSql boundSql) {
                return Optional.empty(); // 0 rows updated
            }
        };
        ModelUpdateDataFetcher fetcher = new ModelUpdateDataFetcher(userModel, registry, executor);

        FieldAuthorizer admin = roles -> true;
        Map<String, Object> input = Map.of("username", "alice_new", "version", 1);
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L, "input", input), admin, List.of());

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("Optimistic lock failure");
    }
}
