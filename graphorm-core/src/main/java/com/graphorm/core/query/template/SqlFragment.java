package com.graphorm.core.query.template;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.QueryType;
import com.graphorm.core.query.SqlQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe SQL fragment template escape hatch.
 * Ensures all SQL identifiers (tables, views, columns) referenced in templates are validated against
 * the {@link ModelRegistry} and safely formatted, and all parameters (:name or ?) are extracted into
 * a positional parameter list with 100% parameter extraction.
 */
public record SqlFragment(
        String sql,
        List<Object> parameters
) implements SqlQuery {

    private static final Pattern IDENTIFIER_TAG_PATTERN = Pattern.compile("\\{(table|col|column|view):([a-zA-Z0-9_.]+)\\}");
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile("(?<!:):([a-zA-Z0-9_]+)");

    public SqlFragment {
        Objects.requireNonNull(sql, "SQL must not be null");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    @Override
    public QueryType queryType() {
        return QueryType.SELECT;
    }

    public static SqlFragment of(String sql) {
        return new SqlFragment(sql, List.of());
    }

    public static SqlFragment of(String sql, List<Object> parameters) {
        return new SqlFragment(sql, parameters);
    }

    public static SqlFragment of(String sql, Object... parameters) {
        return new SqlFragment(sql, parameters != null ? List.of(parameters) : List.of());
    }

    /**
     * Creates a fluent builder with the given metadata registry.
     *
     * @param registry the model registry for identifier validation
     * @return a new Builder instance
     */
    public static Builder builder(ModelRegistry registry) {
        return new Builder(registry);
    }

    /**
     * Compiles a template string, resolving and validating identifier placeholders against the registry,
     * and binding named parameters (:param) to positional '?' markers.
     *
     * @param template   the template string (e.g. "SELECT {col:Student.name} FROM {table:Student} WHERE {col:Student.age} > :minAge")
     * @param registry   the model registry
     * @param parameters named parameter values
     * @return compiled SqlFragment with positional '?' markers and bound parameters
     */
    public static SqlFragment compile(String template, ModelRegistry registry, Map<String, Object> parameters) {
        return builder(registry).template(template).params(parameters).build();
    }

    /**
     * Fluent builder for {@link SqlFragment}.
     */
    public static final class Builder {
        private final ModelRegistry registry;
        private String template;
        private String defaultEntityName;
        private final Map<String, Object> params = new LinkedHashMap<>();
        private final List<Object> positionalParams = new ArrayList<>();

        private Builder(ModelRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "ModelRegistry must not be null");
        }

        public Builder template(String template) {
            this.template = template;
            return this;
        }

        public Builder forEntity(String entityName) {
            this.defaultEntityName = entityName;
            return this;
        }

        public Builder param(String name, Object value) {
            Objects.requireNonNull(name, "Parameter name must not be null");
            this.params.put(name, value);
            return this;
        }

        public Builder params(Map<String, Object> params) {
            if (params != null) {
                this.params.putAll(params);
            }
            return this;
        }

        public Builder positionalParam(Object value) {
            this.positionalParams.add(value);
            return this;
        }

        public Builder positionalParams(List<Object> values) {
            if (values != null) {
                this.positionalParams.addAll(values);
            }
            return this;
        }

        public SqlFragment build() {
            if (template == null || template.isBlank()) {
                throw new IllegalStateException("Template string must not be null or blank");
            }

            // 1. Resolve and validate identifiers: {table:Model}, {col:Model.field}, {col:field}
            Matcher identMatcher = IDENTIFIER_TAG_PATTERN.matcher(template);
            StringBuilder step1 = new StringBuilder();

            while (identMatcher.find()) {
                String type = identMatcher.group(1);
                String target = identMatcher.group(2);
                String replacement = resolveIdentifier(type, target);
                identMatcher.appendReplacement(step1, Matcher.quoteReplacement(replacement));
            }
            identMatcher.appendTail(step1);

            // 2. Replace named parameters (:name) with '?' and collect parameter values in order
            String sqlWithNamed = step1.toString();
            Matcher paramMatcher = NAMED_PARAM_PATTERN.matcher(sqlWithNamed);
            StringBuilder finalSql = new StringBuilder();
            List<Object> extractedParams = new ArrayList<>();

            while (paramMatcher.find()) {
                String paramName = paramMatcher.group(1);
                if (!params.containsKey(paramName)) {
                    throw new IllegalArgumentException("Missing value for named parameter ':" + paramName + "' in SQL template");
                }
                paramMatcher.appendReplacement(finalSql, "?");
                extractedParams.add(params.get(paramName));
            }
            paramMatcher.appendTail(finalSql);

            // If positional params were supplied, append them as well
            if (!positionalParams.isEmpty()) {
                extractedParams.addAll(positionalParams);
            }

            return new SqlFragment(finalSql.toString(), extractedParams);
        }

        private String resolveIdentifier(String type, String target) {
            if ("table".equalsIgnoreCase(type) || "view".equalsIgnoreCase(type)) {
                EntityMetadata entity = registry.findEntity(target).orElseThrow(() ->
                        new IllegalArgumentException("Unknown entity '" + target + "' in SQL template"));
                String physicalName = entity instanceof ModelMetadata m ? m.tableName() :
                        (entity instanceof ViewModelMetadata v ? v.viewName() : entity.name().toLowerCase());
                return "\"" + physicalName + "\"";
            }

            if ("col".equalsIgnoreCase(type) || "column".equalsIgnoreCase(type)) {
                String entityName;
                String fieldName;
                if (target.contains(".")) {
                    String[] parts = target.split("\\.", 2);
                    entityName = parts[0];
                    fieldName = parts[1];
                } else {
                    if (defaultEntityName == null) {
                        throw new IllegalArgumentException("Column placeholder '{" + type + ":" + target +
                                "}' requires qualified entity name or default entity set via forEntity()");
                    }
                    entityName = defaultEntityName;
                    fieldName = target;
                }

                EntityMetadata entity = registry.findEntity(entityName).orElseThrow(() ->
                        new IllegalArgumentException("Unknown entity '" + entityName + "' for column placeholder"));

                FieldMetadata field = entity.findField(fieldName).orElseThrow(() ->
                        new IllegalArgumentException("Unknown field '" + fieldName + "' on entity '" + entityName + "'"));

                return "\"" + field.dbColumnName() + "\"";
            }

            throw new IllegalArgumentException("Unsupported identifier type: " + type);
        }
    }
}
