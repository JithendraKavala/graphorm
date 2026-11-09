package com.graphorm.core.event;

import java.util.concurrent.Flow;

/**
 * Service Provider Interface (SPI) for real-time entity event publication and reactive subscription.
 * Provides decoupled event dissemination for GraphQL subscriptions across both PostgreSQL LISTEN/NOTIFY
 * and in-memory fallback transports.
 */
public interface EntityEventBroker extends AutoCloseable {

    /**
     * Publishes an entity mutation event to all active subscribers.
     *
     * @param event the event to publish
     */
    void publish(EntityEvent event);

    /**
     * Returns an unfiltered reactive publisher for all entity events.
     *
     * @return Flow.Publisher of EntityEvent
     */
    Flow.Publisher<EntityEvent> publisher();

    default Flow.Publisher<EntityEvent> asPublisher() {
        return publisher();
    }

    /**
     * Returns a reactive publisher filtered by the specified entity name.
     *
     * @param entityName the entity name to filter by
     * @return filtered Flow.Publisher of EntityEvent
     */
    Flow.Publisher<EntityEvent> publisher(String entityName);

    /**
     * Returns a reactive publisher filtered by entity name and event type.
     *
     * @param entityName the entity name to filter by
     * @param eventType  the event operation type to filter by
     * @return filtered Flow.Publisher of EntityEvent
     */
    Flow.Publisher<EntityEvent> publisher(String entityName, NotifyEventType eventType);

    @Override
    default void close() {
        // Default no-op
    }
}
