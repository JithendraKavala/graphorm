package com.graphorm.graphql.fetcher;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.InjectedValueResolver;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Fetcher Security, Authorization & Injected Column Tests")
class ModelAndViewFetcherSecurityTest {

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

    // Models
    private final ModelMetadata protectedModel = ModelMetadata.builder("AuditLog")
            .tableName("audit_logs")
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("action", String.class).build())
            .addField(FieldMetadata.builder("tenantId", String.class).dbColumnName("tenant_id").injected("tenantId").build())
            .addPrimaryKeyField("id")
            .authRoles(List.of("ADMIN", "AUDITOR"))
            .build();

    private final ModelMetadata readOnlyModel = ModelMetadata.builder("SystemConfig")
            .tableName("system_configs")
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("configKey", String.class).build())
            .addPrimaryKeyField("id")
            .readOnly(true)
            .build();

    private final ViewModelMetadata protectedView = ViewModelMetadata.builder("AuditSummary")
            .viewName("vw_audit_summary")
            .databaseBacked(true)
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("action", String.class).build())
            .authRoles(List.of("ADMIN"))
            .build();

    private final ModelRegistry registry = ModelRegistry.builder()
            .addModel(protectedModel)
            .addModel(readOnlyModel)
            .addViewModel(protectedView)
            .build();

    private DataFetchingEnvironment createMockEnv(
            Map<String, Object> arguments,
            FieldAuthorizer authorizer,
            GraphQLContext context
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
                        GraphQLContext ctx = context != null ? context : GraphQLContext.newContext().build();
                        if (authorizer != null) {
                            return ctx.put(FieldAuthorizer.class, authorizer);
                        }
                        return ctx;
                    }
                    if ("getSelectionSet".equals(name)) {
                        return mockSelectionSet();
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

    private DataFetchingFieldSelectionSet mockSelectionSet() {
        return (DataFetchingFieldSelectionSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingFieldSelectionSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getImmediateFields" -> List.of(mockSelectedField("id"), mockSelectedField("action"));
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

    // 1. ModelSingleDataFetcher Tests
    @Test
    @DisplayName("ModelSingleDataFetcher: Denies access when caller lacks auth roles")
    void modelSingle_deniedWhenLacksRole() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelSingleDataFetcher fetcher = new ModelSingleDataFetcher(protectedModel, registry, executor);

        FieldAuthorizer userAuth = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L), userAuth, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("AuditLog");
    }

    @Test
    @DisplayName("ModelSingleDataFetcher: Allows access when caller has matching auth role")
    void modelSingle_allowedWhenRoleMatches() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public Optional<Map<String, Object>> queryForObject(SelectQuery query) {
                return Optional.of(Map.of("id", 1L, "action", "LOGIN"));
            }
        };
        ModelSingleDataFetcher fetcher = new ModelSingleDataFetcher(protectedModel, registry, executor);

        FieldAuthorizer adminAuth = roles -> roles.contains("ADMIN");
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L), adminAuth, null);

        Map<String, Object> result = fetcher.get(env);
        assertThat(result).isNotNull();
        assertThat(result.get("action")).isEqualTo("LOGIN");
    }

    // 2. ModelListDataFetcher Tests
    @Test
    @DisplayName("ModelListDataFetcher: Denies access when caller lacks auth roles")
    void modelList_deniedWhenLacksRole() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelListDataFetcher fetcher = new ModelListDataFetcher(protectedModel, registry, executor, new ArgumentBinder(), dialect);

        FieldAuthorizer userAuth = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Collections.emptyMap(), userAuth, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ModelListDataFetcher: Allows access when caller has matching role")
    void modelList_allowedWhenRoleMatches() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public List<Map<String, Object>> queryForList(SelectQuery query) {
                return List.of(Map.of("id", 1L, "action", "VIEW"));
            }
        };
        ModelListDataFetcher fetcher = new ModelListDataFetcher(protectedModel, registry, executor, new ArgumentBinder(), dialect);

        FieldAuthorizer auditorAuth = roles -> roles.contains("AUDITOR");
        DataFetchingEnvironment env = createMockEnv(Collections.emptyMap(), auditorAuth, null);

        List<Map<String, Object>> result = fetcher.get(env);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().get("action")).isEqualTo("VIEW");
    }

    // 3. ViewSingleDataFetcher & ViewListDataFetcher Tests
    @Test
    @DisplayName("ViewSingleDataFetcher: Denies access when caller lacks view auth roles")
    void viewSingle_deniedWhenLacksRole() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ViewSingleDataFetcher fetcher = new ViewSingleDataFetcher(protectedView, registry, executor);

        FieldAuthorizer userAuth = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L), userAuth, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("AuditSummary");
    }

    @Test
    @DisplayName("ViewListDataFetcher: Denies access when caller lacks view auth roles")
    void viewList_deniedWhenLacksRole() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ViewListDataFetcher fetcher = new ViewListDataFetcher(protectedView, registry, executor, new ArgumentBinder(), dialect);

        FieldAuthorizer userAuth = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Collections.emptyMap(), userAuth, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class);
    }

    // 4. ModelCreateDataFetcher Tests
    @Test
    @DisplayName("ModelCreateDataFetcher: Throws ReadOnlyViewException on read-only model")
    void modelCreate_throwsOnReadOnlyModel() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelCreateDataFetcher fetcher = new ModelCreateDataFetcher(readOnlyModel, executor);

        DataFetchingEnvironment env = createMockEnv(Map.of("input", Map.of("configKey", "test")), null, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("SystemConfig");
    }

    @Test
    @DisplayName("ModelCreateDataFetcher: Denies access when caller lacks auth roles")
    void modelCreate_deniesWhenLacksAuth() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelCreateDataFetcher fetcher = new ModelCreateDataFetcher(protectedModel, executor);

        FieldAuthorizer userAuth = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Map.of("input", Map.of("action", "WRITE")), userAuth, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ModelCreateDataFetcher: Hydrates @injected column from context and ignores client input")
    void modelCreate_hydratesInjectedColumn() {
        AtomicReference<InsertQuery> captured = new AtomicReference<>();
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public Optional<Map<String, Object>> executeInsertAndReturn(InsertQuery query) {
                captured.set(query);
                return Optional.of(query.values());
            }
        };
        ModelCreateDataFetcher fetcher = new ModelCreateDataFetcher(protectedModel, executor);

        FieldAuthorizer adminAuth = roles -> true;
        GraphQLContext ctx = GraphQLContext.newContext()
                .of("tenantId", "tenant-alpha")
                .build();

        // Client attempts to tamper with tenantId
        Map<String, Object> input = Map.of(
                "action", "CREATE_RECORD",
                "tenantId", "tampered-tenant"
        );
        DataFetchingEnvironment env = createMockEnv(Map.of("input", input), adminAuth, ctx);

        Map<String, Object> result = fetcher.get(env);

        assertThat(captured.get()).isNotNull();
        // The injected tenantId must come from context ("tenant-alpha"), NOT from client ("tampered-tenant")
        assertThat(captured.get().values().get("tenant_id")).isEqualTo("tenant-alpha");
        assertThat(captured.get().values().get("action")).isEqualTo("CREATE_RECORD");
        assertThat(result.get("tenantId")).isEqualTo("tenant-alpha");
    }

    // 5. ModelUpdateDataFetcher Tests
    @Test
    @DisplayName("ModelUpdateDataFetcher: Throws ReadOnlyViewException on read-only model")
    void modelUpdate_throwsOnReadOnlyModel() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelUpdateDataFetcher fetcher = new ModelUpdateDataFetcher(readOnlyModel, registry, executor);

        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L, "input", Map.of("configKey", "update")), null, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("SystemConfig");
    }

    @Test
    @DisplayName("ModelUpdateDataFetcher: Denies access when caller lacks auth roles")
    void modelUpdate_deniesWhenLacksAuth() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelUpdateDataFetcher fetcher = new ModelUpdateDataFetcher(protectedModel, registry, executor);

        FieldAuthorizer userAuth = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L, "input", Map.of("action", "UPDATE")), userAuth, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ModelUpdateDataFetcher: Hydrates @injected column from context on update")
    void modelUpdate_hydratesInjectedColumn() {
        AtomicReference<BoundSql> captured = new AtomicReference<>();
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public Optional<Map<String, Object>> queryForObject(BoundSql boundSql) {
                captured.set(boundSql);
                return Optional.of(Map.of("id", 1L, "action", "MODIFIED", "tenant_id", "tenant-updated"));
            }
        };
        ModelUpdateDataFetcher fetcher = new ModelUpdateDataFetcher(protectedModel, registry, executor);

        FieldAuthorizer adminAuth = roles -> true;
        GraphQLContext ctx = GraphQLContext.newContext()
                .of("tenantId", "tenant-updated")
                .build();

        Map<String, Object> input = Map.of("action", "MODIFIED", "tenantId", "malicious-tenant");
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L, "input", input), adminAuth, ctx);

        fetcher.get(env);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().sql()).contains("UPDATE ").contains("audit_logs");
        assertThat(captured.get().parameters()).contains("tenant-updated");
        assertThat(captured.get().parameters()).doesNotContain("malicious-tenant");
    }

    // 6. ModelDeleteDataFetcher Tests
    @Test
    @DisplayName("ModelDeleteDataFetcher: Throws ReadOnlyViewException on read-only model")
    void modelDelete_throwsOnReadOnlyModel() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelDeleteDataFetcher fetcher = new ModelDeleteDataFetcher(readOnlyModel, executor);

        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L), null, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("SystemConfig");
    }

    @Test
    @DisplayName("ModelDeleteDataFetcher: Denies access when caller lacks auth roles")
    void modelDelete_deniesWhenLacksAuth() {
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect);
        ModelDeleteDataFetcher fetcher = new ModelDeleteDataFetcher(protectedModel, executor);

        FieldAuthorizer userAuth = roles -> false;
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 1L), userAuth, null);

        assertThatThrownBy(() -> fetcher.get(env))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ModelDeleteDataFetcher: Successfully deletes when authorized")
    void modelDelete_allowedWhenAuthorized() {
        AtomicReference<DeleteQuery> captured = new AtomicReference<>();
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public int executeUpdate(DeleteQuery query) {
                captured.set(query);
                return 1;
            }
        };
        ModelDeleteDataFetcher fetcher = new ModelDeleteDataFetcher(protectedModel, executor);

        FieldAuthorizer adminAuth = roles -> true;
        DataFetchingEnvironment env = createMockEnv(Map.of("id", 42L), adminAuth, null);

        Object result = fetcher.get(env);

        assertThat(captured.get()).isNotNull();
        assertThat(result).isEqualTo(true);
    }
}
