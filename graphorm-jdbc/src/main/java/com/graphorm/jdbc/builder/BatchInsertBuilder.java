package com.graphorm.jdbc.builder;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds parameterized {@link BoundSql} for multi-row batch INSERT operations:
 * {@code INSERT INTO <table> (<cols>) VALUES (?, ?), (?, ?), ...}
 * Strictly enforces read-only invariants by rejecting view targets.
 */
public final class BatchInsertBuilder {

    private final SqlDialect dialect;
    private SourceTable.PhysicalTable table;
    private final List<String> columns = new ArrayList<>();
    private final List<List<Object>> rows = new ArrayList<>();
    private final List<String> returningColumns = new ArrayList<>();
    private boolean returningAll = false;

    public BatchInsertBuilder(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
    }

    public static BatchInsertBuilder of(SqlDialect dialect) {
        return new BatchInsertBuilder(dialect);
    }

    public static BatchInsertBuilder forEntity(SqlDialect dialect, EntityMetadata entity) {
        return of(dialect).forEntity(entity);
    }

    public BatchInsertBuilder forEntity(EntityMetadata entity) {
        Objects.requireNonNull(entity, "Entity metadata must not be null");
        QueryGuards.assertWritable(entity, "BATCH INSERT");
        String tableName = entity instanceof ModelMetadata model ? model.tableName() : entity.name().toLowerCase();
        this.table = SourceTable.table(tableName);
        return this;
    }

    public BatchInsertBuilder into(String tableName) {
        Objects.requireNonNull(tableName, "Table name must not be null");
        this.table = SourceTable.table(tableName);
        return this;
    }

    public BatchInsertBuilder into(SourceTable.PhysicalTable table) {
        this.table = Objects.requireNonNull(table, "Table must not be null");
        return this;
    }

    public BatchInsertBuilder columns(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.isBlank()) {
                    this.columns.add(col.trim());
                }
            }
        }
        return this;
    }

    public BatchInsertBuilder columns(List<String> columns) {
        if (columns != null) {
            this.columns.addAll(columns);
        }
        return this;
    }

    public BatchInsertBuilder addRow(Object... values) {
        if (values != null) {
            List<Object> row = new ArrayList<>();
            Collections.addAll(row, values);
            this.rows.add(row);
        }
        return this;
    }

    public BatchInsertBuilder addRow(List<Object> row) {
        if (row != null) {
            this.rows.add(new ArrayList<>(row));
        }
        return this;
    }

    public BatchInsertBuilder addRows(List<List<Object>> rows) {
        if (rows != null) {
            for (List<Object> row : rows) {
                addRow(row);
            }
        }
        return this;
    }

    public BatchInsertBuilder fromMaps(List<Map<String, Object>> mapRows) {
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

    public BatchInsertBuilder returningAll() {
        this.returningAll = true;
        this.returningColumns.clear();
        return this;
    }

    public BatchInsertBuilder returning(String... columns) {
        if (columns != null) {
            this.returningAll = false;
            for (String col : columns) {
                if ("*".equals(col)) {
                    this.returningAll = true;
                    this.returningColumns.clear();
                    break;
                }
                this.returningColumns.add(col);
            }
        }
        return this;
    }

    public BatchInsertBuilder returning(List<String> columns) {
        if (columns != null) {
            this.returningAll = false;
            this.returningColumns.clear();
            for (String col : columns) {
                if ("*".equals(col)) {
                    this.returningAll = true;
                    this.returningColumns.clear();
                    break;
                }
                this.returningColumns.add(col);
            }
        }
        return this;
    }

    public BoundSql build(BatchInsertQuery query) {
        Objects.requireNonNull(query, "BatchInsertQuery must not be null");
        this.table = query.table();
        this.columns.clear();
        this.columns.addAll(query.columns());
        this.rows.clear();
        this.rows.addAll(query.rows());
        this.returningColumns.clear();
        this.returningColumns.addAll(query.returningColumns());
        this.returningAll = query.returningColumns().contains("*");
        return build();
    }

    public BoundSql build() {
        if (table == null) {
            throw new IllegalStateException("Target table must be specified for batch INSERT");
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("At least one column must be specified for batch INSERT");
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("At least one row must be specified for batch INSERT");
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(dialect.quoteIdentifier(table.tableName()));

        // (col1, col2)
        sql.append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(dialect.quoteIdentifier(columns.get(i)));
        }
        sql.append(") VALUES ");

        List<Object> parameters = new ArrayList<>();

        // ( ?, ? ), ( ?, ? )
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) sql.append(", ");
            sql.append("(");
            List<Object> row = rows.get(r);
            for (int c = 0; c < columns.size(); c++) {
                if (c > 0) sql.append(", ");
                sql.append("?");
                parameters.add(c < row.size() ? row.get(c) : null);
            }
            sql.append(")");
        }

        if (dialect.supportsReturning()) {
            if (returningAll) {
                sql.append(" ").append(dialect.renderReturningClause(List.of("*")));
            } else if (!returningColumns.isEmpty()) {
                sql.append(" ").append(dialect.renderReturningClause(returningColumns));
            }
        }

        return new BoundSql(sql.toString(), parameters);
    }
}
