package com.graphorm.jdbc.event;

import com.graphorm.core.event.EntityEvent;
import com.graphorm.core.event.EntityEventCodec;
import com.graphorm.core.event.InMemoryEntityEventBroker;
import com.graphorm.core.event.NotifyEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostgresNotificationListener Daemon Tests")
class PostgresNotificationListenerTest {

    @Test
    @DisplayName("Driver availability check returns true when PGConnection is on classpath")
    void testDriverAvailable() {
        assertThat(PostgresNotificationListener.isDriverAvailable()).isTrue();
    }

    @Test
    @DisplayName("Listener receives PGNotification, decodes JSON payload and publishes to event broker")
    void testListenAndPublish() throws Exception {
        InMemoryEntityEventBroker broker = new InMemoryEntityEventBroker();
        List<EntityEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        broker.asPublisher().subscribe(new Flow.Subscriber<EntityEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(EntityEvent item) {
                received.add(item);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        EntityEvent originalEvent = new EntityEvent(
                "Student",
                NotifyEventType.CREATE,
                100L,
                Map.of("id", 100L, "name", "Bob"),
                "tenant-xyz",
                Instant.now()
        );
        String jsonPayload = EntityEventCodec.toJson(originalEvent);

        PGNotification mockNotification = new PGNotification() {
            @Override
            public String getName() {
                return "graphorm_events";
            }

            @Override
            public int getPID() {
                return 1234;
            }

            @Override
            public String getParameter() {
                return jsonPayload;
            }
        };

        AtomicBoolean delivered = new AtomicBoolean(false);

        PGConnection mockPgConnection = (PGConnection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PGConnection.class},
                (proxy, method, args) -> {
                    if ("getNotifications".equals(method.getName())) {
                        if (!delivered.getAndSet(true)) {
                            return new PGNotification[]{mockNotification};
                        }
                        Thread.sleep(100);
                        return new PGNotification[0];
                    }
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class, PGConnection.class},
                (proxy, method, args) -> {
                    String m = method.getName();
                    if ("isWrapperFor".equals(m)) {
                        return PGConnection.class.equals(args[0]);
                    }
                    if ("unwrap".equals(m)) {
                        return mockPgConnection;
                    }
                    if ("createStatement".equals(m)) {
                        return createMockStatement();
                    }
                    if ("close".equals(m)) {
                        return null;
                    }
                    return null;
                }
        );

        DataSource mockDs = (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return mockConnection;
                    }
                    return null;
                }
        );

        try (PostgresNotificationListener listener = new PostgresNotificationListener(mockDs, "graphorm_events", broker)) {
            listener.start();
            assertThat(listener.isRunning()).isTrue();

            boolean ok = latch.await(3, TimeUnit.SECONDS);
            assertThat(ok).isTrue();
            assertThat(received).hasSize(1);

            EntityEvent receivedEvent = received.getFirst();
            assertThat(receivedEvent.entityName()).isEqualTo("Student");
            assertThat(receivedEvent.eventType()).isEqualTo(NotifyEventType.CREATE);
            assertThat(String.valueOf(receivedEvent.entityId())).isEqualTo("100");
            assertThat(receivedEvent.payload()).containsEntry("name", "Bob");
            assertThat(receivedEvent.tenantId()).isEqualTo("tenant-xyz");

            listener.stop();
            assertThat(listener.isRunning()).isFalse();
        }
    }

    private Statement createMockStatement() {
        return (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        return true;
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    return null;
                }
        );
    }
}
