package com.graphorm.graphql.fetcher;

import com.graphorm.core.cache.CaffeineGraphOrmCacheManager;
import com.graphorm.core.cache.GraphOrmCacheManager;
import com.graphorm.core.metadata.CacheMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphQL DataFetcher L2 Caching and Tag-Based Mutation Invalidation Tests")
class GraphQlCacheAndInvalidationTest {

    private final ModelMetadata studentModel = ModelMetadata.builder("Student")
            .tableName("students")
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("name", String.class).build())
            .addField(FieldMetadata.builder("email", String.class).build())
            .addPrimaryKeyField("id")
            .cacheMetadata(new CacheMetadata(true, Duration.ofSeconds(60), Set.of("Student", "students")))
            .build();

    private final ViewModelMetadata dashboardView = ViewModelMetadata.builder("StudentDashboard")
            .viewName("vw_student_dashboard")
            .databaseBacked(true)
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("name", String.class).build())
            .cacheMetadata(new CacheMetadata(true, Duration.ofSeconds(60), Set.of("Student", "Enrollment")))
            .build();

    private final ModelRegistry registry = ModelRegistry.builder()
            .addModel(studentModel)
            .addViewModel(dashboardView)
            .build();

    private final DataSource dummyDataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            (proxy, method, args) -> null
    );

    private final SqlDialect dialect = new PostgreSqlDialect();
    private CaffeineGraphOrmCacheManager cacheManager;
    private AtomicInteger queryExecutionCount;
    private JdbcExecutor executor;

    @BeforeEach
    void setUp() {
        cacheManager = new CaffeineGraphOrmCacheManager();
        queryExecutionCount = new AtomicInteger(0);

        executor = new JdbcExecutor(dummyDataSource, dialect) {
            @Override
            public List<Map<String, Object>> queryForList(SelectQuery query) {
                queryExecutionCount.incrementAndGet();
                return List.of(Map.of("id", 1L, "name", "Alice"));
            }

            @Override
            public Optional<Map<String, Object>> queryForObject(SelectQuery query) {
                queryExecutionCount.incrementAndGet();
                return Optional.of(Map.of("id", 1L, "name", "Alice"));
            }

            @Override
            public Optional<Map<String, Object>> executeInsertAndReturn(InsertQuery query) {
                return Optional.of(Map.of("id", 2L, "name", "Bob"));
            }

            @Override
            public GraphOrmCacheManager getCacheManager() {
                return cacheManager;
            }

            @Override
            public Optional<GraphOrmCacheManager> cacheManager() {
                return Optional.of(cacheManager);
            }
        };
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DataFetchingEnvironment createMockEnvironment(Map<String, Object> arguments) {
        DataFetchingFieldSelectionSet selectionSet = (DataFetchingFieldSelectionSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingFieldSelectionSet.class},
                (proxy, method, args) -> {
                    if ("getFields".equals(method.getName())) {
                        SelectedField field = (SelectedField) Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[]{SelectedField.class},
                                (fProxy, fMethod, fArgs) -> {
                                    if ("getName".equals(fMethod.getName())) return "name";
                                    if ("getArguments".equals(fMethod.getName())) return Collections.emptyMap();
                                    return null;
                                }
                        );
                        return List.of(field);
                    }
                    return Collections.emptyList();
                }
        );

        return (DataFetchingEnvironment) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingEnvironment.class},
                (proxy, method, args) -> {
                    if ("containsArgument".equals(method.getName())) {
                        return arguments != null && args.length > 0 && arguments.containsKey(args[0]);
                    }
                    if ("getArguments".equals(method.getName())) {
                        return arguments != null ? arguments : Collections.emptyMap();
                    }
                    if ("getArgument".equals(method.getName())) {
                        return arguments != null && args.length > 0 ? arguments.get(args[0]) : null;
                    }
                    if ("getSelectionSet".equals(method.getName())) {
                        return selectionSet;
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("ModelListDataFetcher caches result and serves subsequent requests from L2 cache")
    void testModelListFetcherCaching() throws Exception {
        ModelListDataFetcher fetcher = new ModelListDataFetcher(
                studentModel,
                registry,
                executor,
                new ArgumentBinder(),
                dialect
        );

        DataFetchingEnvironment env = createMockEnvironment(Collections.emptyMap());

        // First call: executes query on database
        Object result1 = fetcher.get(env);
        assertThat(result1).isInstanceOf(List.class);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Second call: served from cache!
        Object result2 = fetcher.get(env);
        assertThat(result2).isInstanceOf(List.class);
        assertThat(queryExecutionCount.get()).isEqualTo(1); // count not incremented!
    }

    @Test
    @DisplayName("ModelCreateDataFetcher automatically invalidates cache tags on entity mutation")
    void testModelCreateInvalidatesTags() throws Exception {
        ModelListDataFetcher listFetcher = new ModelListDataFetcher(
                studentModel,
                registry,
                executor,
                new ArgumentBinder(),
                dialect
        );

        DataFetchingEnvironment env = createMockEnvironment(Collections.emptyMap());

        // First read: queries DB, caches result
        listFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Second read: cached
        listFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Mutation occurs: Create Student
        ModelCreateDataFetcher createFetcher = new ModelCreateDataFetcher(
                studentModel,
                executor,
                dialect,
                "input"
        );

        DataFetchingEnvironment createEnv = createMockEnvironment(Map.of(
                "input", Map.of("name", "Bob")
        ));
        createFetcher.get(createEnv);

        // Third read: Cache was evicted! Must query DB again
        listFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("ViewListDataFetcher caches view queries and invalidates when dependent model mutates")
    void testViewListFetcherCachingAndInvalidation() throws Exception {
        ViewListDataFetcher viewFetcher = new ViewListDataFetcher(
                dashboardView,
                registry,
                executor,
                new ArgumentBinder(),
                dialect
        );

        DataFetchingEnvironment env = createMockEnvironment(Collections.emptyMap());

        // 1. Initial view query caches under tags ["Student", "Enrollment"]
        viewFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // 2. Second view query hits cache
        viewFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // 3. Mutate Student entity
        ModelCreateDataFetcher createFetcher = new ModelCreateDataFetcher(
                studentModel,
                executor,
                dialect,
                "input"
        );
        createFetcher.get(createMockEnvironment(Map.of("input", Map.of("name", "Charlie"))));

        // 4. View query after Student mutation is evicted because Student tag was invalidated!
        viewFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("TenantContext partitions cache keys so tenants never share cached GraphQL responses")
    void testTenantPartitioningInGraphQL() throws Exception {
        ModelListDataFetcher listFetcher = new ModelListDataFetcher(
                studentModel,
                registry,
                executor,
                new ArgumentBinder(),
                dialect
        );

        DataFetchingEnvironment env = createMockEnvironment(Collections.emptyMap());

        TenantContext.setTenantId("tenant-100");
        listFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Same query for tenant-100 hits cache
        listFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Switch to tenant-200: cache miss, must execute query
        TenantContext.setTenantId("tenant-200");
        listFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(2);
    }

    private DataFetchingEnvironment createMockEnvironmentWithFields(Map<String, Object> arguments, List<String> fieldNames) {
        DataFetchingFieldSelectionSet selectionSet = (DataFetchingFieldSelectionSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingFieldSelectionSet.class},
                (proxy, method, args) -> {
                    if ("getFields".equals(method.getName())) {
                        List<SelectedField> fields = new java.util.ArrayList<>();
                        for (String fName : fieldNames) {
                            SelectedField field = (SelectedField) Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[]{SelectedField.class},
                                    (fProxy, fMethod, fArgs) -> {
                                        if ("getName".equals(fMethod.getName())) return fName;
                                        if ("getArguments".equals(fMethod.getName())) return Collections.emptyMap();
                                        return null;
                                    }
                            );
                            fields.add(field);
                        }
                        return fields;
                    }
                    return Collections.emptyList();
                }
        );

        return (DataFetchingEnvironment) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingEnvironment.class},
                (proxy, method, args) -> {
                    if ("containsArgument".equals(method.getName())) {
                        return arguments != null && args.length > 0 && arguments.containsKey(args[0]);
                    }
                    if ("getArguments".equals(method.getName())) {
                        return arguments != null ? arguments : Collections.emptyMap();
                    }
                    if ("getArgument".equals(method.getName())) {
                        return arguments != null && args.length > 0 ? arguments.get(args[0]) : null;
                    }
                    if ("getSelectionSet".equals(method.getName())) {
                        return selectionSet;
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("ModelSingleDataFetcher partitions cache keys by tenant so tenant A and B never share entity cache")
    void testModelSingleFetcherTenantPartitioning() throws Exception {
        ModelSingleDataFetcher singleFetcher = new ModelSingleDataFetcher(
                studentModel,
                registry,
                executor
        );

        DataFetchingEnvironment env = createMockEnvironmentWithFields(Map.of("id", 1L), List.of("id", "name"));

        TenantContext.setTenantId("tenant-alpha");
        singleFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Same tenant hits cache
        singleFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Different tenant with same entity ID: cache miss!
        TenantContext.setTenantId("tenant-beta");
        singleFetcher.get(env);
        assertThat(queryExecutionCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("ModelSingleDataFetcher caches per projection so different selections never return stale partial objects")
    void testModelSingleFetcherProjectionAwareCaching() throws Exception {
        ModelSingleDataFetcher singleFetcher = new ModelSingleDataFetcher(
                studentModel,
                registry,
                executor
        );

        TenantContext.setTenantId("tenant-alpha");

        // Selection 1: id, name
        DataFetchingEnvironment envNarrow = createMockEnvironmentWithFields(Map.of("id", 1L), List.of("id", "name"));
        singleFetcher.get(envNarrow);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Repeated selection 1: cache hit
        singleFetcher.get(envNarrow);
        assertThat(queryExecutionCount.get()).isEqualTo(1);

        // Selection 2: id, name, email (different projection): cache miss!
        DataFetchingEnvironment envWide = createMockEnvironmentWithFields(Map.of("id", 1L), List.of("id", "name", "email"));
        singleFetcher.get(envWide);
        assertThat(queryExecutionCount.get()).isEqualTo(2);
    }
}
