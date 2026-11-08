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
import com.graphorm.example.university.generated.filter.EnrollmentFilter;
import com.graphorm.example.university.generated.input.EnrollmentCreateInput;
import com.graphorm.example.university.generated.input.EnrollmentUpdateInput;
import com.graphorm.example.university.generated.model.Enrollment;
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
 * Type-safe client accessor for {@link Enrollment}.
 */
public class EnrollmentClient implements ModelAccessor<Enrollment, EnrollmentFilter, EnrollmentCreateInput, EnrollmentUpdateInput> {
    private final JdbcExecutor executor;

    private final ModelRegistry registry;

    private final SqlDialect dialect;

    private final ModelMetadata metadata;

    public EnrollmentClient(JdbcExecutor executor, ModelRegistry registry, SqlDialect dialect) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.metadata = registry.getModel("Enrollment");
    }

    @Override
    public List<Enrollment> findMany() {
        return findMany(q -> {});
    }

    @Override
    public List<Enrollment> findMany(Consumer<QuerySpec<EnrollmentFilter>> specConsumer) {
        QuerySpec<EnrollmentFilter> spec = new QuerySpec<EnrollmentFilter>(() -> EnrollmentFilter.INSTANCE);
        specConsumer.accept(spec);
        SelectQuery.Builder qb = SelectQuery.from(metadata.tableName());
        qb.column("id", "id");
        qb.column("student_id", "studentId");
        qb.column("course_id", "courseId");
        qb.column("grade", "grade");
        qb.column("grade_points", "gradePoints");
        qb.column("enrollment_date", "enrollmentDate");
        if (spec.getPredicate() != null) qb.where(spec.getPredicate());
        if (!spec.getOrderBys().isEmpty()) qb.orderBy(spec.getOrderBys());
        if (spec.getLimit() != null) qb.limit(spec.getLimit());
        if (spec.getOffset() != null) qb.offset(spec.getOffset());
        return executor.queryForList(qb.build(), Enrollment.class);
    }

    @Override
    public Optional<Enrollment> findUnique(Consumer<QuerySpec<EnrollmentFilter>> specConsumer) {
        Consumer<QuerySpec<EnrollmentFilter>> limited = q -> { specConsumer.accept(q); q.limit(1); };
        List<Enrollment> list = findMany(limited);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<Enrollment> findFirst(Consumer<QuerySpec<EnrollmentFilter>> specConsumer) {
        return findUnique(specConsumer);
    }

    @Override
    public Optional<Enrollment> findById(Object id) {
        return findUnique(q -> q.where(WherePredicate.eq("id", id)));
    }

    @Override
    public long count() {
        return count(q -> {});
    }

    @Override
    public long count(Consumer<QuerySpec<EnrollmentFilter>> specConsumer) {
        QuerySpec<EnrollmentFilter> spec = new QuerySpec<EnrollmentFilter>(() -> EnrollmentFilter.INSTANCE);
        specConsumer.accept(spec);
        SelectQuery.Builder qb = SelectQuery.from(metadata.tableName()).column(ProjectedColumn.of("COUNT(*)", "cnt"));
        if (spec.getPredicate() != null) qb.where(spec.getPredicate());
        List<Long> results = executor.queryForList(qb.build(), (rs, rowNum) -> rs.getLong(1));
        return results.isEmpty() ? 0L : results.getFirst();
    }

    @Override
    public boolean exists(Consumer<QuerySpec<EnrollmentFilter>> specConsumer) {
        return count(specConsumer) > 0;
    }

    @Override
    public Enrollment create(EnrollmentCreateInput createInput) {
        QueryGuards.ensureWritable(metadata, "INSERT");
        Objects.requireNonNull(createInput, "createInput must not be null");
        Map<String, Object> values = createInput.toMap();
        InsertQuery insertQuery = InsertQuery.into(metadata.tableName()).values(values).build();
        if (dialect.supportsReturning()) {
                    Optional<Enrollment> created = executor.executeInsertAndReturn(insertQuery, Enrollment.class);
                    if (created.isPresent()) return created.get();
                } else {
                    executor.executeUpdate(insertQuery);
                };
        Object pkVal = values.get("id");
        if (pkVal != null) return findById(pkVal).orElseThrow();
        return findMany(q -> q.limit(1)).stream().findFirst().orElseThrow();
    }

    @Override
    public Enrollment update(Object id, EnrollmentUpdateInput updateInput) {
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
    public long deleteMany(Consumer<QuerySpec<EnrollmentFilter>> specConsumer) {
        QueryGuards.ensureWritable(metadata, "DELETE");
        QuerySpec<EnrollmentFilter> spec = new QuerySpec<EnrollmentFilter>(() -> EnrollmentFilter.INSTANCE);
        specConsumer.accept(spec);
        WherePredicate predicate = spec.getPredicate() == null ? WherePredicate.raw("1=1") : spec.getPredicate();
        DeleteQuery deleteQuery = DeleteQuery.from(metadata.tableName()).where(predicate).build();
        return (long) executor.executeUpdate(deleteQuery);
    }
}
