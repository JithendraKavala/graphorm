package com.graphorm.example.university.generated.client;

import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.example.university.generated.filter.AuditLogFilter;
import com.graphorm.example.university.generated.input.AuditLogCreateInput;
import com.graphorm.example.university.generated.input.AuditLogUpdateInput;
import com.graphorm.example.university.generated.model.AuditLog;
import com.graphorm.generator.client.ModelAccessor;
import com.graphorm.generator.client.QuerySpec;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Type-safe client accessor for {@link AuditLog}.
 */
public class AuditLogClient implements ModelAccessor<AuditLog, AuditLogFilter, AuditLogCreateInput, AuditLogUpdateInput> {
    private final JdbcExecutor executor;

    private final ModelRegistry registry;

    private final SqlDialect dialect;

    private final ModelMetadata metadata;

    public AuditLogClient(JdbcExecutor executor, ModelRegistry registry, SqlDialect dialect) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.metadata = registry.getModel("AuditLog");
    }

    @Override
    public List<AuditLog> findMany() {
        return findMany(q -> {});
    }

    @Override
    public List<AuditLog> findMany(Consumer<QuerySpec<AuditLogFilter>> specConsumer) {
        QuerySpec<AuditLogFilter> spec = new QuerySpec<AuditLogFilter>(() -> AuditLogFilter.INSTANCE);
        specConsumer.accept(spec);
        SelectQuery.Builder qb = SelectQuery.from(metadata.tableName());
        qb.column("id", "id");
        qb.column("action", "action");
        qb.column("details", "details");
        qb.column("tenant_id", "tenantId");
        qb.column("performed_by", "performedBy");
        if (spec.getPredicate() != null) qb.where(spec.getPredicate());
        if (!spec.getOrderBys().isEmpty()) qb.orderBy(spec.getOrderBys());
        if (spec.getLimit() != null) qb.limit(spec.getLimit());
        if (spec.getOffset() != null) qb.offset(spec.getOffset());
        return executor.queryForList(qb.build(), AuditLog.class);
    }

    @Override
    public Optional<AuditLog> findUnique(Consumer<QuerySpec<AuditLogFilter>> specConsumer) {
        Consumer<QuerySpec<AuditLogFilter>> limited = q -> { specConsumer.accept(q); q.limit(1); };
        List<AuditLog> list = findMany(limited);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<AuditLog> findFirst(Consumer<QuerySpec<AuditLogFilter>> specConsumer) {
        return findUnique(specConsumer);
    }

    @Override
    public Optional<AuditLog> findById(Object id) {
        return findUnique(q -> q.where(WherePredicate.eq("id", id)));
    }

    @Override
    public long count() {
        return count(q -> {});
    }

    @Override
    public long count(Consumer<QuerySpec<AuditLogFilter>> specConsumer) {
        QuerySpec<AuditLogFilter> spec = new QuerySpec<AuditLogFilter>(() -> AuditLogFilter.INSTANCE);
        specConsumer.accept(spec);
        SelectQuery.Builder qb = SelectQuery.from(metadata.tableName()).column(ProjectedColumn.of("COUNT(*)", "cnt"));
        if (spec.getPredicate() != null) qb.where(spec.getPredicate());
        List<Long> results = executor.queryForList(qb.build(), (rs, rowNum) -> rs.getLong(1));
        return results.isEmpty() ? 0L : results.getFirst();
    }

    @Override
    public boolean exists(Consumer<QuerySpec<AuditLogFilter>> specConsumer) {
        return count(specConsumer) > 0;
    }

    @Override
    public AuditLog create(AuditLogCreateInput createInput) {
        QueryGuards.ensureWritable(metadata, "INSERT");
        Objects.requireNonNull(createInput, "createInput must not be null");
        Map<String, Object> values = createInput.toMap();
        InsertQuery insertQuery = InsertQuery.into(metadata.tableName()).values(values).build();
        if (dialect.supportsReturning()) {
                    Optional<AuditLog> created = executor.executeInsertAndReturn(insertQuery, AuditLog.class);
                    if (created.isPresent()) return created.get();
                } else {
                    executor.executeUpdate(insertQuery);
                };
        Object pkVal = values.get("id");
        if (pkVal != null) return findById(pkVal).orElseThrow();
        return findMany(q -> q.limit(1)).stream().findFirst().orElseThrow();
    }

    @Override
    public AuditLog update(Object id, AuditLogUpdateInput updateInput) {
        QueryGuards.ensureWritable(metadata, "UPDATE");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(updateInput, "updateInput must not be null");
        Map<String, Object> values = updateInput.toMap();
        UpdateQuery updateQuery = UpdateQuery.table(metadata.tableName()).values(values).where(WherePredicate.eq("id", id)).build();
        executor.executeUpdate(updateQuery);
        return findById(id).orElseThrow();
    }

    @Override
    public boolean delete(Object id) {
        QueryGuards.ensureWritable(metadata, "DELETE");
        Objects.requireNonNull(id, "id must not be null");
        DeleteQuery deleteQuery = DeleteQuery.from(metadata.tableName()).where(WherePredicate.eq("id", id)).build();
        return executor.executeUpdate(deleteQuery) > 0;
    }

    @Override
    public long deleteMany(Consumer<QuerySpec<AuditLogFilter>> specConsumer) {
        QueryGuards.ensureWritable(metadata, "DELETE");
        QuerySpec<AuditLogFilter> spec = new QuerySpec<AuditLogFilter>(() -> AuditLogFilter.INSTANCE);
        specConsumer.accept(spec);
        WherePredicate predicate = spec.getPredicate() == null ? WherePredicate.raw("1=1") : spec.getPredicate();
        DeleteQuery deleteQuery = DeleteQuery.from(metadata.tableName()).where(predicate).build();
        return (long) executor.executeUpdate(deleteQuery);
    }
}
