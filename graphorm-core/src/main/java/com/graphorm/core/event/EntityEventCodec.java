package com.graphorm.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/**
 * High-performance JSON serializer and deserializer for {@link EntityEvent} payloads.
 */
public final class EntityEventCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EntityEventCodec() {}

    /**
     * Serializes an {@link EntityEvent} to a compact JSON string.
     *
     * @param event the event to serialize
     * @return JSON string
     */
    public static String toJson(EntityEvent event) {
        Objects.requireNonNull(event, "EntityEvent must not be null");
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize EntityEvent to JSON: " + event, e);
        }
    }

    /**
     * Deserializes an {@link EntityEvent} from a JSON string.
     *
     * @param json the JSON string
     * @return deserialized EntityEvent
     */
    public static EntityEvent fromJson(String json) {
        Objects.requireNonNull(json, "JSON string must not be null");
        try {
            return MAPPER.readValue(json, EntityEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize EntityEvent from JSON: " + json, e);
        }
    }
}
