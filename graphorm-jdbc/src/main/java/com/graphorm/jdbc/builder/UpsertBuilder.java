package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.ConflictAction;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds parameterized {@link BoundSql} for PostgreSQL UPSERT operations
 * ({@code INSERT ... ON CONFLICT (...) DO UPDATE SET ... / DO NOTHING}).
 * Strictly enforces read-only invariants by rejecting view targets.
 */
public final class UpsertBuilder {

    private final SqlDialect dialect;
    private SourceTable.PhysicalTable table;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> conflictColumns = new ArrayList<>();
    private ConflictAction conflictAction = ConflictAction.DO_NOTHING;
    private final Map<String, Object> updateValues = new LinkedHashMap<>();
    private WherePredicate updateWhere;
    private final List<String> returningColumns = new ArrayList<>();
    private boolean returningAll = false;

    public UpsertBuilder(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
    }

    public static UpsertBuilder of(SqlDialect dialect) {
        return new UpsertBuilder(dialect);
    }

    public static UpsertBuilder forEntity(SqlDialect dialect, EntityMetadata entity) {
        return of(dialect).forEntity(entity);
    }

    public UpsertBuilder forEntity(EntityMetadata entity) {
        Objects.requireNonNull(entity, "Entity metadata must not be null");
        QueryGuards.assertWritable(entity, "UPSERT");
        String tableName = entity instanceof ModelMetadata model ? model.tableName() : entity.name().toLowerCase();
        this.table = SourceTable.table(tableName);
        return this;
    }

    public UpsertBuilder into(String tableName) {
        Objects.requireNonNull(tableName, "Table name must not be null");
        this.table = SourceTable.table(tableName);
        return this;
    }

    public UpsertBuilder into(SourceTable.PhysicalTable table) {
        this.table = Objects.requireNonNull(table, "Table must not be null");
        return this;
    }

    public UpsertBuilder value(String column, Object value) {
        Objects.requireNonNull(column, "Column name must not be null");
        this.values.put(column, value);
        return this;
    }

    public UpsertBuilder values(Map<String, Object> values) {
        if (values != null) {
            this.values.putAll(values);
        }
        return this;
    }

    public UpsertBuilder onConflict(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.isBlank()) {
                    this.conflictColumns.add(col.trim());
                }
            }
        }
        return this;
    }

    public UpsertBuilder onConflict(List<String> columns) {
        if (columns != null) {
            this.conflictColumns.addAll(columns);
        }
        return this;
    }

    public UpsertBuilder doNothing() {
        this.conflictAction = ConflictAction.DO_NOTHING;
        this.updateValues.clear();
        return this;
    }

    public UpsertBuilder doUpdate() {
        this.conflictAction = ConflictAction.DO_UPDATE;
        return this;
    }

    public UpsertBuilder doUpdateSet(String column, Object value) {
        this.conflictAction = ConflictAction.DO_UPDATE;
        this.updateValues.put(column, value);
        return this;
    }

    public UpsertBuilder doUpdate(Map<String, Object> updateValues) {
        this.conflictAction = ConflictAction.DO_UPDATE;
        if (updateValues != null) {
            this.updateValues.putAll(updateValues);
        }
        return this;
    }

    public UpsertBuilder doUpdateWhere(WherePredicate predicate) {
        this.updateWhere = predicate;
        return this;
    }

    public UpsertBuilder returningAll() {
        this.returningAll = true;
        this.returningColumns.clear();
        return this;
    }

    public UpsertBuilder returning(String... columns) {
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

    public UpsertBuilder returning(List<String> columns) {
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

    public BoundSql build(UpsertQuery query) {
        Objects.requireNonNull(query, "UpsertQuery must not be null");
        this.table = query.table();
        this.values.clear();
        this.values.putAll(query.values());
        this.conflictColumns.clear();
        this.conflictColumns.addAll(query.conflictColumns());
        this.conflictAction = query.conflictAction();
        this.updateValues.clear();
        this.updateValues.putAll(query.updateValues());
        this.updateWhere = query.updateWhere();
        this.returningColumns.clear();
        this.returningColumns.addAll(query.returningColumns());
        this.returningAll = query.returningColumns().contains("*");
        return build();
    }

    public BoundSql build() {
        if (table == null) {
            throw new IllegalStateException("Target table must be specified for UPSERT");
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("At least one column value must be specified for UPSERT");
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(dialect.quoteIdentifier(table.tableName()));

        StringBuilder colsPart = new StringBuilder(" (");
        StringBuilder valsPart = new StringBuilder(" VALUES (");
        List<Object> parameters = new ArrayList<>();

        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (i > 0) {
                colsPart.append(", ");
                valsPart.append(", ");
            }
            colsPart.append(dialect.quoteIdentifier(entry.getKey()));
            valsPart.append("?");
            parameters.add(entry.getValue());
            i++;
        }
        colsPart.append(")");
        valsPart.append(")");

        sql.append(colsPart).append(valsPart);

        // ON CONFLICT clause
        sql.append(" ON CONFLICT");
        if (!conflictColumns.isEmpty()) {
            sql.append(" (");
            for (int c = 0; c < conflictColumns.size(); c++) {
                if (c > 0) sql.append(", ");
                sql.append(dialect.quoteIdentifier(conflictColumns.get(c)));
            }
            sql.append(")");
        }

        if (conflictAction == ConflictAction.DO_NOTHING) {
            sql.append(" DO NOTHING");
        } else {
            sql.append(" DO UPDATE SET ");
            Map<String, Object> effectiveUpdates = new LinkedHashMap<>(updateValues);

            // If no explicit update columns were specified, update all non-conflict columns using EXCLUDED
            if (effectiveUpdates.isEmpty()) {
                int u = 0;
                for (String col : values.keySet()) {
                    if (conflictColumns.contains(col)) {
                        continue;
                    }
                    if (u > 0) sql.append(", ");
                    String quotedCol = dialect.quoteIdentifier(col);
                    sql.append(quotedCol).append(" = EXCLUDED.").append(quotedCol);
                    u++;
                }
            } else {
                int u = 0;
                for (Map.Entry<String, Object> entry : effectiveUpdates.entrySet()) {
                    if (u > 0) sql.append(", ");
                    String col = entry.getKey();
                    Object val = entry.getValue();
                    String quotedCol = dialect.quoteIdentifier(col);

                    if (val instanceof String str && str.toUpperCase().startsWith("EXCLUDED.")) {
                        sql.append(quotedCol).append(" = ").append(val);
                    } else {
                        sql.append(quotedCol).append(" = ?");
                        parameters.add(val);
                    }
                    u++;
                }
            }

            if (updateWhere != null) {
                BoundSql whereSql = WhereClauseBuilder.buildWhereClause(updateWhere, dialect);
                sql.append(whereSql.sql());
                parameters.addAll(whereSql.parameters());
            }
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
