package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds parameterized {@link BoundSql} for UPDATE operations.\n * Enforces the architectural read-only invariant by rejecting any view targets.
 */
public final class UpdateBuilder {

    private final SqlDialect dialect;
    private SourceTable.PhysicalTable table;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private WherePredicate where;
    private final List<String> returningColumns = new ArrayList<>();
    private boolean returningAll = false;

    public UpdateBuilder(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
    }

    public static UpdateBuilder of(SqlDialect dialect) {
        return new UpdateBuilder(dialect);
    }

    /**
     * Factory method creating an {@link UpdateBuilder} initialized from entity metadata.
     *
     * @param dialect the dialect to use
     * @param entity  the entity metadata
     * @return a new UpdateBuilder instance
     * @throws ReadOnlyViewException if the entity is marked read-only
     */
    public static UpdateBuilder forEntity(SqlDialect dialect, EntityMetadata entity) {
        return of(dialect).forEntity(entity);
    }

    /**
     * Initializes the target table from entity metadata, throwing {@link ReadOnlyViewException}
     * if the entity is a read-only view.
     *
     * @param entity the entity metadata
     * @return this builder
     * @throws ReadOnlyViewException if the entity is marked read-only
     */
    public UpdateBuilder forEntity(EntityMetadata entity) {
        Objects.requireNonNull(entity, "Entity metadata must not be null");
        QueryGuards.assertWritable(entity, "UPDATE");
        String tableName = entity instanceof ModelMetadata model ? model.tableName() : entity.name().toLowerCase();
        this.table = SourceTable.table(tableName);
        return this;
    }

    public UpdateBuilder table(String tableName) {
        Objects.requireNonNull(tableName, "Table name must not be null");
        this.table = SourceTable.table(tableName);
        return this;
    }

    public UpdateBuilder table(SourceTable.PhysicalTable table) {
        this.table = Objects.requireNonNull(table, "Table must not be null");
        return this;
    }

    public UpdateBuilder table(SourceTable source) {
        Objects.requireNonNull(source, "Source must not be null");
        QueryGuards.assertNotView(source, "UPDATE");
        if (source instanceof SourceTable.PhysicalTable physical) {
            this.table = physical;
        } else {
            this.table = SourceTable.table(source.identifier());
        }
        return this;
    }

    public UpdateBuilder set(String column, Object value) {
        Objects.requireNonNull(column, "Column must not be null");
        this.values.put(column, value);
        return this;
    }

    public UpdateBuilder values(Map<String, Object> values) {
        if (values != null) {
            this.values.putAll(values);
        }
        return this;
    }

    public UpdateBuilder where(WherePredicate where) {
        this.where = where;
        return this;
    }

    public UpdateBuilder returningAll() {
        this.returningAll = true;
        this.returningColumns.clear();
        return this;
    }

    public UpdateBuilder returning(String... columns) {
        if (columns != null) {
            this.returningAll = false;
            for (String col : columns) {
                if (col.equals("*")) {
                    this.returningAll = true;
                    this.returningColumns.clear();
                    break;
                }
                this.returningColumns.add(col);
            }
        }
        return this;
    }

    public UpdateBuilder returning(List<String> columns) {
        if (columns != null) {
            this.returningAll = false;
            this.returningColumns.clear();
            for (String col : columns) {
                if (col.equals("*")) {
                    this.returningAll = true;
                    this.returningColumns.clear();
                    break;
                }
                this.returningColumns.add(col);
            }
        }
        return this;
    }

    /**
     * Builds {@link BoundSql} from an existing {@link UpdateQuery} AST.
     *
     * @param query the update query AST
     * @return the parameterized BoundSql
     */
    public BoundSql build(UpdateQuery query) {
        Objects.requireNonNull(query, "UpdateQuery must not be null");
        this.table = query.table();
        this.values.clear();
        this.values.putAll(query.values());
        this.where = query.where();
        return build();
    }

    /**
     * Builds the parameterized {@link BoundSql}.
     *
     * @return the parameterized BoundSql
     */
    public BoundSql build() {
        if (table == null) {
            throw new IllegalStateException("Target table must be specified for UPDATE");
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("At least one column value must be specified for UPDATE");
        }

        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(dialect.quoteIdentifier(table.tableName()));
        sql.append(" SET ");

        List<Object> parameters = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(dialect.quoteIdentifier(entry.getKey())).append(" = ?");
            parameters.add(entry.getValue());
            i++;
        }

        if (where != null) {
            BoundSql whereSql = WhereClauseBuilder.buildWhereClause(where, dialect);
            sql.append(whereSql.sql());
            parameters.addAll(whereSql.parameters());
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
