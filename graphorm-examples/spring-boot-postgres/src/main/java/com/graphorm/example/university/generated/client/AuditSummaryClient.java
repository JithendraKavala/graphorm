package com.graphorm.example.university.generated.client;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.example.university.generated.filter.AuditSummaryFilter;
import com.graphorm.example.university.generated.model.AuditSummary;
import com.graphorm.generator.client.QuerySpec;
import com.graphorm.generator.client.ReadOnlyModelAccessor;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Read-only type-safe client accessor for view {@link AuditSummary}.
 * <p>
 * STRICT READ-ONLY INVARIANT: Completely omits write and mutation methods.
 */
public class AuditSummaryClient implements ReadOnlyModelAccessor<AuditSummary, AuditSummaryFilter> {
    private final JdbcExecutor executor;

    private final ModelRegistry registry;

    private final SqlDialect dialect;

    private final ViewModelMetadata metadata;

    public AuditSummaryClient(JdbcExecutor executor, ModelRegistry registry, SqlDialect dialect) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.metadata = registry.getViewModel("AuditSummary");
    }

    @Override
    public List<AuditSummary> findMany() {
        return findMany(q -> {});
    }

    @Override
    public List<AuditSummary> findMany(Consumer<QuerySpec<AuditSummaryFilter>> specConsumer) {
        QuerySpec<AuditSummaryFilter> spec = new QuerySpec<AuditSummaryFilter>(() -> AuditSummaryFilter.INSTANCE);
        specConsumer.accept(spec);
        var planner = ViewQueryPlanner.plan(metadata, registry, dialect);
        if (spec.getPredicate() != null) planner.where(spec.getPredicate());
        for (OrderByClause ob : spec.getOrderBys()) planner.orderBy(ob);
        if (spec.getLimit() != null) planner.limit(spec.getLimit());
        if (spec.getOffset() != null) planner.offset(spec.getOffset());
        SelectQuery selectQuery = planner.build();
        return executor.queryForList(selectQuery, AuditSummary.class);
    }

    @Override
    public Optional<AuditSummary> findUnique(Consumer<QuerySpec<AuditSummaryFilter>> specConsumer) {
        Consumer<QuerySpec<AuditSummaryFilter>> limited = q -> { specConsumer.accept(q); q.limit(1); };
        List<AuditSummary> list = findMany(limited);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<AuditSummary> findFirst(Consumer<QuerySpec<AuditSummaryFilter>> specConsumer) {
        return findUnique(specConsumer);
    }

    @Override
    public long count() {
        return count(q -> {});
    }

    @Override
    public long count(Consumer<QuerySpec<AuditSummaryFilter>> specConsumer) {
        QuerySpec<AuditSummaryFilter> spec = new QuerySpec<AuditSummaryFilter>(() -> AuditSummaryFilter.INSTANCE);
        specConsumer.accept(spec);
        var planner = ViewQueryPlanner.plan(metadata, registry, dialect);
        if (spec.getPredicate() != null) planner.where(spec.getPredicate());
        SelectQuery selectQuery = planner.build();
        BoundSql innerSql = SelectBuilder.of(dialect).build(selectQuery);
        String countSql = "SELECT COUNT(*) FROM (" + innerSql.sql() + ") AS _view_count";
        BoundSql countBoundSql = new BoundSql(countSql, innerSql.parameters());
        List<Long> results = executor.queryForList(countBoundSql, (rs, rowNum) -> rs.getLong(1));
        return results.isEmpty() ? 0L : results.getFirst();
    }

    @Override
    public boolean exists(Consumer<QuerySpec<AuditSummaryFilter>> specConsumer) {
        return count(specConsumer) > 0;
    }
}
