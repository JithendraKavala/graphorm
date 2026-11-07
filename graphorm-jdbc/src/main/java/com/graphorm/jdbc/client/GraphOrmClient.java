package com.graphorm.jdbc.client;

import com.graphorm.core.exception.EntityNotFoundException;
import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.builder.WhereClauseBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.transaction.JdbcTransactionManager;
import com.graphorm.jdbc.transaction.TransactionCallback;
import com.graphorm.jdbc.transaction.TransactionManager;
import com.graphorm.jdbc.view.ViewQueryPlanner;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Root database client providing access to models, view models, query execution,
 * and transactions. Serves as the primary programmatic database API for GraphORM.
 */
public class GraphOrmClient {

    protected final JdbcExecutor executor;
    protected final ModelRegistry registry;
    protected final SqlDialect dialect;
    protected final TransactionManager transactionManager;

    public GraphOrmClient(JdbcExecutor executor, ModelRegistry registry, SqlDialect dialect, TransactionManager transactionManager) {
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.transactionManager = transactionManager != null ? transactionManager : new JdbcTransactionManager(executor.dataSource());
    }

    public GraphOrmClient(JdbcExecutor executor, ModelRegistry registry, SqlDialect dialect) {
        this(executor, registry, dialect, new JdbcTransactionManager(executor.dataSource()));
    }

    public GraphOrmClient(JdbcExecutor executor, ModelRegistry registry) {
        this(executor, registry, executor.dialect());
    }

    public static GraphOrmClient create(DataSource dataSource, ModelRegistry registry) {
        return new GraphOrmClient(new JdbcExecutor(dataSource), registry);
    }

    public static GraphOrmClient create(DataSource dataSource, ModelRegistry registry, SqlDialect dialect) {
        return new GraphOrmClient(new JdbcExecutor(dataSource, dialect), registry, dialect);
    }

    public static Builder builder() {
        return new Builder();
    }

    public JdbcExecutor executor() {
        return executor;
    }

    public ModelRegistry registry() {
        return registry;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public TransactionManager transactionManager() {
        return transactionManager;
    }

    public <T> T transaction(TransactionCallback<T> callback) {
        return transactionManager.executeInTransaction(callback);
    }

    public void transaction(Runnable action) {
        transactionManager.executeInTransaction(action);
    }

    // Dynamic operations
    public List<Map<String, Object>> findMany(String entityName) {
        return findMany(entityName, null, Collections.emptyList(), null, null);
    }

    public List<Map<String, Object>> findMany(String entityName, WherePredicate filter) {
        return findMany(entityName, filter, Collections.emptyList(), null, null);
    }

    public List<Map<String, Object>> findMany(String entityName, WherePredicate filter, List<OrderByClause> orderBy, Integer limit, Integer offset) {
        EntityMetadata entity = resolveEntity(entityName);
        SelectQuery.Builder queryBuilder = SelectBuilder.forEntity(entity, registry);

        if (filter != null) {
            queryBuilder.where(filter);
        }
        if (orderBy != null) {
            for (OrderByClause o : orderBy) {
                queryBuilder.orderBy(o);
            }
        }
        if (limit != null && limit > 0) {
            queryBuilder.limit(limit);
        }
        if (offset != null && offset > 0) {
            queryBuilder.offset(offset);
        }

        return executor.queryForList(queryBuilder.build());
    }

    public <T> List<T> findMany(String entityName, WherePredicate filter, Class<T> recordClass) {
        EntityMetadata entity = resolveEntity(entityName);
        SelectQuery.Builder queryBuilder = SelectBuilder.forEntity(entity, registry);

        if (filter != null) {
            queryBuilder.where(filter);
        }
        return executor.queryForList(queryBuilder.build(), recordClass);
    }

    public Optional<Map<String, Object>> findUnique(String entityName, Object id) {
        EntityMetadata entity = resolveEntity(entityName);
        String pkColumn = getPrimaryKeyColumn(entity);
        SelectQuery query = SelectBuilder.forEntity(entity, registry)
                .where(WherePredicate.eq(pkColumn, id))
                .limit(1)
                .build();
        return executor.queryForObject(query);
    }

    public <T> Optional<T> findUnique(String entityName, Object id, Class<T> recordClass) {
        EntityMetadata entity = resolveEntity(entityName);
        String pkColumn = getPrimaryKeyColumn(entity);
        SelectQuery query = SelectBuilder.forEntity(entity, registry)
                .where(WherePredicate.eq(pkColumn, id))
                .limit(1)
                .build();
        return executor.queryForObject(query, recordClass);
    }

    public Optional<Map<String, Object>> findFirst(String entityName, WherePredicate filter) {
        EntityMetadata entity = resolveEntity(entityName);
        SelectQuery.Builder queryBuilder = SelectBuilder.forEntity(entity, registry)
                .limit(1);
        if (filter != null) {
            queryBuilder.where(filter);
        }
        return executor.queryForObject(queryBuilder.build());
    }

    public Map<String, Object> create(String modelName, Map<String, Object> values) {
        ModelMetadata model = resolveModelForMutation(modelName, "create");
        Map<String, Object> dbValues = mapToDbColumns(model, values);
        InsertQuery query = InsertQuery.into(model.tableName())
                .values(dbValues)
                .build();
        return executor.executeInsertAndReturn(query)
                .orElse(values);
    }

    public int update(String modelName, Object id, Map<String, Object> values) {
        ModelMetadata model = resolveModelForMutation(modelName, "update");
        String pkColumn = getPrimaryKeyColumn(model);
        Map<String, Object> dbValues = mapToDbColumns(model, values);
        UpdateQuery query = UpdateQuery.table(model.tableName())
                .values(dbValues)
                .where(WherePredicate.eq(pkColumn, id))
                .build();
        return executor.executeUpdate(query);
    }

    public int delete(String modelName, Object id) {
        ModelMetadata model = resolveModelForMutation(modelName, "delete");
        String pkColumn = getPrimaryKeyColumn(model);
        DeleteQuery query = DeleteQuery.from(model.tableName())
                .where(WherePredicate.eq(pkColumn, id))
                .build();
        return executor.executeUpdate(query);
    }

    public long count(String entityName) {
        return count(entityName, null);
    }

    public long count(String entityName, WherePredicate filter) {
        EntityMetadata entity = resolveEntity(entityName);
        if (entity instanceof ViewModelMetadata vm && (vm.isVirtual() || vm.isRawQuery())) {
            var planner = ViewQueryPlanner.plan(vm, registry, dialect);
            if (filter != null) {
                planner.where(filter);
            }
            SelectQuery query = planner.build();
            BoundSql innerSql = SelectBuilder.of(dialect).build(query);
            String countSql = "SELECT COUNT(*) FROM (" + innerSql.sql() + ") AS count_wrapper";
            BoundSql countBoundSql = new BoundSql(countSql, innerSql.parameters());
            return executor.queryForObject(countBoundSql, (rs, rowNum) -> rs.getLong(1)).orElse(0L);
        }

        String sql = "SELECT COUNT(*) FROM " + dialect.quoteIdentifier(toSourceTable(entity).identifier());
        BoundSql boundSql;
        if (filter != null) {
            BoundSql whereClause = WhereClauseBuilder.buildWhereClause(filter, dialect);
            sql += whereClause.sql();
            boundSql = new BoundSql(sql, whereClause.parameters());
        } else {
            boundSql = new BoundSql(sql, Collections.emptyList());
        }
        return executor.queryForObject(boundSql, (rs, rowNum) -> rs.getLong(1)).orElse(0L);
    }

    private Map<String, Object> mapToDbColumns(ModelMetadata model, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String colName = model.findField(entry.getKey())
                    .map(FieldMetadata::dbColumnName)
                    .orElse(entry.getKey());
            mapped.put(colName, entry.getValue());
        }
        return mapped;
    }

