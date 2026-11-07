package com.graphorm.example.university.generated.model;

/**
 * Immutable Java 21 model record representing the {@code Department} entity (table {@code departments}).
 */
public record Department(
        java.lang.Integer id,
        java.lang.String name,
        java.lang.String code
) {
    public static final String MODEL_NAME = "Department";

    public static final String TABLE_NAME = "departments";

    /**
     * Creates a new {@link Builder} for {@link Department}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this {@link Department} with the {@code id} component updated.
     */
    public Department withId(Integer id) {
        return new Department(id, this.name, this.code);
    }

    /**
     * Returns a copy of this {@link Department} with the {@code name} component updated.
     */
    public Department withName(String name) {
        return new Department(this.id, name, this.code);
    }

    /**
     * Returns a copy of this {@link Department} with the {@code code} component updated.
     */
    public Department withCode(String code) {
        return new Department(this.id, this.name, code);
    }

    /**
     * Fluent builder for constructing {@link Department} instances.
     */
    public static final class Builder {
        private Integer id;

        private String name;

        private String code;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Department build() {
            return new Department(id, name, code);
        }
    }
}
