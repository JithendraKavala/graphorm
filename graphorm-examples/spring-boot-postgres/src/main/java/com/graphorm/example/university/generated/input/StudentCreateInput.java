package com.graphorm.example.university.generated.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create input payload for {@link Student}.
 */
public record StudentCreateInput(
        java.lang.String name,
        java.lang.String email,
        java.lang.Double gpa,
        java.lang.Integer departmentId
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (this.name != null) map.put("name", this.name);
        if (this.email != null) map.put("email", this.email);
        if (this.gpa != null) map.put("gpa", this.gpa);
        if (this.departmentId != null) map.put("department_id", this.departmentId);
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private String name;

        private String email;

        private Double gpa;

        private Integer departmentId;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder gpa(Double gpa) {
            this.gpa = gpa;
            return this;
        }

        public Builder departmentId(Integer departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public StudentCreateInput build() {
            return new StudentCreateInput(name, email, gpa, departmentId);
        }
    }
}
