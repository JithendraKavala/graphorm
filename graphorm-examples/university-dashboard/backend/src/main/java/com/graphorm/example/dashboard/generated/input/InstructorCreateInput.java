package com.graphorm.example.dashboard.generated.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create input payload for {@link Instructor}.
 */
public record InstructorCreateInput(
        java.lang.String name,
        java.lang.String email,
        java.lang.Double salary,
        java.lang.Integer departmentId
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (this.name != null) map.put("name", this.name);
        if (this.email != null) map.put("email", this.email);
        if (this.salary != null) map.put("salary", this.salary);
        if (this.departmentId != null) map.put("department_id", this.departmentId);
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private String name;

        private String email;

        private Double salary;

        private Integer departmentId;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder salary(Double salary) {
            this.salary = salary;
            return this;
        }

        public Builder departmentId(Integer departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public InstructorCreateInput build() {
            return new InstructorCreateInput(name, email, salary, departmentId);
        }
    }
}
