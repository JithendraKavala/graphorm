package com.graphorm.graphql.fetcher;

import com.graphorm.core.cache.CacheKeyBuilder;
import com.graphorm.core.cache.GraphOrmCacheManager;
import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.planner.AnalyzedSelectionSet;
import com.graphorm.graphql.planner.SelectionSetAnalyzer;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.graphql.response.ResponseAssembler;
import com.graphorm.graphql.security.SecurityPlannerInterceptor;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic GraphQL {@link DataFetcher} for querying a list of regular models (e.g. {@code Query.students}).
 * Handles selective column projection, hidden dependency foreign key injection (TASK-11.2, TASK-11.3),
 * model authorization, field-level security verification (TASK-15.2), dynamic filtering (where), sorting (orderBy), and pagination (limit, offset).
 * Enforces a default maximum page size limit for unbounded list queries (PERF-02) and supports L2 query result caching.
 */
public class ModelListDataFetcher implements DataFetcher<List<Map<String, Object>>> {

    private static final Logger log = LoggerFactory.getLogger(ModelListDataFetcher.class);
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private final ModelMetadata model;
    private final ModelRegistry registry;
    private final QueryExecutionEngine executor;
    private final ArgumentBinder argumentBinder;
    private final SqlDialect dialect;
    private final int maxPageSize;

    public ModelListDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            QueryExecutionEngine executor,
            ArgumentBinder argumentBinder,
            SqlDialect dialect,
            int maxPageSize
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.registry = registry;
        this.executor = Objects.requireNonNull(executor, "QueryExecutionEngine must not be null");
        this.argumentBinder = argumentBinder != null ? argumentBinder : new ArgumentBinder();
        this.dialect = dialect != null ? dialect : (executor instanceof JdbcExecutor je ? je.dialect() : new PostgreSqlDialect());
        this.maxPageSize = maxPageSize > 0 ? maxPageSize : DEFAULT_MAX_PAGE_SIZE;
    }

    public ModelListDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            QueryExecutionEngine executor,
            ArgumentBinder argumentBinder,
            SqlDialect dialect
    ) {
        this(model, registry, executor, argumentBinder, dialect, DEFAULT_MAX_PAGE_SIZE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> get(DataFetchingEnvironment env) {
        log.debug("Executing ModelListDataFetcher for model: {}", model.name());

        if (model.hasAuthRoles()) {
            FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
            if (!authorizer.isAuthorized(model.authRoles())) {
                log.warn("Access denied to model '{}'. Required roles: {}, user roles: {}",
                        model.name(), model.authRoles(), authorizer.getCurrentRoles());
                throw new AccessDeniedException(
                        "ACCESS_DENIED",
                        "Access denied to model '" + model.name() + "'. Required roles: " + model.authRoles()
                );
            }
        }

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(env, model, registry);

        if (analyzed != null) {
            FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
            SecurityPlannerInterceptor.verifyFieldAccess(model, analyzed.requestedScalarFields(), authorizer);
        }

        SelectQuery.Builder builder = SelectBuilder.forEntity(model, registry);
        if (analyzed != null && !analyzed.projectedColumns().isEmpty()) {
            builder.columns(analyzed.projectedColumns());
        }

        WherePredicate where = argumentBinder.bindWhere(env, model);
        if (where != null) {
            builder.where(where);
        }

        List<OrderByClause> orderBy = argumentBinder.bindOrderBy(env, model);
        if (!orderBy.isEmpty()) {
            builder.orderBy(orderBy);
        }

        Integer limit = argumentBinder.bindLimit(env);
        if (limit != null && limit > 0) {
            builder.limit(Math.min(limit, maxPageSize));
        } else {
            builder.limit(maxPageSize);
        }

        Integer offset = argumentBinder.bindOffset(env);
        if (offset != null) {
            builder.offset(offset);
        }

        SelectQuery query = builder.build();
        Optional<GraphOrmCacheManager> cacheOpt = executor.cacheManager();
        if (model.isCached() && cacheOpt.isPresent()) {
            GraphOrmCacheManager cm = cacheOpt.get();
            String tenantId = TenantContext.getTenantId().orElse(null);
            String queryHash = CacheKeyBuilder.hashQuery(query);
            String cacheKey = CacheKeyBuilder.forQuery(model.name(), tenantId, queryHash);
            Optional<List> cached = cm.get(cacheKey, List.class);
            if (cached.isPresent()) {
                return (List<Map<String, Object>>) cached.get();
            }
            List<Map<String, Object>> rows = executor.queryForList(query);
            List<Map<String, Object>> normalizedRows = rows.stream()
                    .map(r -> EntityRowNormalizer.normalize(r, model))
                    .toList();
            List<Map<String, Object>> result = normalizedRows;
            if (analyzed != null && analyzed.requestedRelations().isEmpty() && !analyzed.hiddenDependencyFields().isEmpty()) {
                result = ResponseAssembler.stripHiddenList(normalizedRows, analyzed.hiddenDependencyFields());
            }
            cm.put(cacheKey, result, model.cacheTtl(), model.cacheTags());
            return result;
        }

        List<Map<String, Object>> rows = executor.queryForList(query);
        List<Map<String, Object>> normalizedRows = rows.stream()
                .map(r -> EntityRowNormalizer.normalize(r, model))
                .toList();

        if (analyzed != null && analyzed.requestedRelations().isEmpty() && !analyzed.hiddenDependencyFields().isEmpty()) {
            return ResponseAssembler.stripHiddenList(normalizedRows, analyzed.hiddenDependencyFields());
        }
        return normalizedRows;
    }

    public ModelMetadata model() {
        return model;
    }

    public int maxPageSize() {
        return maxPageSize;
    }
}
