package com.graphorm.core.query;

import java.util.Objects;

/**
 * Strongly typed, immutable AST representation of an SQL DELETE query.
 * Can only target regular physical models (never view models).
 *
 * @param table the physical target table
 * @param where the WHERE filter condition (optional)
 */
public record DeleteQuery(
        SourceTable.PhysicalTable table,
        WherePredicate where
) implements SqlQuery {

    public DeleteQuery {
        Objects.requireNonNull(table, "Target table must not be null");
    }

    @Override
    public QueryType queryType() {
        return QueryType.DELETE;
    }

    /**
     * Creates a builder for a DELETE query from the specified table.\n     *\n     * @param tableName the target physical table name\n     * @return a new Builder instance\n     */
    public static Builder from(String tableName) {
        return new Builder(SourceTable.table(tableName));
    }

    /**
     * Creates a builder for a DELETE query from the specified physical table.
     *
     * @param table the physical table
     * @return a new Builder instance
     */
    public static Builder from(SourceTable.PhysicalTable table) {
        return new Builder(table);
    }

    /**
     * Fluent builder for {@link DeleteQuery}.
     */
    public static final class Builder {
        private final SourceTable.PhysicalTable table;
        private WherePredicate where;

        private Builder(SourceTable.PhysicalTable table) {
            this.table = Objects.requireNonNull(table, "Target table must not be null");
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

        public Builder orWhere(WherePredicate predicate) {
            Objects.requireNonNull(predicate, "Predicate must not be null");
            if (this.where == null) {
                this.where = predicate;
            } else {
                this.where = this.where.or(predicate);
            }
            return this;
        }

        public DeleteQuery build() {
            return new DeleteQuery(table, where);
        }
    }
}
