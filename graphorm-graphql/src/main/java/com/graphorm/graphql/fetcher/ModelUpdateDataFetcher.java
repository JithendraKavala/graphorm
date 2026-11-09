package com.graphorm.graphql.fetcher;

import com.graphorm.jdbc.dialect.PostgreSqlDialect;

import com.graphorm.core.execution.QueryExecutionEngine;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import com.graphorm.graphql.security.InjectedValueResolver;
import com.graphorm.graphql.security.SecurityPlannerInterceptor;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generic GraphQL {@link DataFetcher} for updating a model entity (e.g. {@code Mutation.updateStudent(id: ID!, input: StudentInput!)}).
 * Enforces writability, model authorization, field-level authorization on input fields, primary key immutability,
 * optimistic locking via version column tracking, hydrates backend-injected fields from context,
 * and invalidates tag-dependent L2 entity/view caches upon update.
 */
public class ModelUpdateDataFetcher implements DataFetcher<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ModelUpdateDataFetcher.class);

    private final ModelMetadata model;
    private final ModelRegistry registry;
    private final QueryExecutionEngine executor;
    private final SqlDialect dialect;
    private final String idArgumentName;
    private final String inputArgumentName;

    public ModelUpdateDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            QueryExecutionEngine executor,
            SqlDialect dialect,
            String idArgumentName,
            String inputArgumentName
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "QueryExecutionEngine must not be null");
        this.dialect = dialect != null ? dialect : (executor instanceof JdbcExecutor je ? je.dialect() : new PostgreSqlDialect());
        this.idArgumentName = idArgumentName != null ? idArgumentName : "id";
        this.inputArgumentName = inputArgumentName != null ? inputArgumentName : "input";
    }

    public ModelUpdateDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            QueryExecutionEngine executor
    ) {
        this(model, registry, executor, (executor instanceof JdbcExecutor je ? je.dialect() : new PostgreSqlDialect()), "id", "input");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(DataFetchingEnvironment env) {
        log.debug("Executing ModelUpdateDataFetcher for model: {}", model.name());

        QueryGuards.ensureWritable(model, "UPDATE");

        FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
        if (model.hasAuthRoles()) {
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

        Map<String, Object> inputMap;
        if (env.containsArgument(inputArgumentName) && env.getArgument(inputArgumentName) instanceof Map<?, ?> m) {
            inputMap = (Map<String, Object>) m;
        } else {
            inputMap = env.getArguments();
        }

        Object idValue = extractId(env, inputMap, pkField);
        if (idValue == null) {
            throw new IllegalArgumentException("Cannot update " + model.name() + " without specifying an identifier");
        }

        idValue = EntityRowNormalizer.coerceToFieldType(idValue, pkFieldMeta);

        Map<String, Object> dbValues = new LinkedHashMap<>();
        for (FieldMetadata field : model.fields()) {
            if (field.isRelation() || field.isInjected()) {
                continue;
            }
            // Do not update primary key column
            if (field.name().equals(pkField) || field.dbColumnName().equals(pkColumn)) {
                continue;
            }
            if (inputMap.containsKey(field.name()) || inputMap.containsKey(field.dbColumnName())) {
                if (field.hasAuthRoles()) {
                    SecurityPlannerInterceptor.verifyFieldAccess(model, List.of(field.name()), authorizer);
                }
                Object raw = inputMap.containsKey(field.name()) ? inputMap.get(field.name()) : inputMap.get(field.dbColumnName());
                Object coerced = EntityRowNormalizer.coerceToFieldType(raw, field);
                dbValues.put(field.dbColumnName(), coerced);
            }
        }

        // Hydrate backend-injected fields from context
        for (FieldMetadata field : model.fields()) {
            if (field.isRelation() || field.name().equals(pkField) || field.dbColumnName().equals(pkColumn) || !field.isInjected()) {
                continue;
            }
            Object injectedValue = InjectedValueResolver.resolve(field, env);
            if (injectedValue != null) {
                dbValues.put(field.dbColumnName(), EntityRowNormalizer.coerceToFieldType(injectedValue, field));
            }
        }

        if (dbValues.isEmpty()) {
            SelectQuery selectQuery = SelectBuilder.forEntity(model, registry)
                    .where(WherePredicate.eq(pkColumn, idValue))
                    .limit(1)
                    .build();
            return executor.queryForObject(selectQuery)
                    .map(r -> EntityRowNormalizer.normalize(r, model))
                    .orElse(null);
        }

        WherePredicate updateWhere = WherePredicate.eq(pkColumn, idValue);
        if (model.isMultiTenant()) {
            String tenantId = QueryGuards.requireTenantContext(model, "update");
            if (tenantId != null) {
                updateWhere = updateWhere.and(WherePredicate.eq(model.tenantColumn(), tenantId));
                dbValues.put(model.tenantColumn(), tenantId);
            }
        }

        FieldMetadata versionField = model.hasVersionField() ? model.versionField().orElse(null) : null;
        Object expectedVersion = null;
        if (versionField != null) {
            if (inputMap.containsKey(versionField.name())) {
                expectedVersion = inputMap.get(versionField.name());
            } else if (inputMap.containsKey(versionField.dbColumnName())) {
                expectedVersion = inputMap.get(versionField.dbColumnName());
            }
            if (expectedVersion != null) {
                updateWhere = updateWhere.and(WherePredicate.eq(versionField.dbColumnName(), expectedVersion));
            }
        }

        UpdateQuery updateQuery = new UpdateQuery(
                SourceTable.table(model.tableName()),
                dbValues,
                updateWhere
        );
        Optional<Map<String, Object>> result = executor.executeUpdateAndReturn(updateQuery);

        if (result.isEmpty() && versionField != null && expectedVersion != null) {
            throw new OptimisticLockException(
                    "Optimistic lock failure: record in '" + model.tableName() +
                            "' with identifier '" + idValue + "' was modified or deleted by another transaction (expected version: " +
                            expectedVersion + ")",
                    null,
                    model.name(),
                    idValue,
                    expectedVersion,
                    null
            );
        }

        executor.cacheManager().ifPresent(cm -> cm.evictByTags(Set.of(model.name(), model.tableName())));

        return result.map(r -> EntityRowNormalizer.normalize(r, model)).orElse(null);
    }

    private Object extractId(DataFetchingEnvironment env, Map<String, Object> inputMap, String pkField) {
        if (env.containsArgument(idArgumentName) && env.getArgument(idArgumentName) != null) {
            return env.getArgument(idArgumentName);
        }
        if (env.containsArgument("id") && env.getArgument("id") != null) {
            return env.getArgument("id");
        }
        if (env.containsArgument(pkField) && env.getArgument(pkField) != null) {
            return env.getArgument(pkField);
        }
        if (inputMap != null) {
            if (inputMap.get(idArgumentName) != null) {
                return inputMap.get(idArgumentName);
            }
            if (inputMap.get("id") != null) {
                return inputMap.get("id");
            }
            if (inputMap.get(pkField) != null) {
                return inputMap.get(pkField);
            }
        }
        return null;
    }

    public ModelMetadata model() {
        return model;
    }
}
