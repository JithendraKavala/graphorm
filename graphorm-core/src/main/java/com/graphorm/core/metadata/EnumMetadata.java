package com.graphorm.core.metadata;

import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata record defining a schema enumeration and its allowable values.
 *
 * @param enumName the logical name of the enum
 * @param values   the immutable list of declared enum values
 */
public record EnumMetadata(
        String enumName,
        List<String> values
) {

    /**
     * Compact constructor enforcing immutability and argument validation.
     */
    public EnumMetadata {
        Objects.requireNonNull(enumName, "Enum name must not be null");
        if (enumName.isBlank()) {
            throw new IllegalArgumentException("Enum name must not be blank");
        }
        Objects.requireNonNull(values, "Enum values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Enum values must not be empty");
        }
        values = List.copyOf(values);
    }

    /**
     * Checks if this enum contains the specified string value.
     *
     * @param value value to verify
     * @return true if the value exists in this enum
     */
    public boolean hasValue(String value) {
        if (value == null) {
            return false;
        }
        return values.contains(value);
    }

    /**
     * Creates a new builder for {@link EnumMetadata}.
     *
     * @param enumName the enum name
     * @return a new Builder instance
     */
    public static Builder builder(String enumName) {
        return new Builder(enumName);
    }

    /**
     * Fluent builder for {@link EnumMetadata}.
     */
    public static final class Builder {
        private final String enumName;
        private List<String> values = List.of();

        private Builder(String enumName) {
            this.enumName = Objects.requireNonNull(enumName, "Enum name must not be null");
        }

        public Builder values(List<String> values) {
            this.values = values != null ? List.copyOf(values) : List.of();
            return this;
        }

        public Builder values(String... values) {
            this.values = values != null ? List.of(values) : List.of();
            return this;
        }

        public EnumMetadata build() {
            return new EnumMetadata(enumName, values);
        }
    }
}
