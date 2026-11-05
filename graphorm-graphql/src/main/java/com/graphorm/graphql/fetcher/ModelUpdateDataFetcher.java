package com.graphorm.graphql.fetcher;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic GraphQL {@link DataFetcher} for updating a model entity (e.g. {@code Mutation.updateStudent(id: ID!, input: StudentInput!)}).
 */
public class ModelUpdateDataFetcher implements DataFetcher<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ModelUpdateDataFetcher.class);

    private final ModelMetadata model;
    private final ModelRegistry registry;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final String idArgumentName;
    private final String inputArgumentName;

    public ModelUpdateDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            JdbcExecutor executor,
            SqlDialect dialect,
            String idArgumentName,
            String inputArgumentName
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.registry = registry;
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = dialect != null ? dialect : executor.dialect();
        this.idArgumentName = idArgumentName != null ? idArgumentName : "id";
        this.inputArgumentName = inputArgumentName != null ? inputArgumentName : "input";
    }

    public ModelUpdateDataFetcher(ModelMetadata model, ModelRegistry registry, JdbcExecutor executor) {
        this(model, registry, executor, executor.dialect(), "id", "input");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(DataFetchingEnvironment env) {
        log.debug("Executing ModelUpdateDataFetcher for model: {}", model.name());

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
            if (field.isRelation()) {
                continue;
            }
            // Do not update primary key column
            if (field.name().equals(pkField) || field.dbColumnName().equals(pkColumn)) {
                continue;
            }
            if (inputMap.containsKey(field.name())) {
                Object coerced = EntityRowNormalizer.coerceToFieldType(inputMap.get(field.name()), field);
                dbValues.put(field.dbColumnName(), coerced);
            } else if (inputMap.containsKey(field.dbColumnName())) {
                Object coerced = EntityRowNormalizer.coerceToFieldType(inputMap.get(field.dbColumnName()), field);
                dbValues.put(field.dbColumnName(), coerced);
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

        Optional<Map<String, Object>> result;
        if (dialect.supportsReturning()) {
            BoundSql updateBoundSql = UpdateBuilder.of(dialect)
                    .table(model.tableName())
                    .values(dbValues)
                    .where(WherePredicate.eq(pkColumn, idValue))
                    .returningAll()
                    .build();
            result = executor.queryForObject(updateBoundSql);
        } else {
            BoundSql updateBoundSql = UpdateBuilder.of(dialect)
                    .table(model.tableName())
                    .values(dbValues)
                    .where(WherePredicate.eq(pkColumn, idValue))
                    .build();
            executor.executeUpdate(updateBoundSql);

            SelectQuery selectQuery = SelectBuilder.forEntity(model, registry)
                    .where(WherePredicate.eq(pkColumn, idValue))
                    .limit(1)
                    .build();
            result = executor.queryForObject(selectQuery);
        }

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
