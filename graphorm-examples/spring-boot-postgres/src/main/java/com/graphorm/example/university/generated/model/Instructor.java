package com.graphorm.example.university.generated.model;

/**
 * Immutable Java 21 model record representing the {@code Instructor} entity (table {@code instructors}).
 */
public record Instructor(
        java.lang.Integer id,
        java.lang.String name,
        java.lang.String email,
        java.lang.Double salary,
        java.lang.Integer departmentId
) {
    public static final String MODEL_NAME = "Instructor";

    public static final String TABLE_NAME = "instructors";

    /**
     * Creates a new {@link Builder} for {@link Instructor}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this {@link Instructor} with the {@code id} component updated.
     */
    public Instructor withId(Integer id) {
        return new Instructor(id, this.name, this.email, this.salary, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Instructor} with the {@code name} component updated.
     */
    public Instructor withName(String name) {
        return new Instructor(this.id, name, this.email, this.salary, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Instructor} with the {@code email} component updated.
     */
    public Instructor withEmail(String email) {
        return new Instructor(this.id, this.name, email, this.salary, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Instructor} with the {@code salary} component updated.
     */
    public Instructor withSalary(Double salary) {
        return new Instructor(this.id, this.name, this.email, salary, this.departmentId);
    }

    /**
     * Returns a copy of this {@link Instructor} with the {@code departmentId} component updated.
     */
    public Instructor withDepartmentId(Integer departmentId) {
        return new Instructor(this.id, this.name, this.email, this.salary, departmentId);
    }

    /**
     * Fluent builder for constructing {@link Instructor} instances.
     */
    public static final class Builder {
        private Integer id;

        private String name;

        private String email;

        private Double salary;

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

        public Builder salary(Double salary) {
            this.salary = salary;
            return this;
        }

        public Builder departmentId(Integer departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public Instructor build() {
            return new Instructor(id, name, email, salary, departmentId);
        }
    }
}
