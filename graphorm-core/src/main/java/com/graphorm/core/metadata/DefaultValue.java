package com.graphorm.core.metadata;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Semantic representation of a database column or field default value.
 * Distinguishes string literals, numeric literals, boolean literals,
 * SQL null literals, and database function/expression calls.
 */
public sealed interface DefaultValue permits
        DefaultValue.StringValue,
        DefaultValue.NumericValue,
        DefaultValue.BooleanValue,
        DefaultValue.NullValue,
        DefaultValue.ExpressionValue {

    /**
     * Textual or raw value representation.
     */
    String asString();

    /**
     * String literal default value (e.g. "ACTIVE", "John's", "").
     *
     * @param value unquoted string literal content
     */
    record StringValue(String value) implements DefaultValue {
        public StringValue {
            Objects.requireNonNull(value, "String default value must not be null");
        }

        @Override
        public String asString() {
            return value;
        }
    }

    /**
     * Numeric literal default value (e.g. 0, -5, 9.99).
     *
     * @param value string representation of the numeric literal
     */
    record NumericValue(String value) implements DefaultValue {
        public NumericValue {
            Objects.requireNonNull(value, "Numeric default value must not be null");
        }

        @Override
        public String asString() {
            return value;
        }
    }

    /**
     * Boolean literal default value (true / false).
     *
     * @param value boolean value
     */
    record BooleanValue(boolean value) implements DefaultValue {
        @Override
        public String asString() {
            return String.valueOf(value);
        }
    }

    /**
     * Explicit SQL NULL default value.
     */
    record NullValue() implements DefaultValue {
        @Override
        public String asString() {
            return "NULL";
        }
    }

    /**
     * SQL or database expression default value (e.g. now(), CURRENT_TIMESTAMP, gen_random_uuid(), autoincrement()).
     *
     * @param expression the raw SQL expression
     */
    record ExpressionValue(String expression) implements DefaultValue {
        public ExpressionValue {
            Objects.requireNonNull(expression, "Expression default value must not be null");
        }

        @Override
        public String asString() {
            return expression;
        }
    }

    static DefaultValue ofString(String value) {
        return new StringValue(value);
    }

    static DefaultValue ofNumeric(String value) {
        return new NumericValue(value);
    }

    static DefaultValue ofNumeric(long value) {
        return new NumericValue(String.valueOf(value));
    }

    static DefaultValue ofNumeric(double value) {
        return new NumericValue(String.valueOf(value));
    }

    static DefaultValue ofBoolean(boolean value) {
        return new BooleanValue(value);
    }

    static DefaultValue ofNull() {
        return new NullValue();
    }

    static DefaultValue ofExpression(String expression) {
        return new ExpressionValue(expression);
    }

    static final Set<String> SQL_EXPRESSIONS = Set.of(
            "CURRENT_TIMESTAMP",
            "CURRENT_DATE",
            "CURRENT_TIME",
            "LOCALTIMESTAMP",
            "LOCALTIME"
    );

    static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$");

    /**
     * Parses or infers a {@link DefaultValue} from a string representation and optional type context.
     *
     * @param raw      raw default string
     * @param javaType Java type of the field
     * @param typeName type name of the field
     * @return inferred DefaultValue, or null if raw is null
     */
    static DefaultValue from(String raw, Class<?> javaType, String typeName) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new StringValue("");
        }
        // Already quoted string literal: 'value' -> unescape '' to '
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2) {
            String unquoted = trimmed.substring(1, trimmed.length() - 1).replace("''", "'");
            return new StringValue(unquoted);
        }
        // Double-quoted string literal: "value" -> unquote
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            String unquoted = trimmed.substring(1, trimmed.length() - 1);
            return new StringValue(unquoted);
        }
        // Expressions with function calls: e.g. now(), autoincrement(), gen_random_uuid(), concat(...)
        if (trimmed.endsWith("()") || (trimmed.contains("(") && trimmed.endsWith(")"))) {
            return new ExpressionValue(trimmed);
        }
        // Known SQL date/time expression keywords
        if (SQL_EXPRESSIONS.contains(trimmed.toUpperCase(Locale.ROOT))) {
            return new ExpressionValue(trimmed.toUpperCase(Locale.ROOT));
        }
        // Explicit type context takes precedence over content-based heuristic matching
        if (isStringType(javaType, typeName)) {
            if ("NULL".equals(trimmed)) {
                return new NullValue();
            }
            return new StringValue(trimmed);
        }
        // Boolean type context
        if (isBooleanType(javaType, typeName)) {
            if ("NULL".equalsIgnoreCase(trimmed)) {
                return new NullValue();
            }
            if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
                return new BooleanValue(true);
            }
            if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
                return new BooleanValue(false);
            }
        }
        // Numeric types
        if (isNumericType(javaType, typeName)) {
            if ("NULL".equalsIgnoreCase(trimmed)) {
                return new NullValue();
            }
            return new NumericValue(trimmed);
        }
        // Untyped / fallback inference when declared type is not specified
        if ("null".equalsIgnoreCase(trimmed)) {
            return new NullValue();
        }
        // Boolean literal fallback
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return new BooleanValue(Boolean.parseBoolean(trimmed));
        }
        // Numeric string on untyped field
        if (NUMERIC_PATTERN.matcher(trimmed).matches()) {
            return new NumericValue(trimmed);
        }
        // String or enum types fallback
        return new StringValue(trimmed);
    }

    static DefaultValue from(String raw, Class<?> javaType) {
        return from(raw, javaType, null);
    }

    /**
     * Normalizes a SQL or schema type name by stripping type parameters/modifiers
     * (e.g. {@code VARCHAR(50)} -> {@code varchar}, {@code CHARACTER VARYING (255)} -> {@code character varying})
     * and collapsing whitespace.
     *
     * @param typeName raw type name
     * @return normalized lowercase base type name, or null if typeName is null
     */
    static String normalizeTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        String normalized = typeName.trim();
        int parenIdx = normalized.indexOf('(');
        if (parenIdx >= 0) {
            normalized = normalized.substring(0, parenIdx).trim();
        }
        return normalized.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static boolean isNumericType(Class<?> javaType, String typeName) {
        if (javaType != null) {
            if (Number.class.isAssignableFrom(javaType)
                    || javaType == int.class
                    || javaType == long.class
                    || javaType == short.class
                    || javaType == byte.class
                    || javaType == double.class
                    || javaType == float.class) {
                return true;
            }
        }
        if (typeName != null) {
            String norm = normalizeTypeName(typeName);
            return switch (norm) {
                case "int", "integer", "bigint", "smallint", "tinyint",
                     "float", "double", "double precision", "real",
                     "decimal", "dec", "numeric" -> true;
                default -> false;
            };
        }
        return false;
    }

    static boolean isStringType(Class<?> javaType, String typeName) {
        if (javaType == String.class || javaType == Character.class || javaType == char.class) {
            return true;
        }
        if (typeName != null) {
            String norm = normalizeTypeName(typeName);
            return switch (norm) {
                case "string", "varchar", "char", "character", "character varying",
                     "nvarchar", "nchar", "national character", "national character varying",
                     "text", "tinytext", "mediumtext", "longtext", "clob", "nclob", "citext" -> true;
                default -> false;
            };
        }
        return false;
    }

    static boolean isBooleanType(Class<?> javaType, String typeName) {
        if (javaType == Boolean.class || javaType == boolean.class) {
            return true;
        }
        if (typeName != null) {
            String norm = normalizeTypeName(typeName);
            return switch (norm) {
                case "bool", "boolean" -> true;
                default -> false;
            };
        }
        return false;
    }
}
