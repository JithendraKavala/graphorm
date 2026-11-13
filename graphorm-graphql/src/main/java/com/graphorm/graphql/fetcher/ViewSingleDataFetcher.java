package com.graphorm.graphql.fetcher;

import com.graphorm.core.cache.CacheKeyBuilder;
import com.graphorm.core.cache.GraphOrmCacheManager;
import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.graphql.planner.AnalyzedSelectionSet;
import com.graphorm.graphql.planner.SelectionSetAnalyzer;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.graphql.response.ResponseAssembler;
import com.graphorm.graphql.security.SecurityPlannerInterceptor;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generic GraphQL {@link DataFetcher} for querying a single view model by unique key (e.g. {@code Query.studentDashboard(studentId: Int!)}).
 * Translates queries via {@link ViewQueryPlanner} with selective projection, join pruning, view authorization,
 * field-level authorization (TASK-15.2), and L2 view result caching.
 */
public class ViewSingleDataFetcher implements DataFetcher<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ViewSingleDataFetcher.class);

    private final ViewModelMetadata view;
    private final ModelRegistry registry;
    private final QueryExecutionEngine executor;
    private final SqlDialect dialect;
    private final String idArgumentName;

    public ViewSingleDataFetcher(
            ViewModelMetadata view,
            ModelRegistry registry,
            QueryExecutionEngine executor,
            SqlDialect dialect,
            String idArgumentName
    ) {
        this.view = Objects.requireNonNull(view, "ViewModelMetadata must not be null");
        this.registry = registry;
        this.executor = Objects.requireNonNull(executor, "QueryExecutionEngine must not be null");
        this.dialect = dialect != null ? dialect : (executor instanceof JdbcExecutor je ? je.dialect() : new PostgreSqlDialect());
        this.idArgumentName = idArgumentName != null ? idArgumentName : "id";
    }

    public ViewSingleDataFetcher(ViewModelMetadata view, ModelRegistry registry, QueryExecutionEngine executor) {
        this(view, registry, executor, (executor instanceof JdbcExecutor je ? je.dialect() : new PostgreSqlDialect()), "id");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(DataFetchingEnvironment env) {
        log.debug("Executing ViewSingleDataFetcher for view: {}", view.name());

        if (view.hasAuthRoles()) {
            FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
            if (!authorizer.isAuthorized(view.authRoles())) {
                log.warn("Access denied to view '{}'. Required roles: {}, user roles: {}",
                        view.name(), view.authRoles(), authorizer.getCurrentRoles());
                throw new AccessDeniedException(
                        "ACCESS_DENIED",
                        "Access denied to view '" + view.name() + "'. Required roles: " + view.authRoles()
                );
            }
        }

        String pkField = resolvePkField(view, idArgumentName);
        FieldMetadata pkFieldMeta = view.findField(pkField).orElse(null);

        Object idValue = extractId(env, pkField);
        if (idValue == null) {
            return null;
        }

        idValue = EntityRowNormalizer.coerceToFieldType(idValue, pkFieldMeta);

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(env, view, registry);

        if (analyzed != null) {
            FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
            SecurityPlannerInterceptor.verifyFieldAccess(view, analyzed.requestedScalarFields(), authorizer);
        }

        Set<String> selectedFieldNames = null;
        if (analyzed != null && !analyzed.requestedScalarFields().isEmpty()) {
            selectedFieldNames = new LinkedHashSet<>(analyzed.requestedScalarFields());
            selectedFieldNames.addAll(analyzed.hiddenDependencyFields());
        }

        SelectQuery query = ViewQueryPlanner.plan(view, registry, selectedFieldNames, dialect)
                .where(WherePredicate.eq(pkField, idValue))
                .limit(1)
                .build();

        Optional<GraphOrmCacheManager> cacheOpt = executor.cacheManager();
        if (view.isCached() && cacheOpt.isPresent()) {
            GraphOrmCacheManager cm = cacheOpt.get();
            String tenantId = view.isMultiTenant()
                    ? QueryGuards.requireTenantContext(view, "SELECT")
                    : TenantContext.getCurrentTenantId().orElse(null);
            Collection<String> projected = analyzed != null ? analyzed.completeProjectedFields() : Collections.emptyList();
            String cacheKey = CacheKeyBuilder.forEntityId(view.name(), tenantId, idValue, projected);
            Optional<Map> cached = cm.get(cacheKey, Map.class);
            if (cached.isPresent()) {
                return (Map<String, Object>) cached.get();
            }
            Optional<Map<String, Object>> row = executor.queryForObject(query);
            Map<String, Object> normalized = row.map(r -> EntityRowNormalizer.normalize(r, view)).orElse(null);
            Map<String, Object> result = normalized;
            if (normalized != null && analyzed != null && !analyzed.hiddenDependencyFields().isEmpty()) {
                result = ResponseAssembler.stripHidden(normalized, analyzed.hiddenDependencyFields());
            }
            if (result != null) {
                cm.put(cacheKey, result, view.cacheTtl(), view.cacheTags());
            }
            return result;
        }

        Optional<Map<String, Object>> row = executor.queryForObject(query);
        Map<String, Object> normalized = row.map(r -> EntityRowNormalizer.normalize(r, view)).orElse(null);
        if (normalized != null && analyzed != null && !analyzed.hiddenDependencyFields().isEmpty()) {
            return ResponseAssembler.stripHidden(normalized, analyzed.hiddenDependencyFields());
        }
        return normalized;
    }

    private String resolvePkField(ViewModelMetadata view, String idArgName) {
        if (view.hasField(idArgName)) {
            return idArgName;
        }
        for (FieldMetadata f : view.fields()) {
            if (f.name().equalsIgnoreCase(idArgName) || f.name().equalsIgnoreCase("id") || f.name().equalsIgnoreCase(view.name() + "Id")) {
                return f.name();
            }
        }
        return idArgName;
    }

    private Object extractId(DataFetchingEnvironment env, String pkField) {
        if (env.containsArgument(idArgumentName)) {
            return env.getArgument(idArgumentName);
        }
        if (env.containsArgument("id")) {
            return env.getArgument("id");
        }
        if (env.containsArgument(pkField)) {
            return env.getArgument(pkField);
        }
        Object whereArg = env.getArgument("where");
        if (whereArg instanceof Map<?, ?> whereMap) {
            if (whereMap.containsKey(idArgumentName)) {
                return whereMap.get(idArgumentName);
            }
            if (whereMap.containsKey("id")) {
                return whereMap.get("id");
            }
            if (whereMap.containsKey(pkField)) {
                return whereMap.get(pkField);
            }
        }
        return null;
    }

    public ViewModelMetadata view() {
        return view;
    }
}
