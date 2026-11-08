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
import com.graphorm.example.university.generated.filter.CourseFilter;
import com.graphorm.example.university.generated.input.CourseCreateInput;
import com.graphorm.example.university.generated.input.CourseUpdateInput;
import com.graphorm.example.university.generated.model.Course;
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
 * Type-safe client accessor for {@link Course}.
 */
public class CourseClient implements ModelAccessor<Course, CourseFilter, CourseCreateInput, CourseUpdateInput> {
    private final JdbcExecutor executor;

    private final ModelRegistry registry;

    private final SqlDialect dialect;

    private final ModelMetadata metadata;

    public CourseClient(JdbcExecutor executor, ModelRegistry registry, SqlDialect dialect) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.metadata = registry.getModel("Course");
    }

    @Override
    public List<Course> findMany() {
        return findMany(q -> {});
    }

    @Override
    public List<Course> findMany(Consumer<QuerySpec<CourseFilter>> specConsumer) {
        QuerySpec<CourseFilter> spec = new QuerySpec<CourseFilter>(() -> CourseFilter.INSTANCE);
        specConsumer.accept(spec);
        SelectQuery.Builder qb = SelectQuery.from(metadata.tableName());
        qb.column("id", "id");
        qb.column("title", "title");
        qb.column("code", "code");
        qb.column("credits", "credits");
        qb.column("department_id", "departmentId");
        qb.column("instructor_id", "instructorId");
        if (spec.getPredicate() != null) qb.where(spec.getPredicate());
        if (!spec.getOrderBys().isEmpty()) qb.orderBy(spec.getOrderBys());
        if (spec.getLimit() != null) qb.limit(spec.getLimit());
        if (spec.getOffset() != null) qb.offset(spec.getOffset());
        return executor.queryForList(qb.build(), Course.class);
    }

    @Override
    public Optional<Course> findUnique(Consumer<QuerySpec<CourseFilter>> specConsumer) {
        Consumer<QuerySpec<CourseFilter>> limited = q -> { specConsumer.accept(q); q.limit(1); };
        List<Course> list = findMany(limited);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<Course> findFirst(Consumer<QuerySpec<CourseFilter>> specConsumer) {
        return findUnique(specConsumer);
    }

    @Override
    public Optional<Course> findById(Object id) {
        return findUnique(q -> q.where(WherePredicate.eq("id", id)));
    }

    @Override
    public long count() {
        return count(q -> {});
    }

    @Override
    public long count(Consumer<QuerySpec<CourseFilter>> specConsumer) {
        QuerySpec<CourseFilter> spec = new QuerySpec<CourseFilter>(() -> CourseFilter.INSTANCE);
        specConsumer.accept(spec);
        SelectQuery.Builder qb = SelectQuery.from(metadata.tableName()).column(ProjectedColumn.of("COUNT(*)", "cnt"));
        if (spec.getPredicate() != null) qb.where(spec.getPredicate());
        List<Long> results = executor.queryForList(qb.build(), (rs, rowNum) -> rs.getLong(1));
        return results.isEmpty() ? 0L : results.getFirst();
    }

    @Override
    public boolean exists(Consumer<QuerySpec<CourseFilter>> specConsumer) {
        return count(specConsumer) > 0;
    }

    @Override
    public Course create(CourseCreateInput createInput) {
        QueryGuards.ensureWritable(metadata, "INSERT");
        Objects.requireNonNull(createInput, "createInput must not be null");
        Map<String, Object> values = createInput.toMap();
        InsertQuery insertQuery = InsertQuery.into(metadata.tableName()).values(values).build();
        if (dialect.supportsReturning()) {
                    Optional<Course> created = executor.executeInsertAndReturn(insertQuery, Course.class);
                    if (created.isPresent()) return created.get();
                } else {
                    executor.executeUpdate(insertQuery);
                };
        Object pkVal = values.get("id");
        if (pkVal != null) return findById(pkVal).orElseThrow();
        return findMany(q -> q.limit(1)).stream().findFirst().orElseThrow();
    }

    @Override
    public Course update(Object id, CourseUpdateInput updateInput) {
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
    public long deleteMany(Consumer<QuerySpec<CourseFilter>> specConsumer) {
        QueryGuards.ensureWritable(metadata, "DELETE");
        QuerySpec<CourseFilter> spec = new QuerySpec<CourseFilter>(() -> CourseFilter.INSTANCE);
        specConsumer.accept(spec);
        WherePredicate predicate = spec.getPredicate() == null ? WherePredicate.raw("1=1") : spec.getPredicate();
        DeleteQuery deleteQuery = DeleteQuery.from(metadata.tableName()).where(predicate).build();
        return (long) executor.executeUpdate(deleteQuery);
    }
}
