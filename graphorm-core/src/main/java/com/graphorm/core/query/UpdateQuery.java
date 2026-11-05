package com.graphorm.core.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly typed, immutable AST representation of an SQL UPDATE query.
 * Can only target regular physical models (never view models).
 *
 * @param table  the physical target table
 * @param values the column-to-value map of updates
 * @param where  the WHERE filter condition (optional)
 */
public record UpdateQuery(
        SourceTable.PhysicalTable table,
        Map<String, Object> values,
        WherePredicate where
) implements SqlQuery {

    public UpdateQuery {
        Objects.requireNonNull(table, "Target table must not be null");
        Objects.requireNonNull(values, "Update values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Update query must specify at least one column value");
        }
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override
    public QueryType queryType() {
        return QueryType.UPDATE;
    }

    /**
     * Creates a builder for an UPDATE query on the specified table.
     *
     * @param tableName the target physical table name
     * @return a new Builder instance
     */
    public static Builder table(String tableName) {
        return new Builder(SourceTable.table(tableName));
    }

    /**
     * Creates a builder for an UPDATE query on the specified physical table.
     *
     * @param table the physical table
     * @return a new Builder instance
     */
    public static Builder table(SourceTable.PhysicalTable table) {
        return new Builder(table);
    }

    /**
     * Fluent builder for {@link UpdateQuery}.
     */
    public static final class Builder {
        private final SourceTable.PhysicalTable table;
        private final Map<String, Object> values = new LinkedHashMap<>();
        private WherePredicate where;

        private Builder(SourceTable.PhysicalTable table) {
            this.table = Objects.requireNonNull(table, "Target table must not be null");
        }

        public Builder set(String column, Object value) {
            Objects.requireNonNull(column, "Column name must not be null");
            this.values.put(column, value);
            return this;
        }

        public Builder values(Map<String, Object> values) {
            if (values != null) {
                this.values.putAll(values);
            }
            return this;
        }

        public Builder where(WherePredicate where) {
            this.where = where;
            return this;
        }

        public UpdateQuery build() {
            return new UpdateQuery(table, values, where);
        }
    }
}
