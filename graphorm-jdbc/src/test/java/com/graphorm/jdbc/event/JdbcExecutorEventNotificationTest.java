package com.graphorm.jdbc.event;

import com.graphorm.core.event.EntityEvent;
import com.graphorm.core.event.InMemoryEntityEventBroker;
import com.graphorm.core.event.NotifyEventType;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.NotifyMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JdbcExecutor Event Notification Tests")
class JdbcExecutorEventNotificationTest {

    private InMemoryEntityEventBroker broker;
    private JdbcExecutor executor;
    private List<EntityEvent> receivedEvents;
    private List<Map<String, Object>> stubRows;

    @BeforeEach
    void setUp() {
        broker = new InMemoryEntityEventBroker();
        receivedEvents = new CopyOnWriteArrayList<>();
        stubRows = new ArrayList<>();

        broker.asPublisher().subscribe(new Flow.Subscriber<EntityEvent>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(EntityEvent item) {
                receivedEvents.add(item);
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        DataSource mockDs = createMockDataSource();
        executor = new JdbcExecutor(mockDs, new PostgreSqlDialect());
        executor.setEventBroker(broker);

        ModelMetadata studentModel = ModelMetadata.builder("Student").tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("tenantId", String.class).dbColumnName("tenant_id").build())
                .multiTenant(true)
                .tenantColumn("tenant_id")
                .notifyMetadata(NotifyMetadata.of(Set.of(NotifyEventType.CREATE, NotifyEventType.UPDATE, NotifyEventType.DELETE)))
                .build();

        ModelMetadata auditModel = ModelMetadata.builder("AuditLog").tableName("audit_logs")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("message", String.class).build())
                .notifyMetadata(NotifyMetadata.DISABLED)
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(studentModel).addModel(auditModel).build();
        executor.setModelRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Insert on @notify model emits CREATE event with payload and tenant ID")
    void testInsertEmitsEvent() {
        TenantContext.setTenantId("tenant-alpha");

        stubRows.add(Map.of("id", 42L, "name", "Alice", "tenant_id", "tenant-alpha"));

        InsertQuery query = InsertQuery.into("students")
                .value("name", "Alice")
                .value("tenant_id", "tenant-alpha")
                .build();

        executor.executeInsertAndReturn(query);

        assertThat(receivedEvents).hasSize(1);
        EntityEvent event = receivedEvents.getFirst();
        assertThat(event.entityName()).isEqualTo("Student");
        assertThat(event.eventType()).isEqualTo(NotifyEventType.CREATE);
        assertThat(event.entityId()).isEqualTo(42L);
        assertThat(event.payload()).containsEntry("name", "Alice");
        assertThat(event.tenantId()).isEqualTo("tenant-alpha");
    }

    @Test
    @DisplayName("Update on @notify model emits UPDATE event with ID and payload")
    void testUpdateEmitsEvent() {
        TenantContext.setTenantId("tenant-beta");

        stubRows.add(Map.of("id", 42L, "name", "Alice Updated", "tenant_id", "tenant-beta"));

        UpdateQuery query = UpdateQuery.table("students")
                .set("name", "Alice Updated")
                .where(WherePredicate.eq("id", 42L))
                .build();

        executor.executeUpdateAndReturn(query);

        assertThat(receivedEvents).hasSize(1);
        EntityEvent event = receivedEvents.getFirst();
        assertThat(event.entityName()).isEqualTo("Student");
        assertThat(event.eventType()).isEqualTo(NotifyEventType.UPDATE);
        assertThat(event.entityId()).isEqualTo(42L);
        assertThat(event.payload()).containsEntry("name", "Alice Updated");
        assertThat(event.tenantId()).isEqualTo("tenant-beta");
    }

    @Test
    @DisplayName("Delete on @notify model emits DELETE event with ID")
    void testDeleteEmitsEvent() {
        TenantContext.setTenantId("tenant-alpha");

        DeleteQuery query = new DeleteQuery(
                com.graphorm.core.query.SourceTable.table("students"),
                WherePredicate.eq("id", 99L)
        );

        executor.executeUpdate(query);

        assertThat(receivedEvents).hasSize(1);
        EntityEvent event = receivedEvents.getFirst();
        assertThat(event.entityName()).isEqualTo("Student");
        assertThat(event.eventType()).isEqualTo(NotifyEventType.DELETE);
        assertThat(event.entityId()).isEqualTo(99L);
        assertThat(event.tenantId()).isEqualTo("tenant-alpha");
    }

    @Test
    @DisplayName("Insert on model without @notify does NOT emit events")
    void testUnnotifiedModelDoesNotEmit() {
        stubRows.add(Map.of("id", 1L, "message", "System reboot"));

        InsertQuery query = InsertQuery.into("audit_logs")
                .value("message", "System reboot")
                .build();

        executor.executeInsertAndReturn(query);
        assertThat(receivedEvents).isEmpty();
    }

    private DataSource createMockDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return createMockConnection();
                    }
                    return null;
                }
        );
    }

    private Connection createMockConnection() {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        return createMockPreparedStatement();
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    return null;
                }
        );
    }

    private PreparedStatement createMockPreparedStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String m = method.getName();
                    if ("executeQuery".equals(m)) {
                        return createMockResultSet();
                    }
                    if ("executeUpdate".equals(m)) {
                        return 1;
                    }
                    if ("close".equals(m)) {
                        return null;
                    }
                    return null;
                }
        );
    }

    private ResultSet createMockResultSet() {
        int[] rowIdx = new int[]{-1};
        ResultSetMetaData meta = (ResultSetMetaData) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class},
                (proxy, method, args) -> {
                    if ("getColumnCount".equals(method.getName())) {
                        return stubRows.isEmpty() ? 0 : stubRows.getFirst().size();
                    }
                    if ("getColumnLabel".equals(method.getName()) || "getColumnName".equals(method.getName())) {
                        int index = (Integer) args[0] - 1;
                        return new ArrayList<>(stubRows.getFirst().keySet()).get(index);
                    }
                    return null;
                }
        );

        return (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String m = method.getName();
                    if ("getMetaData".equals(m)) {
                        return meta;
                    }
                    if ("next".equals(m)) {
                        rowIdx[0]++;
                        return rowIdx[0] < stubRows.size();
                    }
                    if ("getObject".equals(m)) {
                        if (args[0] instanceof Integer idx) {
                            String col = new ArrayList<>(stubRows.getFirst().keySet()).get(idx - 1);
                            return stubRows.get(rowIdx[0]).get(col);
                        } else {
                            String col = (String) args[0];
                            return stubRows.get(rowIdx[0]).get(col);
                        }
                    }
                    if ("close".equals(m)) {
                        return null;
                    }
                    return null;
                }
        );
    }
}
