package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds parameterized {@link BoundSql} for UPDATE operations.
 * Enforces the architectural read-only invariant by rejecting any view targets,
 * and transparently coordinates optimistic locking via version column tracking.
 */
public final class UpdateBuilder {

    private final SqlDialect dialect;
    private SourceTable.PhysicalTable table;
    private final Map<String, Object> values = new LinkedHashMap<>();
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
     * Initializes the target table and optimistic locking metadata from entity metadata,
     * throwing {@link ReadOnlyViewException} if the entity is a read-only view.
     *
     * @param entity the entity metadata
     * @return this builder
     * @throws ReadOnlyViewException if the entity is marked read-only
     */
    public UpdateBuilder forEntity(EntityMetadata entity) {
        Objects.requireNonNull(entity, "Entity metadata must not be null");
        QueryGuards.assertWritable(entity, "UPDATE");
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

    public UpdateBuilder id(Object idValue) {
        this.idValue = idValue;
        return this;
    }

    public UpdateBuilder id(String idColumn, Object idValue) {
        this.idColumn = Objects.requireNonNull(idColumn, "idColumn must not be null");
        this.idValue = idValue;
        return this;
    }

    public UpdateBuilder versionColumn(String versionColumn) {
        this.versionColumn = versionColumn;
        return this;
    }

    public UpdateBuilder expectedVersion(Object expectedVersion) {
        this.expectedVersion = expectedVersion;
        this.optimisticLocking = true;
        return this;
    }

    public UpdateBuilder optimisticLock(String versionColumn, Object expectedVersion) {
        this.versionColumn = Objects.requireNonNull(versionColumn, "versionColumn must not be null");
        this.expectedVersion = expectedVersion;
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
     * Automatically coordinates version increment and WHERE predicates when optimistic locking is active.
     *
     * @return the parameterized BoundSql
     */
    public BoundSql build() {
        if (table == null) {
            throw new IllegalStateException("Target table must be specified for UPDATE");
        }

        // Auto-detect version field from values if set
        if (versionColumn != null && expectedVersion == null && values.containsKey(versionColumn)) {
            this.expectedVersion = values.get(versionColumn);
            this.optimisticLocking = true;
        }

        // Apply optimistic locking version increment and WHERE condition
        if (optimisticLocking && versionColumn != null && expectedVersion != null) {
            Object nextVersion = computeNextVersion(expectedVersion);
            values.put(versionColumn, nextVersion);

            WherePredicate verPredicate = WherePredicate.eq(versionColumn, expectedVersion);
            if (where == null) {
                if (idValue != null) {
                    where = WherePredicate.eq(idColumn, idValue).and(verPredicate);
                } else {
                    where = verPredicate;
                }
            } else {
                if (idValue != null) {
                    where = where.and(WherePredicate.eq(idColumn, idValue)).and(verPredicate);
                } else {
                    where = where.and(verPredicate);
                }
            }
        } else if (idValue != null) {
            WherePredicate idPredicate = WherePredicate.eq(idColumn, idValue);
            where = (where == null) ? idPredicate : where.and(idPredicate);
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

    private static Object computeNextVersion(Object currentVersion) {
        if (currentVersion instanceof Number num) {
            if (currentVersion instanceof Long) {
                return num.longValue() + 1;
            }
            return num.intValue() + 1;
        } else if (currentVersion instanceof Instant) {
            return Instant.now();
        } else if (currentVersion instanceof Timestamp) {
            return new Timestamp(System.currentTimeMillis());
        }
        return currentVersion;
    }

    /**
     * Checks the affected row count against optimistic locking requirements.
     * Throws {@link OptimisticLockException} if optimistic locking is enabled and 0 rows were updated.
     *
     * @param affectedRows the number of rows modified by the UPDATE statement
     * @throws OptimisticLockException if 0 rows were updated under optimistic locking
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
     * Builds and executes this UPDATE query using the supplied {@link JdbcExecutor},
     * automatically verifying optimistic locking concurrency control.
     *
     * @param executor the JdbcExecutor to use
     * @return the number of updated rows
     * @throws OptimisticLockException if optimistic locking is enabled and 0 rows are affected
     */
    public int execute(JdbcExecutor executor) {
        Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        return executor.executeUpdate(this);
    }
}
