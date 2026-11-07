package com.graphorm.graphql.fetcher;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.planner.AnalyzedSelectionSet;
import com.graphorm.graphql.planner.SelectionSetAnalyzer;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.graphql.response.ResponseAssembler;
import com.graphorm.graphql.security.SecurityPlannerInterceptor;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generic GraphQL {@link DataFetcher} for querying a list of view models (e.g. {@code Query.studentDashboards}).
 * Strictly read-only; delegates to {@link ViewQueryPlanner} for unified query execution.
 * Enforces field-level authorization (TASK-15.2).
 */
public class ViewListDataFetcher implements DataFetcher<List<Map<String, Object>>> {

    private static final Logger log = LoggerFactory.getLogger(ViewListDataFetcher.class);

    private final ViewModelMetadata view;
    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final ArgumentBinder argumentBinder;
    private final SqlDialect dialect;

    public ViewListDataFetcher(
            ViewModelMetadata view,
            ModelRegistry registry,
            JdbcExecutor executor,
            ArgumentBinder argumentBinder,
            SqlDialect dialect
    ) {
        this.view = Objects.requireNonNull(view, "ViewModelMetadata must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.argumentBinder = argumentBinder != null ? argumentBinder : new ArgumentBinder();
        this.dialect = dialect != null ? dialect : executor.dialect();
    }

    public ViewListDataFetcher(ViewModelMetadata view, ModelRegistry registry, JdbcExecutor executor) {
        this(view, registry, executor, new ArgumentBinder(), executor.dialect());
    }

    @Override
    public List<Map<String, Object>> get(DataFetchingEnvironment env) {
        log.debug("Executing ViewListDataFetcher for view: {}", view.name());

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

        ViewQueryPlanner.ViewQueryBuilder builder = ViewQueryPlanner.plan(view, registry, selectedFieldNames, dialect);

        WherePredicate where = argumentBinder.bindWhere(env, view);
        if (where != null) {
            builder.where(where);
        }

        List<OrderByClause> orderBy = argumentBinder.bindOrderBy(env, view);
        if (!orderBy.isEmpty()) {
            builder.orderBy(orderBy.toArray(new OrderByClause[0]));
        }

        Integer limit = argumentBinder.bindLimit(env);
        if (limit != null) {
            builder.limit(limit);
        }

        Integer offset = argumentBinder.bindOffset(env);
        if (offset != null) {
            builder.offset(offset);
        }

        SelectQuery query = builder.build();
        List<Map<String, Object>> rows = executor.queryForList(query);
        List<Map<String, Object>> normalized = rows.stream()
                .map(row -> EntityRowNormalizer.normalize(row, view))
                .toList();

        if (analyzed != null && !analyzed.hiddenDependencyFields().isEmpty()) {
            return ResponseAssembler.stripHidden(normalized, analyzed.hiddenDependencyFields());
        }
        return normalized;
    }

    public ViewModelMetadata view() {
        return view;
    }
}
