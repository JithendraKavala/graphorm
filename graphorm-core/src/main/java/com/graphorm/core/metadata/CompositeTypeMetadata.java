package com.graphorm.core.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata record representing a composite type (value object) in GraphORM.
 * Composite types have no independent database identity or primary key.
 *
 * @param name   the declared composite type name (e.g. "Address")
 * @param fields the immutable list of declared field metadata
 */
public record CompositeTypeMetadata(
        String name,
        List<FieldMetadata> fields
) implements EntityMetadata {

    /**
     * Compact constructor enforcing immutability and defensive copying.
     */
    public CompositeTypeMetadata {
        Objects.requireNonNull(name, "Composite type name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Composite type name must not be blank");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    @Override
    public EntityType entityType() {
        return EntityType.COMPOSITE_TYPE;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Creates a new builder for {@link CompositeTypeMetadata}.
     *
     * @param name the composite type name
     * @return a new Builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Fluent builder for {@link CompositeTypeMetadata}.
     */
    public static final class Builder {
        private final String name;
        private final List<FieldMetadata> fields = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Composite type name must not be null");
        }

        public Builder addField(FieldMetadata field) {
            this.fields.add(Objects.requireNonNull(field, "Field must not be null"));
            return this;
        }

        public Builder fields(List<FieldMetadata> fields) {
            this.fields.clear();
            if (fields != null) {
                this.fields.addAll(fields);
            }
            return this;
        }

        public CompositeTypeMetadata build() {
            return new CompositeTypeMetadata(name, fields);
        }
    }
}
