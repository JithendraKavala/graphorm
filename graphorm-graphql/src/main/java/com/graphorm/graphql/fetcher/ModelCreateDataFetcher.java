package com.graphorm.graphql.fetcher;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.jdbc.dialect.SqlDialect;
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
 * Generic GraphQL {@link DataFetcher} for creating a model entity (e.g. {@code Mutation.createStudent(input: StudentInput!)}).
 */
public class ModelCreateDataFetcher implements DataFetcher<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ModelCreateDataFetcher.class);

    private final ModelMetadata model;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;
    private final String inputArgumentName;

    public ModelCreateDataFetcher(
            ModelMetadata model,
            JdbcExecutor executor,
            SqlDialect dialect,
            String inputArgumentName
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.executor = Objects.requireNonNull(executor, "JdbcExecutor must not be null");
        this.dialect = dialect != null ? dialect : executor.dialect();
        this.inputArgumentName = inputArgumentName != null ? inputArgumentName : "input";
    }

    public ModelCreateDataFetcher(ModelMetadata model, JdbcExecutor executor) {
        this(model, executor, executor.dialect(), "input");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(DataFetchingEnvironment env) {
        log.debug("Executing ModelCreateDataFetcher for model: {}", model.name());

        Map<String, Object> inputMap;
        if (env.containsArgument(inputArgumentName) && env.getArgument(inputArgumentName) instanceof Map<?, ?> m) {
            inputMap = (Map<String, Object>) m;
        } else {
            inputMap = env.getArguments();
        }

        Map<String, Object> dbValues = new LinkedHashMap<>();
        for (FieldMetadata field : model.fields()) {
            if (field.isRelation()) {
                continue;
            }
            if (inputMap.containsKey(field.name())) {
                Object raw = inputMap.get(field.name());
                dbValues.put(field.dbColumnName(), EntityRowNormalizer.coerceToFieldType(raw, field));
            } else if (inputMap.containsKey(field.dbColumnName())) {
                Object raw = inputMap.get(field.dbColumnName());
                dbValues.put(field.dbColumnName(), EntityRowNormalizer.coerceToFieldType(raw, field));
            }
        }

        InsertQuery insertQuery = new InsertQuery(SourceTable.table(model.tableName()), dbValues);
        Optional<Map<String, Object>> created = executor.executeInsertAndReturn(insertQuery);

        return created.map(row -> EntityRowNormalizer.normalize(row, model))
                .orElseGet(() -> EntityRowNormalizer.normalize(dbValues, model));
    }

    public ModelMetadata model() {
        return model;
    }
}
