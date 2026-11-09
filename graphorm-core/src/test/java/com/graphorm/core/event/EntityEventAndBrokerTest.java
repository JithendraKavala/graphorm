package com.graphorm.core.event;

import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.NotifyMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityEventAndBrokerTest {

    @Test
    @DisplayName("NotifyEventType parsing and case-insensitivity")
    void testNotifyEventTypeParsing() {
        assertThat(NotifyEventType.fromString("create")).isEqualTo(NotifyEventType.CREATE);
        assertThat(NotifyEventType.fromString("UPDATE")).isEqualTo(NotifyEventType.UPDATE);
        assertThat(NotifyEventType.fromString("delete")).isEqualTo(NotifyEventType.DELETE);
        assertThatThrownBy(() -> NotifyEventType.fromString("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NotifyMetadata creation, default state, and listeners")
    void testNotifyMetadata() {
        NotifyMetadata disabled = NotifyMetadata.disabled();
        assertThat(disabled.isNotifyEnabled()).isFalse();
        assertThat(disabled.events()).isEmpty();
        assertThat(disabled.listensTo(NotifyEventType.CREATE)).isFalse();

        NotifyMetadata custom = NotifyMetadata.of(Set.of(NotifyEventType.CREATE, NotifyEventType.UPDATE));
        assertThat(custom.isNotifyEnabled()).isTrue();
        assertThat(custom.listensTo(NotifyEventType.CREATE)).isTrue();
        assertThat(custom.listensTo(NotifyEventType.UPDATE)).isTrue();
        assertThat(custom.listensTo(NotifyEventType.DELETE)).isFalse();
    }

    @Test
    @DisplayName("EntityEvent immutability, factory methods, and JSON codec")
    void testEntityEventCodec() {
        EntityEvent event = EntityEvent.of("Student", NotifyEventType.CREATE, 101, Map.of("name", "Alice", "gpa", 3.9), "tenant_alpha");

        assertThat(event.entityName()).isEqualTo("Student");
        assertThat(event.eventType()).isEqualTo(NotifyEventType.CREATE);
        assertThat(event.entityId()).isEqualTo(101);
        assertThat(event.payload()).containsEntry("name", "Alice");
        assertThat(event.tenantId()).isEqualTo("tenant_alpha");
        assertThat(event.timestamp()).isNotNull();

        String json = EntityEventCodec.toJson(event);
        assertThat(json).contains("\"entityName\":\"Student\"");
        assertThat(json).contains("\"eventType\":\"CREATE\"");
        assertThat(json).contains("\"tenantId\":\"tenant_alpha\"");

        EntityEvent deserialized = EntityEventCodec.fromJson(json);
        assertThat(deserialized.entityName()).isEqualTo(event.entityName());
        assertThat(deserialized.eventType()).isEqualTo(event.eventType());
        assertThat(deserialized.entityId()).isEqualTo(101);
        assertThat(deserialized.tenantId()).isEqualTo("tenant_alpha");
        assertThat(deserialized.payload()).containsEntry("name", "Alice");
    }

    @Test
    @DisplayName("InMemoryEntityEventBroker reactive subscription and event delivery")
    void testInMemoryEntityEventBrokerDelivery() throws InterruptedException {
        try (InMemoryEntityEventBroker broker = new InMemoryEntityEventBroker()) {
            List<EntityEvent> receivedAll = new ArrayList<>();
            List<EntityEvent> receivedStudent = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(3);

            broker.publisher().subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(10);
                }

                @Override
                public void onNext(EntityEvent item) {
                    receivedAll.add(item);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}
            });

            broker.publisher("Student", NotifyEventType.CREATE).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(10);
                }

                @Override
                public void onNext(EntityEvent item) {
                    receivedStudent.add(item);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}
            });

            broker.publish(EntityEvent.of("Student", NotifyEventType.CREATE, 1, Map.of("name", "John")));
            broker.publish(EntityEvent.of("Department", NotifyEventType.CREATE, 2, Map.of("name", "CS")));

            boolean completed = latch.await(2, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(receivedAll).hasSize(2);
            assertThat(receivedStudent).hasSize(1);
            assertThat(receivedStudent.getFirst().entityName()).isEqualTo("Student");
        }
    }

    @Test
    @DisplayName("ModelMetadata builder attaches NotifyMetadata correctly")
    void testModelMetadataNotifyAttachment() {
        ModelMetadata model = ModelMetadata.builder("Student")
                .tableName("students")
                .notify(Set.of(NotifyEventType.CREATE, NotifyEventType.DELETE))
                .build();

        assertThat(model.isNotifyEnabled()).isTrue();
        assertThat(model.notifyMetadata().listensTo(NotifyEventType.CREATE)).isTrue();
        assertThat(model.notifyMetadata().listensTo(NotifyEventType.UPDATE)).isFalse();
        assertThat(model.notifyMetadata().listensTo(NotifyEventType.DELETE)).isTrue();
    }
}