    private EntityMetadata resolveEntity(String name) {
        return registry.findEntity(name)
                .orElseThrow(() -> new EntityNotFoundException("Entity '" + name + "' not found in ModelRegistry"));
    }

    private ModelMetadata resolveModelForMutation(String name, String operation) {
        if (registry.findViewModel(name).isPresent()) {
            throw new ReadOnlyViewException(name, operation);
        }
        return registry.getModel(name);
    }

    private SourceTable toSourceTable(EntityMetadata entity) {
        if (entity instanceof ViewModelMetadata vm) {
            return vm.isDatabaseBacked()
                    ? SourceTable.view(vm.viewName())
                    : (vm.sourceModelName() != null ? SourceTable.table(vm.sourceModelName()) : SourceTable.rawSql(vm.rawQuery(), vm.name().toLowerCase()));
        }
        if (entity instanceof ModelMetadata model) {
            return SourceTable.table(model.tableName());
        }
        return SourceTable.table(entity.name());
    }

    private String getPrimaryKeyColumn(EntityMetadata entity) {
        if (entity instanceof ModelMetadata model && !model.primaryKeyFields().isEmpty()) {
            String pkField = model.primaryKeyFields().getFirst();
            return model.findField(pkField)
                    .map(FieldMetadata::dbColumnName)
                    .orElse(pkField);
        }
        return entity.fields().stream()
                .filter(FieldMetadata::isPrimaryKey)
                .map(FieldMetadata::dbColumnName)
                .findFirst()
                .orElse("id");
    }

    public static final class Builder {
        private DataSource dataSource;
        private JdbcExecutor executor;
        private ModelRegistry registry;
        private SqlDialect dialect;
        private TransactionManager transactionManager;

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder executor(JdbcExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder registry(ModelRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder dialect(SqlDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder transactionManager(TransactionManager transactionManager) {
            this.transactionManager = transactionManager;
            return this;
        }

        public GraphOrmClient build() {
            SqlDialect activeDialect = dialect != null ? dialect : new PostgreSqlDialect();
            JdbcExecutor activeExecutor = executor != null ? executor : new JdbcExecutor(dataSource, activeDialect);
            return new GraphOrmClient(activeExecutor, registry, activeDialect, transactionManager);
        }
    }
}
