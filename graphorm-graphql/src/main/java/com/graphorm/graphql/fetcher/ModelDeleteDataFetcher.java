package com.graphorm.graphql.fetcher;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Generic GraphQL {@link DataFetcher} for deleting a model entity (e.g. {@code Mutation.deleteStudent(id: ID!): Boolean!}).
 * Enforces writability and model-level authorization.
 */
public class ModelDeleteDataFetcher implements DataFetcher<Object> {

    private static final Logger log = LoggerFactory.getLogger(ModelDeleteDataFetcher.class);

    private final ModelMetadata model;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final String idArgumentName;

    public ModelDeleteDataFetcher(
            ModelMetadata model,
            JdbcExecutor executor,
            SqlDialect dialect,
            String idArgumentName
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = dialect != null ? dialect : executor.dialect();
        this.idArgumentName = idArgumentName != null ? idArgumentName : "id";
    }

    public ModelDeleteDataFetcher(ModelMetadata model, JdbcExecutor executor) {
        this(model, executor, executor.dialect(), "id");
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        log.debug("Executing ModelDeleteDataFetcher for model: {}", model.name());

        QueryGuards.ensureWritable(model, "DELETE");

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
            throw new IllegalArgumentException("Cannot delete " + model.name() + " without specifying an identifier");
        }

        idValue = EntityRowNormalizer.coerceToFieldType(idValue, pkFieldMeta);

        DeleteQuery deleteQuery = new DeleteQuery(SourceTable.table(model.tableName()), WherePredicate.eq(pkColumn, idValue));
        int affected = executor.executeUpdate(deleteQuery);

        String returnTypeName = GraphQLTypeUtil.simplePrint(env.getFieldDefinition().getType());
        if (returnTypeName.contains("Boolean")) {
            return affected > 0;
        }
        return affected > 0 ? idValue : null;
    }

    private Object extractId(DataFetchingEnvironment env, String pkField) {
        if (env.containsArgument(idArgumentName) && env.getArgument(idArgumentName) != null) {
            return env.getArgument(idArgumentName);
        }
        if (env.containsArgument("id") && env.getArgument("id") != null) {
            return env.getArgument("id");
        }
        if (env.containsArgument(pkField) && env.getArgument(pkField) != null) {
            return env.getArgument(pkField);
        }
        Object whereArg = env.getArgument("where");
        if (whereArg instanceof Map<?, ?> whereMap) {
            if (whereMap.get(idArgumentName) != null) {
                return whereMap.get(idArgumentName);
            }
            if (whereMap.get("id") != null) {
                return whereMap.get("id");
            }
            if (whereMap.get(pkField) != null) {
                return whereMap.get(pkField);
            }
        }
        return null;
    }

    public ModelMetadata model() {
        return model;
    }
}
