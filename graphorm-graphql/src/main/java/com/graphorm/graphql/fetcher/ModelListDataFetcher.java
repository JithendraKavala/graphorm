package com.graphorm.graphql.fetcher;

import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generic GraphQL {@link DataFetcher} for querying a list of regular models (e.g. {@code Query.students}).
 * Handles dynamic filtering (where), sorting (orderBy), and pagination (limit, offset).
 */
public class ModelListDataFetcher implements DataFetcher<List<Map<String, Object>>> {

    private static final Logger log = LoggerFactory.getLogger(ModelListDataFetcher.class);

    private final ModelMetadata model;
    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final ArgumentBinder argumentBinder;
    private final SqlDialect dialect;

    public ModelListDataFetcher(
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
    public List<Map<String, Object>> get(DataFetchingEnvironment env) {
        log.debug("Executing ModelListDataFetcher for model: {}", model.name());

        SelectQuery.Builder builder = SelectBuilder.forEntity(model, registry);

        WherePredicate where = argumentBinder.bindWhere(env, model);
        if (where != null) {
            builder.where(where);
        }

        List<OrderByClause> orderBy = argumentBinder.bindOrderBy(env, model);
        if (!orderBy.isEmpty()) {
            builder.orderBy(orderBy);
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
        return rows.stream()
                .map(row -> EntityRowNormalizer.normalize(row, model))
                .toList();
    }

    public ModelMetadata model() {
        return model;
    }
}
