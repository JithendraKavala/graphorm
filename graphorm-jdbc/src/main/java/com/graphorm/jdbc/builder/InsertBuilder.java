package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds parameterized {@link BoundSql} for INSERT operations.
 * Enforces the architectural read-only invariant by rejecting any view targets.
 */
public final class InsertBuilder {

    private final SqlDialect dialect;
    private SourceTable.PhysicalTable table;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> returningColumns = new ArrayList<>();
    private boolean returningAll = false;

    public InsertBuilder(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
    }

    public static InsertBuilder of(SqlDialect dialect) {
        return new InsertBuilder(dialect);
    }

    /**
     * Initializes the target table from entity metadata, throwing {@link ReadOnlyViewException}
     * if the entity is a read-only view.
     *
     * @param entity the entity metadata
     * @return this builder
     * @throws ReadOnlyViewException if the entity is marked read-only
     */
    public InsertBuilder forEntity(EntityMetadata entity) {
        Objects.requireNonNull(entity, "Entity metadata must not be null");
        QueryGuards.assertWritable(entity, "INSERT");
        String tableName = entity instanceof ModelMetadata model ? model.tableName() : entity.name().toLowerCase();
        this.table = SourceTable.table(tableName);
        return this;
    }

    public InsertBuilder into(String tableName) {
        Objects.requireNonNull(tableName, "Table name must not be null");
        this.table = SourceTable.table(tableName);
        return this;
    }

    public InsertBuilder into(SourceTable.PhysicalTable table) {
        this.table = Objects.requireNonNull(table, "Table must not be null");
        return this;
    }

    public InsertBuilder into(SourceTable source) {
        Objects.requireNonNull(source, "Source must not be null");
        QueryGuards.assertNotView(source, "INSERT");
        if (source instanceof SourceTable.PhysicalTable physical) {
            this.table = physical;
        } else {
            this.table = SourceTable.table(source.identifier());
        }
        return this;
    }

    public InsertBuilder value(String column, Object value) {
        Objects.requireNonNull(column, "Column must not be null");
        this.values.put(column, value);
        return this;
    }

    public InsertBuilder values(Map<String, Object> values) {
        if (values != null) {
            this.values.putAll(values);
        }
        return this;
    }

    public InsertBuilder returningAll() {
        this.returningAll = true;
        this.returningColumns.clear();
        return this;
    }

    public InsertBuilder returning(String... columns) {
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

    public InsertBuilder returning(List<String> columns) {
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
     * Builds {@link BoundSql} from an existing {@link InsertQuery} AST.
     *
     * @param query the insert query AST
     * @return the parameterized BoundSql
     */
    public BoundSql build(InsertQuery query) {
        Objects.requireNonNull(query, "InsertQuery must not be null");
        this.table = query.table();
        this.values.clear();
        this.values.putAll(query.values());
        return build();
    }

    /**
     * Builds the parameterized {@link BoundSql}.
     *
     * @return the parameterized BoundSql
     */
    public BoundSql build() {
        if (table == null) {
            throw new IllegalStateException("Target table must be specified for INSERT");
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("At least one column value must be specified for INSERT");
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
