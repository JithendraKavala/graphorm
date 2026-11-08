package com.graphorm.graphql.fetcher;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.planner.AnalyzedSelectionSet;
import com.graphorm.graphql.planner.SelectionSetAnalyzer;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.graphql.response.ResponseAssembler;
import com.graphorm.graphql.security.SecurityPlannerInterceptor;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic GraphQL {@link DataFetcher} for querying a single regular model by unique/primary key (e.g. {@code Query.student(id: ID!)}).
 * Supports selective column projection, hidden dependency foreign key injection, model authorization, and field-level security verification (TASK-15.2).
 */
public class ModelSingleDataFetcher implements DataFetcher<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ModelSingleDataFetcher.class);

    private final ModelMetadata model;
    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final String idArgumentName;

    public ModelSingleDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            JdbcExecutor executor,
            SqlDialect dialect,
            String idArgumentName
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.registry = registry;
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = dialect != null ? dialect : executor.dialect();
        this.idArgumentName = idArgumentName != null ? idArgumentName : "id";
    }

    public ModelSingleDataFetcher(ModelMetadata model, ModelRegistry registry, JdbcExecutor executor) {
        this(model, registry, executor, executor.dialect(), "id");
    }

    @Override
    public Map<String, Object> get(DataFetchingEnvironment env) {
        log.debug("Executing ModelSingleDataFetcher for model: {}", model.name());

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

        String pkField = model.primaryKeyFields().isEmpty() ? "id" : model.primaryKeyFields().getFirst();
        FieldMetadata pkFieldMeta = model.findField(pkField).orElse(null);
        String pkColumn = pkFieldMeta != null ? pkFieldMeta.dbColumnName() : pkField;

        Object idValue = extractId(env, pkField);
        if (idValue == null) {
            return null;
        }

        idValue = EntityRowNormalizer.coerceToFieldType(idValue, pkFieldMeta);

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(env, model, registry);

        if (analyzed != null) {
            FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
            SecurityPlannerInterceptor.verifyFieldAccess(model, analyzed.requestedScalarFields(), authorizer);
        }

        SelectQuery.Builder builder;
        if (analyzed != null && !analyzed.projectedColumns().isEmpty()) {
            builder = SelectQuery.from(model.tableName());
            builder.columns(analyzed.projectedColumns());
        } else {
            builder = SelectBuilder.forEntity(model, registry);
        }

        SelectQuery query = builder
                .where(WherePredicate.eq(pkColumn, idValue))
                .limit(1)
                .build();

        Optional<Map<String, Object>> row = executor.queryForObject(query);
        Map<String, Object> normalized = row.map(r -> EntityRowNormalizer.normalize(r, model)).orElse(null);
        if (normalized != null && analyzed != null && analyzed.requestedRelations().isEmpty() && !analyzed.hiddenDependencyFields().isEmpty()) {
            return ResponseAssembler.stripHidden(normalized, analyzed.hiddenDependencyFields());
        }
        return normalized;
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

    public ModelMetadata model() {
        return model;
    }
}
