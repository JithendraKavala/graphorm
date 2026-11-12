package com.graphorm.graphql.engine;

import com.graphorm.core.event.EntityEvent;
import com.graphorm.core.event.InMemoryEntityEventBroker;
import com.graphorm.core.event.NotifyEventType;
import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.NotifyMetadata;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.exception.TenantContextMissingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GraphQL Subscriptions Multi-Tenant Isolation Tests")
class GraphQlSubscriptionTenantIsolationTest {

    private InMemoryEntityEventBroker broker;
    private GraphQL graphQL;

    private static final String SDL = """
            type Query {
                orders: [Order!]!
            }
            type Subscription {
                orderCreated(id: ID, tenantId: String): Order!
            }
            type Order {
                id: ID!
                total: Float!
                tenantId: String!
            }
            """;

    @BeforeEach
    void setUp() {
        broker = new InMemoryEntityEventBroker();

        ModelMetadata orderModel = ModelMetadata.builder("Order")
                .tableName("orders")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("total", Double.class).build())
                .addField(FieldMetadata.builder("tenantId", String.class).dbColumnName("tenant_id").build())
                .multiTenant(true)
                .tenantColumn("tenant_id")
                .notifyMetadata(NotifyMetadata.of(Set.of(NotifyEventType.CREATE)))
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(orderModel).build();

        javax.sql.DataSource dummyDs = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{javax.sql.DataSource.class},
                (proxy, method, args) -> null
        );
        com.graphorm.jdbc.executor.JdbcExecutor dummyEngine = new com.graphorm.jdbc.executor.JdbcExecutor(dummyDs, new PostgreSqlDialect()) {
            @Override
            public List<Map<String, Object>> executeSelect(SelectQuery query) {
                return List.of(Map.of("id", 101L, "total", 99.5, "tenant_id", "tenant-alpha"));
            }

            @Override
            public Optional<Map<String, Object>> executeSelectOne(SelectQuery query) {
                return Optional.of(Map.of("id", 101L, "total", 99.5, "tenant_id", "tenant-alpha"));
            }
        };
        dummyEngine.setEventBroker(broker);

        GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(registry, dummyEngine, new PostgreSqlDialect(), null);
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(SDL);
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
        registrar.register(wiringBuilder, typeRegistry);
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiringBuilder.build());
        graphQL = GraphQL.newGraphQL(schema).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Subscription subscriber for Tenant A never receives events belonging to Tenant B")
    void testTenantIsolationZeroLeak() throws Exception {
        TenantContext.setTenantId("tenant-alpha");

        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { orderCreated { id total tenantId } }")
                .graphQLContext(Map.of("tenantId", "tenant-alpha"))
                .build();

        ExecutionResult executionResult = graphQL.execute(input);
        Publisher<ExecutionResult> stream = executionResult.getData();

        List<ExecutionResult> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        stream.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ExecutionResult item) {
                received.add(item);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
        });

        // Publish event for Tenant B (should be ignored by Tenant A's subscriber)
        broker.publish(new EntityEvent(
                "Order",
                NotifyEventType.CREATE,
                999L,
                Map.of("id", 999L, "total", 500.0, "tenant_id", "tenant-beta"),
                "tenant-beta",
                Instant.now()
        ));

        // Publish event for Tenant A (should be received)
        broker.publish(new EntityEvent(
                "Order",
                NotifyEventType.CREATE,
                101L,
                Map.of("id", 101L, "total", 99.5, "tenant_id", "tenant-alpha"),
                "tenant-alpha",
                Instant.now()
        ));

        boolean ok = latch.await(3, TimeUnit.SECONDS);
        assertThat(ok).isTrue();
        assertThat(received).hasSize(1);

        Map<String, Object> data = received.getFirst().getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) data.get("orderCreated");
        assertThat(order.get("id").toString()).isEqualTo("101");
        assertThat(order.get("tenantId")).isEqualTo("tenant-alpha");
    }

    @Test
    @DisplayName("Subscription rejects spoofed tenantId argument that does not match authenticated tenant")
    void testSpoofedTenantArgumentThrowsAccessDenied() {
        TenantContext.setTenantId("tenant-alpha");

        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { orderCreated(tenantId: \"tenant-beta\") { id } }")
                .graphQLContext(Map.of("tenantId", "tenant-alpha"))
                .build();

        ExecutionResult result = graphQL.execute(input);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().getFirst().getMessage()).contains("Supplied tenantId argument does not match authenticated tenant context");
    }

    @Test
    @DisplayName("Subscription rejects tenantId argument when server context is completely absent")
    void testUntrustedTenantArgumentWithoutContextThrowsAccessDenied() {
        TenantContext.clear();

        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { orderCreated(tenantId: \"tenant-untrusted\") { id } }")
                .build();

        ExecutionResult result = graphQL.execute(input);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().getFirst().getMessage()).contains("Client-supplied tenant argument cannot be used as proof of tenant identity");
    }

    @Test
    @DisplayName("Subscription throws TenantContextMissingException when multi-tenant model has no tenant context")
    void testMissingTenantContextThrowsException() {
        TenantContext.clear();

        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { orderCreated { id } }")
                .build();

        ExecutionResult result = graphQL.execute(input);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().getFirst().getMessage()).contains("Tenant context is required for SUBSCRIBE on multi-tenant entity 'Order'");
    }

    @Test
    @DisplayName("Subscription succeeds when tenantId argument matches authenticated tenant")
    void testMatchingTenantArgumentSucceeds() throws Exception {
        TenantContext.setTenantId("tenant-alpha");

        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { orderCreated(tenantId: \"tenant-alpha\") { id total tenantId } }")
                .graphQLContext(Map.of("tenantId", "tenant-alpha"))
                .build();

        ExecutionResult result = graphQL.execute(input);
        Publisher<ExecutionResult> stream = result.getData();
        assertThat(stream).isNotNull();

        List<ExecutionResult> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        stream.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ExecutionResult item) {
                received.add(item);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
        });

        broker.publish(new EntityEvent(
                "Order",
                NotifyEventType.CREATE,
                101L,
                Map.of("id", 101L, "total", 99.5, "tenant_id", "tenant-alpha"),
                "tenant-alpha",
                Instant.now()
        ));

        boolean ok = latch.await(3, TimeUnit.SECONDS);
        assertThat(ok).isTrue();
        assertThat(received).hasSize(1);
    }
}
