package com.graphorm.graphql.fetcher;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.planner.AnalyzedSelectionSet;
import com.graphorm.graphql.planner.SelectionSetAnalyzer;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generic GraphQL {@link DataFetcher} for querying a single view model by unique key (e.g. {@code Query.studentDashboard(studentId: Int!)}).
 * Translates queries via {@link ViewQueryPlanner} with selective projection and join pruning.
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

        String pkField = resolvePkField(view, idArgumentName);
        FieldMetadata pkFieldMeta = view.findField(pkField).orElse(null);

        Object idValue = extractId(env, pkField);
        if (idValue == null) {
            return null;
        }

        idValue = EntityRowNormalizer.coerceToFieldType(idValue, pkFieldMeta);

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(env, view, registry);
        Set<String> selectedFieldNames = null;
        if (analyzed != null && !analyzed.requestedScalarFields().isEmpty()) {
            selectedFieldNames = new LinkedHashSet<>(analyzed.requestedScalarFields());
            selectedFieldNames.addAll(analyzed.hiddenDependencyFields());
        }

        SelectQuery query = ViewQueryPlanner.plan(view, registry, selectedFieldNames, dialect)
                .where(WherePredicate.eq(pkField, idValue))
                .limit(1)
                .build();

        Optional<Map<String, Object>> row = executor.queryForObject(query);
        return row.map(r -> EntityRowNormalizer.normalize(r, view)).orElse(null);
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
