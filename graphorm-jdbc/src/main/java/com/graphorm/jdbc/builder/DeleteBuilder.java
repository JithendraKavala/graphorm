package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds parameterized {@link BoundSql} for DELETE operations.
 * Enforces the architectural read-only invariant by rejecting any view targets,
 * and transparently coordinates optimistic locking via version column tracking.
 */
public final class DeleteBuilder {

    private final SqlDialect dialect;
    private EntityMetadata entity;
    private SourceTable.PhysicalTable table;
    private WherePredicate where;
    private final List<String> returningColumns = new ArrayList<>();
    private boolean returningAll = false;

    // Optimistic locking fields
    private String entityName;
    private String versionColumn;
    private Object expectedVersion;
    private boolean optimisticLocking = false;
    private String idColumn = "id";
    private Object idValue;

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
        this.entity = entity;
        if (entity.isMultiTenant()) {
            QueryGuards.requireTenantContext(entity, "DELETE");
        }
        this.entityName = entity.name();
        if (entity instanceof ModelMetadata model) {
            this.table = SourceTable.table(model.tableName());
            if (model.hasVersionField()) {
                FieldMetadata vf = model.versionField().get();
                this.versionColumn = vf.dbColumnName();
            }
            if (!model.primaryKeyFields().isEmpty()) {
                String pk = model.primaryKeyFields().getFirst();
                this.idColumn = model.findField(pk).map(FieldMetadata::dbColumnName).orElse(pk);
            }
        } else {
            this.table = SourceTable.table(entity.name().toLowerCase());
        }
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

    public DeleteBuilder id(Object idValue) {
        this.idValue = idValue;
        return this;
    }

    public DeleteBuilder id(String idColumn, Object idValue) {
        this.idColumn = Objects.requireNonNull(idColumn, "idColumn must not be null");
        this.idValue = idValue;
        return this;
    }

    public DeleteBuilder versionColumn(String versionColumn) {
        this.versionColumn = versionColumn;
        return this;
    }

    public DeleteBuilder expectedVersion(Object expectedVersion) {
        this.expectedVersion = expectedVersion;
        this.optimisticLocking = true;
        return this;
    }

    /**
     * Enables optimistic locking concurrency control with the given version column and expected version.
     *
     * @param versionColumn   the name of the version column in the database table
     * @param expectedVersion the expected version value prior to delete
     * @return this builder
     */
    public DeleteBuilder optimisticLock(String versionColumn, Object expectedVersion) {
        this.versionColumn = Objects.requireNonNull(versionColumn, "versionColumn must not be null");
        this.expectedVersion = Objects.requireNonNull(expectedVersion, "expectedVersion must not be null");
        this.optimisticLocking = true;
        return this;
    }

    public boolean isOptimisticLockingEnabled() {
        return optimisticLocking;
    }

    public String getVersionColumn() {
        return versionColumn;
    }

    public Object getExpectedVersion() {
        return expectedVersion;
    }

    public String getTableName() {
        return table != null ? table.tableName() : entityName;
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
     * Automatically coordinates version predicates when optimistic locking is active.
     *
     * @return the parameterized BoundSql
     */
    public BoundSql build() {
        if (table == null) {
            throw new IllegalStateException("Target table must be specified for DELETE");
        }

        WherePredicate effectiveWhere = this.where;
        if (entity != null && entity.isMultiTenant()) {
            String tenantId = QueryGuards.requireTenantContext(entity, "DELETE");
            if (tenantId != null) {
                WherePredicate tenantPredicate = WherePredicate.equal(entity.tenantColumn(), tenantId);
                effectiveWhere = (effectiveWhere == null) ? tenantPredicate : tenantPredicate.and(effectiveWhere);
            }
        }

        if (optimisticLocking && versionColumn != null && expectedVersion != null) {
            WherePredicate verPredicate = WherePredicate.eq(versionColumn, expectedVersion);
            if (effectiveWhere == null) {
                if (idValue != null) {
                    effectiveWhere = WherePredicate.eq(idColumn, idValue).and(verPredicate);
                } else {
                    effectiveWhere = verPredicate;
                }
            } else {
                if (idValue != null) {
                    effectiveWhere = effectiveWhere.and(WherePredicate.eq(idColumn, idValue)).and(verPredicate);
                } else {
                    effectiveWhere = effectiveWhere.and(verPredicate);
                }
            }
        } else if (idValue != null) {
            WherePredicate idPredicate = WherePredicate.eq(idColumn, idValue);
            effectiveWhere = (effectiveWhere == null) ? idPredicate : effectiveWhere.and(idPredicate);
        }

        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(dialect.quoteIdentifier(table.tableName()));

        List<Object> parameters = new ArrayList<>();
        if (effectiveWhere != null) {
            BoundSql whereSql = WhereClauseBuilder.buildWhereClause(effectiveWhere, dialect);
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

    /**
     * Checks the affected row count against optimistic locking requirements.
     * Throws {@link OptimisticLockException} if optimistic locking is enabled and 0 rows were deleted.
     *
     * @param affectedRows the number of rows modified by the DELETE statement
     * @throws OptimisticLockException if 0 rows were deleted under optimistic locking
     */
    public void verifyOptimisticLock(int affectedRows) {
        if (optimisticLocking && affectedRows == 0) {
            throw new OptimisticLockException(
                    "Optimistic lock failure: record in '" + (entityName != null ? entityName : getTableName()) +
                            "' with identifier '" + idValue + "' was modified or deleted by another transaction (expected version: " +
                            expectedVersion + ")",
                    null,
                    entityName != null ? entityName : getTableName(),
                    idValue,
                    expectedVersion,
                    null
            );
        }
    }

    /**
     * Builds and executes this DELETE query using the supplied {@link JdbcExecutor},
     * automatically verifying optimistic locking concurrency control.
     *
     * @param executor the JdbcExecutor to use
     * @return the number of deleted rows
     * @throws OptimisticLockException if optimistic locking is enabled and 0 rows are affected
     */
    public int execute(JdbcExecutor executor) {
        Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        int affected = executor.executeUpdate(build());
        verifyOptimisticLock(affected);
        return affected;
    }
}
