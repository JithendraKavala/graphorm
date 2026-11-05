package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds parameterized {@link BoundSql} for DELETE operations.
 * Enforces the architectural read-only invariant by rejecting any view targets.
 */
public final class DeleteBuilder {

    private final SqlDialect dialect;
    private SourceTable.PhysicalTable table;
    private WherePredicate where;
    private final List<String> returningColumns = new ArrayList<>();
    private boolean returningAll = false;

    public DeleteBuilder(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
    }

    public static DeleteBuilder of(SqlDialect dialect) {
        return new DeleteBuilder(dialect);
    }

    /**
     * Factory method creating a {@link DeleteBuilder} initialized from entity metadata.
     *
     * @param dialect the dialect to use
     * @param entity  the entity metadata
     * @return a new DeleteBuilder instance
     * @throws ReadOnlyViewException if the entity is marked read-only
     */
    public static DeleteBuilder forEntity(SqlDialect dialect, EntityMetadata entity) {
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
    public DeleteBuilder forEntity(EntityMetadata entity) {
        Objects.requireNonNull(entity, "Entity metadata must not be null");
        QueryGuards.assertWritable(entity, "DELETE");
        String tableName = entity instanceof ModelMetadata model ? model.tableName() : entity.name().toLowerCase();
        this.table = SourceTable.table(tableName);
        return this;
    }

    public DeleteBuilder from(String tableName) {
        Objects.requireNonNull(tableName, "Table name must not be null");
        this.table = SourceTable.table(tableName);
        return this;
    }

    public DeleteBuilder from(SourceTable.PhysicalTable table) {
        this.table = Objects.requireNonNull(table, "Table must not be null");
        return this;
    }

    public DeleteBuilder from(SourceTable source) {
        Objects.requireNonNull(source, "Source must not be null");
        QueryGuards.assertNotView(source, "DELETE");
        if (source instanceof SourceTable.PhysicalTable physical) {
            this.table = physical;
        } else {
            this.table = SourceTable.table(source.identifier());
        }
        return this;
    }

    public DeleteBuilder where(WherePredicate where) {
        this.where = where;
        return this;
    }

    public DeleteBuilder returningAll() {
        this.returningAll = true;
        this.returningColumns.clear();
        return this;
    }

    public DeleteBuilder returning(String... columns) {
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

    public DeleteBuilder returning(List<String> columns) {
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

    /**
     * Builds {@link BoundSql} from an existing {@link DeleteQuery} AST.
     *
     * @param query the delete query AST
     * @return the parameterized BoundSql
     */
    public BoundSql build(DeleteQuery query) {
        Objects.requireNonNull(query, "DeleteQuery must not be null");
        this.table = query.table();
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
            throw new IllegalStateException("Target table must be specified for DELETE");
        }

        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(dialect.quoteIdentifier(table.tableName()));

        List<Object> parameters = new ArrayList<>();
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
