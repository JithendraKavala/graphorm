package com.graphorm.core.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly typed, immutable AST representation of an SQL UPSERT query
 * (INSERT ... ON CONFLICT (...) DO UPDATE / DO NOTHING).
 * Can only target regular physical models (never view models).
 *
 * @param table            the physical target table
 * @param values           the column-to-value map for insertion
 * @param conflictColumns  the unique/primary key column(s) defining the conflict target
 * @param conflictAction   the action to take on conflict (DO_NOTHING or DO_UPDATE)
 * @param updateValues     the column-to-value map to update on conflict (when conflictAction is DO_UPDATE)
 * @param updateWhere      optional WHERE clause filtering which rows are updated on conflict
 * @param returningColumns optional columns to return (e.g. RETURNING * or RETURNING id)
 */
public record UpsertQuery(
        SourceTable.PhysicalTable table,
        Map<String, Object> values,
        List<String> conflictColumns,
        ConflictAction conflictAction,
        Map<String, Object> updateValues,
        WherePredicate updateWhere,
        List<String> returningColumns
) implements SqlQuery {

    public UpsertQuery {
        Objects.requireNonNull(table, "Target table must not be null");
        Objects.requireNonNull(values, "Insert values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Upsert query must specify at least one column value");
        }
        Objects.requireNonNull(conflictAction, "ConflictAction must not be null");
        conflictColumns = conflictColumns == null ? List.of() : List.copyOf(conflictColumns);
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        updateValues = updateValues == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(updateValues));
        returningColumns = returningColumns == null ? List.of() : List.copyOf(returningColumns);
    }

    @Override
    public QueryType queryType() {
        return QueryType.UPSERT;
    }

    public boolean isDoNothing() {
        return conflictAction == ConflictAction.DO_NOTHING;
    }

    public boolean isDoUpdate() {
        return conflictAction == ConflictAction.DO_UPDATE;
    }

    public boolean hasReturning() {
        return !returningColumns.isEmpty();
    }

    public List<String> returning() {
        return returningColumns();
    }

    public Builder toBuilder() {
        Builder b = new Builder(table)
                .values(values)
                .onConflict(conflictColumns);
        if (conflictAction == ConflictAction.DO_UPDATE) {
            b.doUpdate(updateValues);
            if (updateWhere != null) {
                b.doUpdateWhere(updateWhere);
            }
        } else {
            b.doNothing();
        }
        b.returning(returningColumns);
        return b;
    }

    public static Builder into(String tableName) {
        return new Builder(SourceTable.table(tableName));
    }

    public static Builder into(SourceTable.PhysicalTable table) {
        return new Builder(table);
    }

    /**
     * Fluent builder for {@link UpsertQuery}.
     */
    public static final class Builder {
        private final SourceTable.PhysicalTable table;
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final List<String> conflictColumns = new ArrayList<>();
        private ConflictAction conflictAction = ConflictAction.DO_NOTHING;
        private final Map<String, Object> updateValues = new LinkedHashMap<>();
        private WherePredicate updateWhere;
        private final List<String> returningColumns = new ArrayList<>();

        private Builder(SourceTable.PhysicalTable table) {
            this.table = Objects.requireNonNull(table, "Target table must not be null");
        }

        public Builder value(String column, Object value) {
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

        public Builder onConflict(String... columns) {
            if (columns != null) {
                for (String col : columns) {
                    if (col != null && !col.isBlank()) {
                        this.conflictColumns.add(col.trim());
                    }
                }
            }
            return this;
        }

        public Builder onConflict(List<String> columns) {
            if (columns != null) {
                this.conflictColumns.addAll(columns);
            }
            return this;
        }

        public Builder doNothing() {
            this.conflictAction = ConflictAction.DO_NOTHING;
            this.updateValues.clear();
            return this;
        }

        public Builder doUpdate() {
            this.conflictAction = ConflictAction.DO_UPDATE;
            return this;
        }

        public Builder doUpdateSet(String column, Object value) {
            this.conflictAction = ConflictAction.DO_UPDATE;
            this.updateValues.put(column, value);
            return this;
        }

        public Builder doUpdate(Map<String, Object> updateValues) {
            this.conflictAction = ConflictAction.DO_UPDATE;
            if (updateValues != null) {
                this.updateValues.putAll(updateValues);
            }
            return this;
        }

        public Builder doUpdateWhere(WherePredicate predicate) {
            this.updateWhere = predicate;
            return this;
        }

        public Builder returningAll() {
            this.returningColumns.clear();
            this.returningColumns.add("*");
            return this;
        }

        public Builder returning(String... columns) {
            this.returningColumns.clear();
            if (columns != null) {
                for (String col : columns) {
                    if (col != null && !col.isBlank()) {
                        this.returningColumns.add(col.trim());
                    }
                }
            }
            return this;
        }

        public Builder returning(List<String> columns) {
            this.returningColumns.clear();
            if (columns != null) {
                this.returningColumns.addAll(columns);
            }
            return this;
        }

        public UpsertQuery build() {
            return new UpsertQuery(
                    table,
                    values,
                    conflictColumns,
                    conflictAction,
                    updateValues,
                    updateWhere,
                    returningColumns
            );
        }
    }
}
