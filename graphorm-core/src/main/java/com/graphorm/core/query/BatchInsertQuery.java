package com.graphorm.core.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Strongly typed, immutable AST representation of a multi-row batch SQL INSERT query:
 * {@code INSERT INTO <table> (<col1>, <col2>) VALUES (?, ?), (?, ?), ...}
 * Can only target regular physical models (never view models).
 *
 * @param table            the physical target table
 * @param columns          the column names to insert
 * @param rows             the list of rows, each containing values matching columns in order
 * @param returningColumns optional returning columns (e.g. RETURNING * or RETURNING id)
 */
public record BatchInsertQuery(
        SourceTable.PhysicalTable table,
        List<String> columns,
        List<List<Object>> rows,
        List<String> returningColumns
) implements SqlQuery {

    public BatchInsertQuery {
        Objects.requireNonNull(table, "Target table must not be null");
        Objects.requireNonNull(columns, "Columns must not be null");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Batch insert must specify at least one column");
        }
        Objects.requireNonNull(rows, "Rows must not be null");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Batch insert must specify at least one row of values");
        }
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            if (row == null || row.size() != columns.size()) {
                throw new IllegalArgumentException("Row " + r + " value count (" + (row == null ? 0 : row.size())
                        + ") must match column count (" + columns.size() + ")");
            }
        }
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
        returningColumns = returningColumns == null ? List.of() : List.copyOf(returningColumns);
    }

    @Override
    public QueryType queryType() {
        return QueryType.BATCH_INSERT;
    }

    public int rowCount() {
        return rows.size();
    }

    public boolean hasReturning() {
        return !returningColumns.isEmpty();
    }

    public List<String> returning() {
        return returningColumns();
    }

    public Builder toBuilder() {
        Builder b = new Builder(table)
                .columns(columns)
                .addRows(rows);
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
     * Fluent builder for {@link BatchInsertQuery}.
     */
    public static final class Builder {
        private final SourceTable.PhysicalTable table;
        private final List<String> columns = new ArrayList<>();
        private final List<List<Object>> rows = new ArrayList<>();
        private final List<String> returningColumns = new ArrayList<>();

        private Builder(SourceTable.PhysicalTable table) {
            this.table = Objects.requireNonNull(table, "Target table must not be null");
        }

        public Builder columns(String... columns) {
            if (columns != null) {
                for (String col : columns) {
                    if (col != null && !col.isBlank()) {
                        this.columns.add(col.trim());
                    }
                }
            }
            return this;
        }

        public Builder columns(List<String> columns) {
            if (columns != null) {
                this.columns.addAll(columns);
            }
            return this;
        }

        public Builder addRow(Object... values) {
            if (values != null) {
                List<Object> row = new ArrayList<>();
                Collections.addAll(row, values);
                this.rows.add(row);
            }
            return this;
        }

        public Builder addRow(List<Object> row) {
            if (row != null) {
                this.rows.add(new ArrayList<>(row));
            }
            return this;
        }

        public Builder addRows(List<List<Object>> rows) {
            if (rows != null) {
                for (List<Object> row : rows) {
                    addRow(row);
                }
            }
            return this;
        }

        /**
         * Adds rows from a list of column-to-value maps.
         * If columns are not yet defined, infers columns from the union of map keys.
         *
         * @param mapRows list of row maps
         * @return this builder
         */
        public Builder fromMaps(List<Map<String, Object>> mapRows) {
            if (mapRows == null || mapRows.isEmpty()) {
                return this;
            }

            if (this.columns.isEmpty()) {
                Set<String> allKeys = new LinkedHashSet<>();
                for (Map<String, Object> map : mapRows) {
                    if (map != null) {
                        allKeys.addAll(map.keySet());
                    }
                }
                this.columns.addAll(allKeys);
            }

            for (Map<String, Object> map : mapRows) {
                List<Object> row = new ArrayList<>();
                for (String col : this.columns) {
                    row.add(map != null ? map.get(col) : null);
                }
                this.rows.add(row);
            }
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

        public BatchInsertQuery build() {
            return new BatchInsertQuery(table, columns, rows, returningColumns);
        }
    }
}
