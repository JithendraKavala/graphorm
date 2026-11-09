package com.graphorm.core.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable domain event emitted when an entity is created, updated, or deleted.
 * Carries tenant partition metadata to prevent cross-tenant event leaks.
 *
 * @param entityName the logical entity name (e.g. "Student")
 * @param eventType  the event operation (CREATE, UPDATE, DELETE)
 * @param entityId   the primary key identifier of the entity
 * @param payload    the entity attribute values (empty for DELETE)
 * @param tenantId   the tenant partition key (null if single-tenant or global)
 * @param timestamp  the instant the event was generated
 */
public record EntityEvent(
        String entityName,
        NotifyEventType eventType,
        Object entityId,
        Map<String, Object> payload,
        String tenantId,
        Instant timestamp
) {

    public EntityEvent {
        Objects.requireNonNull(entityName, "entityName must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (payload == null || payload.isEmpty()) {
            payload = Map.of();
        } else {
            Map<String, Object> copy = new LinkedHashMap<>();
            payload.forEach((k, v) -> {
                if (k != null) {
                    copy.put(k, v);
                }
            });
            payload = Collections.unmodifiableMap(copy);
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static EntityEvent of(String entityName, NotifyEventType eventType, Object entityId, Map<String, Object> payload) {
        return new EntityEvent(entityName, eventType, entityId, payload, null, Instant.now());
    }

    public static EntityEvent of(String entityName, NotifyEventType eventType, Object entityId, Map<String, Object> payload, String tenantId) {
        return new EntityEvent(entityName, eventType, entityId, payload, tenantId, Instant.now());
    }
}
