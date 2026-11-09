package com.graphorm.core.event;

import java.util.Locale;

/**
 * Event types supported by the real-time notification engine.
 */
public enum NotifyEventType {
    CREATE,
    UPDATE,
    DELETE;

    /**
     * Parses a string (case-insensitive) into a {@link NotifyEventType}.
     *
     * @param value the string representation
     * @return the matching NotifyEventType
     * @throws IllegalArgumentException if value does not match any event type
     */
    public static NotifyEventType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NotifyEventType value must not be blank");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
