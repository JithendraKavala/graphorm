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
import com.graphorm.graphql.fetcher.EntityRowNormalizer;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
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

@DisplayName("GraphQL Real-Time Subscriptions Execution Tests")
class GraphQlSubscriptionExecutionTest {

    private InMemoryEntityEventBroker broker;
    private GraphQL graphQL;
    private ModelMetadata studentModel;

    private static final String SDL = """
            type Query {
                students: [Student!]!
            }
            type Mutation {
                createStudent(input: String): Student!
            }
            type Subscription {
                studentCreated: Student!
                studentUpdated(id: ID): Student!
                studentDeleted: ID!
            }
            type Student {
                id: ID!
                name: String!
                gpa: Float
            }
            """;

    @BeforeEach
    void setUp() {
        broker = new InMemoryEntityEventBroker();

        studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("gpa", Double.class).nullable(true).build())
                .notifyMetadata(NotifyMetadata.of(Set.of(NotifyEventType.CREATE, NotifyEventType.UPDATE, NotifyEventType.DELETE)))
                .build();

        ModelRegistry modelRegistry = ModelRegistry.builder()
                .addModel(studentModel)
                .build();

        javax.sql.DataSource dummyDs = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{javax.sql.DataSource.class},
                (proxy, method, args) -> null
        );
        com.graphorm.jdbc.executor.JdbcExecutor dummyEngine = new com.graphorm.jdbc.executor.JdbcExecutor(dummyDs, new PostgreSqlDialect()) {
            @Override
            public List<Map<String, Object>> executeSelect(SelectQuery query) {
                return List.of(Map.of("id", 42L, "name", "Alice", "gpa", 3.9));
            }

            @Override
            public Optional<Map<String, Object>> executeSelectOne(SelectQuery query) {
                return Optional.of(Map.of("id", 42L, "name", "Alice", "gpa", 3.9));
            }
        };
        dummyEngine.setEventBroker(broker);

        GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(
                modelRegistry,
                dummyEngine,
                new PostgreSqlDialect(),
                null
        );

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(SDL);

        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
        registrar.register(wiringBuilder, typeRegistry);

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema schema = schemaGenerator.makeExecutableSchema(typeRegistry, wiringBuilder.build());
        graphQL = GraphQL.newGraphQL(schema).build();
    }

    @Test
    @DisplayName("studentCreated subscription stream receives newly created entity and resolves selection set")
    void testStudentCreatedSubscription() throws Exception {
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { studentCreated { id name gpa } }")
                .build();

        ExecutionResult executionResult = graphQL.execute(input);
        assertThat(executionResult.getErrors()).isEmpty();

        Publisher<ExecutionResult> stream = executionResult.getData();
        assertThat(stream).isNotNull();

        List<ExecutionResult> results = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        stream.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ExecutionResult item) {
                results.add(item);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
        });

        // Publish CREATE event
        broker.publish(new EntityEvent(
                "Student",
                NotifyEventType.CREATE,
                42L,
                Map.of("id", 42L, "name", "Alice", "gpa", 3.9),
                null,
                Instant.now()
        ));

        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(results).hasSize(1);

        Map<String, Object> data = results.getFirst().getData();
        assertThat(data).containsKey("studentCreated");
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) data.get("studentCreated");
        assertThat(student).containsEntry("name", "Alice");
        assertThat(student.get("id").toString()).isEqualTo("42");
    }

    @Test
    @DisplayName("studentUpdated subscription with ID filter receives only events matching ID")
    void testStudentUpdatedSubscriptionWithIdFilter() throws Exception {
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { studentUpdated(id: \"42\") { id name } }")
                .build();

        ExecutionResult executionResult = graphQL.execute(input);
        Publisher<ExecutionResult> stream = executionResult.getData();

        List<ExecutionResult> results = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        stream.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ExecutionResult item) {
                results.add(item);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
        });

        // Event for ID 99 should be filtered out
        broker.publish(new EntityEvent("Student", NotifyEventType.UPDATE, 99L, Map.of("id", 99L, "name", "Bob"), null, Instant.now()));
        // Event for ID 42 should match
        broker.publish(new EntityEvent("Student", NotifyEventType.UPDATE, 42L, Map.of("id", 42L, "name", "Alice Updated"), null, Instant.now()));

        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(results).hasSize(1);

        Map<String, Object> data = results.getFirst().getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) data.get("studentUpdated");
        assertThat(student.get("id").toString()).isEqualTo("42");
    }

    @Test
    @DisplayName("studentDeleted subscription receives deleted entity ID")
    void testStudentDeletedSubscription() throws Exception {
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("subscription { studentDeleted }")
                .build();

        ExecutionResult executionResult = graphQL.execute(input);
        Publisher<ExecutionResult> stream = executionResult.getData();

        List<ExecutionResult> results = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        stream.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ExecutionResult item) {
                results.add(item);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
        });

        broker.publish(new EntityEvent("Student", NotifyEventType.DELETE, 42L, Map.of("id", 42L), null, Instant.now()));

        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(results).hasSize(1);

        Map<String, Object> data = results.getFirst().getData();
        assertThat(data.get("studentDeleted").toString()).isEqualTo("42");
    }
}
