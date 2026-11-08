package com.graphorm.example.university.generated.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create input payload for {@link AuditLog}.
 */
public record AuditLogCreateInput(
        java.lang.String action,
        java.lang.String details
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (this.action != null) map.put("action", this.action);
        if (this.details != null) map.put("details", this.details);
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private String action;

        private String details;

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public AuditLogCreateInput build() {
            return new AuditLogCreateInput(action, details);
        }
    }
}
