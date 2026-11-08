package com.graphorm.core.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly typed, immutable AST representation of a bulk SQL UPDATE query:
 * {@code UPDATE <table> SET <col1> = ?, <col2> = ? WHERE <condition>}
 * Can only target regular physical models (never view models).
 *
 * @param table  the physical target table
 * @param values the column-to-value map of updates
 * @param where  the WHERE filter condition determining which records are bulk updated
 */
public record BulkUpdateQuery(
        SourceTable.PhysicalTable table,
        Map<String, Object> values,
        WherePredicate where
) implements SqlQuery {

    public BulkUpdateQuery {
        Objects.requireNonNull(table, "Target table must not be null");
        Objects.requireNonNull(values, "Update values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Bulk update query must specify at least one column value");
        }
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override
    public QueryType queryType() {
        return QueryType.BULK_UPDATE;
    }

    public static Builder table(String tableName) {
        return new Builder(SourceTable.table(tableName));
    }

    public static Builder table(SourceTable.PhysicalTable table) {
        return new Builder(table);
    }

    /**
     * Fluent builder for {@link BulkUpdateQuery}.
     */
    public static final class Builder {
        private final SourceTable.PhysicalTable table;
        private final Map<String, Object> values = new LinkedHashMap<>();
        private WherePredicate where;

        private Builder(SourceTable.PhysicalTable table) {
            this.table = Objects.requireNonNull(table, "Target table must not be null");
        }

        public Builder value(String column, Object value) {
            return set(column, value);
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

        public Builder andWhere(WherePredicate predicate) {
            Objects.requireNonNull(predicate, "Predicate must not be null");
            if (this.where == null) {
                this.where = predicate;
            } else {
                this.where = this.where.and(predicate);
            }
            return this;
        }

        public BulkUpdateQuery build() {
            return new BulkUpdateQuery(table, values, where);
        }
    }
}
