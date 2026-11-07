package com.graphorm.example.dashboard.generated.model;

/**
 * Immutable Java 21 model record representing the {@code Course} entity (table {@code courses}).
 */
public record Course(
        java.lang.Integer id,
        java.lang.String title,
        java.lang.String code,
        java.lang.Integer credits,
        java.lang.Integer departmentId,
        java.lang.Integer instructorId
) {
    public static final String MODEL_NAME = "Course";

    public static final String TABLE_NAME = "courses";

    /**
     * Creates a new {@link Builder} for {@link Course}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this {@link Course} with the {@code id} component updated.
     */
    public Course withId(Integer id) {
        return new Course(id, this.title, this.code, this.credits, this.departmentId, this.instructorId);
    }

    /**
     * Returns a copy of this {@link Course} with the {@code title} component updated.
     */
    public Course withTitle(String title) {
        return new Course(this.id, title, this.code, this.credits, this.departmentId, this.instructorId);
    }

    /**
     * Returns a copy of this {@link Course} with the {@code code} component updated.
     */
    public Course withCode(String code) {
        return new Course(this.id, this.title, code, this.credits, this.departmentId, this.instructorId);
    }

    /**
     * Returns a copy of this {@link Course} with the {@code credits} component updated.
     */
    public Course withCredits(Integer credits) {
        return new Course(this.id, this.title, this.code, credits, this.departmentId, this.instructorId);
    }

    /**
     * Returns a copy of this {@link Course} with the {@code departmentId} component updated.
     */
    public Course withDepartmentId(Integer departmentId) {
        return new Course(this.id, this.title, this.code, this.credits, departmentId, this.instructorId);
    }

    /**
     * Returns a copy of this {@link Course} with the {@code instructorId} component updated.
     */
    public Course withInstructorId(Integer instructorId) {
        return new Course(this.id, this.title, this.code, this.credits, this.departmentId, instructorId);
    }

    /**
     * Fluent builder for constructing {@link Course} instances.
     */
    public static final class Builder {
        private Integer id;

        private String title;

        private String code;

        private Integer credits;

        private Integer departmentId;

        private Integer instructorId;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

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

        public Course build() {
            return new Course(id, title, code, credits, departmentId, instructorId);
        }
    }
}
