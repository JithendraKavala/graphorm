package com.graphorm.example.dashboard.generated.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Update input payload for {@link Department}.
 */
public record DepartmentUpdateInput(
        java.lang.String name,
        java.lang.String code
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (this.name != null) map.put("dept_name", this.name);
        if (this.code != null) map.put("code", this.code);
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private String name;

        private String code;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public DepartmentUpdateInput build() {
            return new DepartmentUpdateInput(name, code);
        }
    }
}
