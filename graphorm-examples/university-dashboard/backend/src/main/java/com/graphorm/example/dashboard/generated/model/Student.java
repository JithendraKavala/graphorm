package com.graphorm.example.dashboard.generated.model;

/**
 * Immutable Java 21 model record representing the {@code Student} entity (table {@code students}).
 */
public record Student(
        java.lang.Integer id,
        java.lang.String name,
        java.lang.String email,
        java.lang.Double gpa,
        java.lang.Integer departmentId
) {
    public static final String MODEL_NAME = "Student";

    public static final String TABLE_NAME = "students";

    /**
     * Creates a new {@link Builder} for {@link Student}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this {@link Student} with the {@code id} component updated.
     */
    public Student withId(Integer id) {
        return new Student(id, this.name, this.email, this.gpa, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Student} with the {@code name} component updated.
     */
    public Student withName(String name) {
        return new Student(this.id, name, this.email, this.gpa, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Student} with the {@code email} component updated.
     */
    public Student withEmail(String email) {
        return new Student(this.id, this.name, email, this.gpa, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Student} with the {@code gpa} component updated.
     */
    public Student withGpa(Double gpa) {
        return new Student(this.id, this.name, this.email, gpa, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Student} with the {@code departmentId} component updated.
     */
    public Student withDepartmentId(Integer departmentId) {
        return new Student(this.id, this.name, this.email, this.gpa, departmentId);
    }

    /**
     * Fluent builder for constructing {@link Student} instances.
     */
    public static final class Builder {
        private Integer id;

        private String name;

        private String email;

        private Double gpa;

        private Integer departmentId;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

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

        public Student build() {
            return new Student(id, name, email, gpa, departmentId);
        }
    }
}
