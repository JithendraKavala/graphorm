package com.graphorm.graphql.fetcher;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic GraphQL {@link DataFetcher} for querying a single view model entity (e.g. {@code Query.studentDashboard(studentId: Int!)}).
 */
public class ViewSingleDataFetcher implements DataFetcher<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ViewSingleDataFetcher.class);

    private final ViewModelMetadata view;
    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final String idArgumentName;

    public ViewSingleDataFetcher(
            ViewModelMetadata view,
            ModelRegistry registry,
            JdbcExecutor executor,
            SqlDialect dialect,
            String idArgumentName
    ) {
        this.view = Objects.requireNonNull(view, "ViewModelMetadata must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = dialect != null ? dialect : executor.dialect();
        this.idArgumentName = idArgumentName != null ? idArgumentName : "id";
    }

    public ViewSingleDataFetcher(ViewModelMetadata view, ModelRegistry registry, JdbcExecutor executor) {
        this(view, registry, executor, executor.dialect(), "id");
    }

    @Override
    public Map<String, Object> get(DataFetchingEnvironment env) {
        log.debug("Executing ViewSingleDataFetcher for view: {}", view.name());

        String viewFieldName = resolveViewField(env);
        Object idValue = extractId(env, viewFieldName);
        if (idValue == null) {
            return null;
        }

        FieldMetadata fieldMeta = view.findField(viewFieldName).orElse(null);
        idValue = EntityRowNormalizer.coerceToFieldType(idValue, fieldMeta);

        ViewQueryPlanner.ViewQueryBuilder builder = ViewQueryPlanner.forView(view, registry, dialect)
                .where(WherePredicate.eq(viewFieldName, idValue))
                .limit(1);

        SelectQuery query = builder.build();
        Optional<Map<String, Object>> row = executor.queryForObject(query);
        return row.map(r -> EntityRowNormalizer.normalize(r, view)).orElse(null);
    }

    private String resolveViewField(DataFetchingEnvironment env) {
        if (view.hasField(idArgumentName)) {
            return idArgumentName;
        }
        for (String arg : env.getArguments().keySet()) {
            if (view.hasField(arg)) {
                return arg;
            }
        }
        if (view.hasField("id")) {
            return "id";
        }
        // First scalar field
        for (FieldMetadata f : view.fields()) {
            if (!f.isRelation()) {
                return f.name();
            }
        }
        return idArgumentName;
    }

    private Object extractId(DataFetchingEnvironment env, String viewFieldName) {
        if (env.containsArgument(idArgumentName) && env.getArgument(idArgumentName) != null) {
            return env.getArgument(idArgumentName);
        }
        if (env.containsArgument(viewFieldName) && env.getArgument(viewFieldName) != null) {
            return env.getArgument(viewFieldName);
        }
        if (env.containsArgument("id") && env.getArgument("id") != null) {
            return env.getArgument("id");
        }
        Object whereArg = env.getArgument("where");
        if (whereArg instanceof Map<?, ?> whereMap) {
            if (whereMap.get(idArgumentName) != null) {
                return whereMap.get(idArgumentName);
            }
            if (whereMap.get(viewFieldName) != null) {
                return whereMap.get(viewFieldName);
            }
            if (whereMap.get("id") != null) {
                return whereMap.get("id");
            }
        }
        return null;
    }

    public ViewModelMetadata view() {
        return view;
    }
}
