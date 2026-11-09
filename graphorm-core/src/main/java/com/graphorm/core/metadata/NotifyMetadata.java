package com.graphorm.core.metadata;

import com.graphorm.core.event.NotifyEventType;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable metadata configuration for real-time model notifications declared via {@code @@notify}.
 *
 * @param isNotifyEnabled whether real-time change notifications are enabled for this model
 * @param events          set of event types to notify (CREATE, UPDATE, DELETE)
 */
public record NotifyMetadata(
        boolean isNotifyEnabled,
        Set<NotifyEventType> events
) {

    public static final NotifyMetadata DISABLED = new NotifyMetadata(false, Set.of());

    public NotifyMetadata {
        events = events == null ? Set.of() : Set.copyOf(events);
    }

    public static NotifyMetadata disabled() {
        return DISABLED;
    }

    public static NotifyMetadata of(Set<NotifyEventType> events) {
        Objects.requireNonNull(events, "Events set must not be null");
        return new NotifyMetadata(true, events);
    }

    public boolean listensTo(NotifyEventType type) {
        return isNotifyEnabled && events.contains(type);
    }

    public boolean supports(NotifyEventType type) {
        return listensTo(type);
    }
}
