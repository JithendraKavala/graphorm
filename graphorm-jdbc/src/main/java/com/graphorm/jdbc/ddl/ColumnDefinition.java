package com.graphorm.jdbc.ddl;

import com.graphorm.core.metadata.DefaultValue;
import java.util.Objects;

/**
 * Definition of a database column for DDL generation and diffing.
 *
 * @param name                   physical column name
 * @param type                   SQL type name (e.g. "INTEGER", "VARCHAR(255)", "TIMESTAMPTZ", "JSONB")
 * @param isNullable             whether this column allows NULL values
 * @param isPrimaryKey           whether this column is part of the primary key
 * @param isUnique               whether this column has an inline unique constraint
 * @param isGenerated            whether this column is auto-generated (SERIAL / IDENTITY)
 * @param defaultValue           default SQL expression literal (e.g. "CURRENT_TIMESTAMP", "'active'", "0")
 * @param defaultValueDefinition semantic typed representation of the default value
 */
public record ColumnDefinition(
        String name,
        String type,
        boolean isNullable,
        boolean isPrimaryKey,
        boolean isUnique,
        boolean isGenerated,
        String defaultValue,
        DefaultValue defaultValueDefinition
) {

    public ColumnDefinition {
        Objects.requireNonNull(name, "Column name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Column name must not be blank");
        }
        Objects.requireNonNull(type, "Column type must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("Column type must not be blank");
        }
        if (isGenerated) {
            defaultValue = null;
            defaultValueDefinition = null;
        } else {
            if (defaultValueDefinition == null && defaultValue != null && !defaultValue.isBlank()) {
                defaultValueDefinition = DefaultValue.from(defaultValue, null, type);
            }
            if (defaultValue == null && defaultValueDefinition != null) {
                defaultValue = defaultValueDefinition.asString();
            }
        }
    }

    /**
     * Backward-compatible 7-parameter constructor.
     */
    public ColumnDefinition(
            String name,
            String type,
            boolean isNullable,
            boolean isPrimaryKey,
            boolean isUnique,
            boolean isGenerated,
            String defaultValue
    ) {
        this(name, type, isNullable, isPrimaryKey, isUnique, isGenerated, defaultValue, null);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String type = "VARCHAR(255)";
        private boolean isNullable = true;
        private boolean isPrimaryKey = false;
        private boolean isUnique = false;
        private boolean isGenerated = false;
        private String defaultValue;
        private DefaultValue defaultValueDefinition;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Column name must not be null");
        }

        public Builder type(String type) {
            this.type = Objects.requireNonNull(type, "Column type must not be null");
            return this;
        }

        public Builder nullable(boolean nullable) {
            this.isNullable = nullable;
            return this;
        }

        public Builder primaryKey(boolean primaryKey) {
            this.isPrimaryKey = primaryKey;
            return this;
        }

        public Builder unique(boolean unique) {
            this.isUnique = unique;
            return this;
        }

        public Builder generated(boolean generated) {
            this.isGenerated = generated;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder defaultValue(DefaultValue defaultValueDefinition) {
            return defaultValueDefinition(defaultValueDefinition);
        }

        public Builder defaultValueDefinition(DefaultValue defaultValueDefinition) {
            this.defaultValueDefinition = defaultValueDefinition;
            return this;
        }

        public ColumnDefinition build() {
            if (isGenerated) {
                return new ColumnDefinition(name, type, isNullable, isPrimaryKey, isUnique, true, null, null);
            }
            DefaultValue def = defaultValueDefinition;
            if (def == null && defaultValue != null && !defaultValue.isBlank()) {
                def = DefaultValue.from(defaultValue, null, type);
            }
            String rawDefault = defaultValue;
            if (rawDefault == null && def != null) {
                rawDefault = def.asString();
            }
            return new ColumnDefinition(name, type, isNullable, isPrimaryKey, isUnique, isGenerated, rawDefault, def);
        }
    }
}
