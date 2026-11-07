package com.graphorm.example.dashboard.generated.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create input payload for {@link Course}.
 */
public record CourseCreateInput(
        java.lang.String title,
        java.lang.String code,
        java.lang.Integer credits,
        java.lang.Integer departmentId,
        java.lang.Integer instructorId
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (this.title != null) map.put("title", this.title);
        if (this.code != null) map.put("code", this.code);
        if (this.credits != null) map.put("credits", this.credits);
        if (this.departmentId != null) map.put("department_id", this.departmentId);
        if (this.instructorId != null) map.put("instructor_id", this.instructorId);
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private String title;

        private String code;

        private Integer credits;

        private Integer departmentId;

        private Integer instructorId;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder credits(Integer credits) {
            this.credits = credits;
            return this;
        }

        public Builder departmentId(Integer departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public Builder instructorId(Integer instructorId) {
            this.instructorId = instructorId;
            return this;
        }

        public CourseCreateInput build() {
            return new CourseCreateInput(title, code, credits, departmentId, instructorId);
        }
    }
}
