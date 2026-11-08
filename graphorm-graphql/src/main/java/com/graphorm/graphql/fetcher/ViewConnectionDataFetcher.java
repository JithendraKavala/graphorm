package com.graphorm.graphql.fetcher;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.pagination.CursorCodec;
import com.graphorm.core.pagination.KeysetPredicateGenerator;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.pagination.Connection;
import com.graphorm.graphql.pagination.Edge;
import com.graphorm.graphql.pagination.PageInfo;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.graphql.security.SecurityPlannerInterceptor;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Relay-spec compliant GraphQL {@link DataFetcher} for keyset cursor-paginated queries
 * on View Models returning a {@link Connection}.
 */
public class ViewConnectionDataFetcher implements DataFetcher<Connection<Map<String, Object>>> {

    private static final Logger log = LoggerFactory.getLogger(ViewConnectionDataFetcher.class);
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final ViewModelMetadata view;
    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final ArgumentBinder argumentBinder;
    private final SqlDialect dialect;

    public ViewConnectionDataFetcher(
            ViewModelMetadata view,
            ModelRegistry registry,
            JdbcExecutor executor,
            ArgumentBinder argumentBinder,
            SqlDialect dialect
    ) {
        this.view = Objects.requireNonNull(view, "ViewModelMetadata must not be null");
        this.registry = registry;
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.argumentBinder = argumentBinder != null ? argumentBinder : new ArgumentBinder();
        this.dialect = dialect != null ? dialect : executor.dialect();
    }

    @Override
    public Connection<Map<String, Object>> get(DataFetchingEnvironment env) {
        log.debug("Executing ViewConnectionDataFetcher for view: {}", view.name());

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

        FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
        for (FieldMetadata field : view.fields()) {
            if (field.hasAuthRoles()) {
                SecurityPlannerInterceptor.verifyFieldAccess(view, List.of(field.name()), authorizer);
            }
        }

        SelectQuery.Builder builder = SelectQuery.from(view.viewName());
        for (FieldMetadata f : view.fields()) {
            builder.column(f.name());
        }

        WherePredicate where = argumentBinder.bindWhere(env, view);
        if (where != null) {
            builder.where(where);
        }

        List<OrderByClause> orderBy = argumentBinder.bindOrderBy(env, view);
        String tieBreaker = view.fields().isEmpty() ? "id" : view.fields().get(0).name();
        List<OrderByClause> effectiveOrderBy = KeysetPredicateGenerator.ensureTieBreaker(orderBy, tieBreaker);
        builder.orderBy(effectiveOrderBy);

        String after = argumentBinder.bindAfter(env);
        String before = argumentBinder.bindBefore(env);
        Integer first = argumentBinder.bindFirst(env);
        Integer last = argumentBinder.bindLast(env);

        int requestedSize = first != null && first > 0 ? first : (last != null && last > 0 ? last : DEFAULT_PAGE_SIZE);
        int pageSize = Math.min(requestedSize, MAX_PAGE_SIZE);

        if (after != null) {
            builder.after(after);
        }
        if (before != null) {
            builder.before(before);
        }

        builder.first(pageSize + 1);

        SelectQuery query = builder.build();
        List<Map<String, Object>> rows = executor.queryForList(query);

        boolean hasMore = rows.size() > pageSize;
        List<Map<String, Object>> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        List<Edge<Map<String, Object>>> edges = new ArrayList<>(pageRows.size());
        for (Map<String, Object> row : pageRows) {
            Map<String, Object> normalized = EntityRowNormalizer.normalize(row, view);
            List<Object> cursorValues = new ArrayList<>();
            for (OrderByClause ob : effectiveOrderBy) {
                Object val = row.get(ob.column());
                if (val == null) {
                    val = normalized.get(ob.column());
                }
                cursorValues.add(val);
            }
            String cursor = CursorCodec.encode(cursorValues);
            edges.add(new Edge<>(normalized, cursor));
        }

        boolean hasNextPage = (first != null || after != null) ? hasMore : false;
        boolean hasPreviousPage = (last != null || before != null) ? hasMore : (after != null);
        String startCursor = edges.isEmpty() ? null : edges.get(0).cursor();
        String endCursor = edges.isEmpty() ? null : edges.get(edges.size() - 1).cursor();

        PageInfo pageInfo = new PageInfo(hasNextPage, hasPreviousPage, startCursor, endCursor);

        Integer totalCount = null;
        if (env.getSelectionSet() != null && env.getSelectionSet().contains("totalCount")) {
            SelectQuery countQuery = SelectQuery.from(view.viewName())
                    .columns("COUNT(*) as cnt")
                    .where(where)
                    .build();
            totalCount = executor.queryForObject(countQuery)
                    .map(m -> ((Number) m.get("cnt")).intValue())
                    .orElse(0);
        }

        return new Connection<>(edges, pageInfo, totalCount);
    }
}
