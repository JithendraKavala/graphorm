package com.graphorm.graphql.fetcher;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
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
import com.graphorm.jdbc.builder.SelectBuilder;
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

/**\n * Relay-spec compliant GraphQL {@link DataFetcher} for keyset cursor-paginated queries\n * returning a {@link Connection}.\n */
public class ModelConnectionDataFetcher implements DataFetcher<Connection<Map<String, Object>>> {

    private static final Logger log = LoggerFactory.getLogger(ModelConnectionDataFetcher.class);
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final ModelMetadata model;
    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final ArgumentBinder argumentBinder;
    private final SqlDialect dialect;

    public ModelConnectionDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            JdbcExecutor executor,
            ArgumentBinder argumentBinder,
            SqlDialect dialect
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.registry = registry;
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.argumentBinder = argumentBinder != null ? argumentBinder : new ArgumentBinder();
        this.dialect = dialect != null ? dialect : executor.dialect();
    }

    @Override
    public Connection<Map<String, Object>> get(DataFetchingEnvironment env) {
        log.debug("Executing ModelConnectionDataFetcher for model: {}", model.name());

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

        // Verify field-level authorization
        FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
        for (FieldMetadata field : model.fields()) {
            if (field.hasAuthRoles()) {
                SecurityPlannerInterceptor.verifyFieldAccess(model, List.of(field.name()), authorizer);
            }
        }

        SelectQuery.Builder builder = SelectBuilder.forEntity(model, registry);

        WherePredicate where = argumentBinder.bindWhere(env, model);
        if (where != null) {
            builder.where(where);
        }

        List<OrderByClause> orderBy = argumentBinder.bindOrderBy(env, model);
        String tieBreaker = model.primaryKeyFields().isEmpty() ? "id" : model.primaryKeyFields().get(0);
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

        // Fetch pageSize + 1 to determine hasNextPage
        builder.first(pageSize + 1);

        SelectQuery query = builder.build();
        List<Map<String, Object>> rows = executor.queryForList(query);

        boolean hasMore = rows.size() > pageSize;
        List<Map<String, Object>> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        List<Edge<Map<String, Object>>> edges = new ArrayList<>(pageRows.size());
        for (Map<String, Object> row : pageRows) {
            Map<String, Object> normalized = EntityRowNormalizer.normalize(row, model);
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
            SelectQuery countQuery = SelectQuery.from(model.tableName())
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
