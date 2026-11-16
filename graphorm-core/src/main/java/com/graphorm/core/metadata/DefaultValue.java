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

    static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

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
        if ("null".equalsIgnoreCase(trimmed)) {
            return new NullValue();
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return new BooleanValue(Boolean.parseBoolean(trimmed));
        }
        // Already quoted string literal: 'value' -> unescape '' to '
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2) {
            String unquoted = trimmed.substring(1, trimmed.length() - 1).replace("''", "'");
            return new StringValue(unquoted);
        }
        // Expressions with function calls: e.g. now(), autoincrement(), gen_random_uuid()
        if (trimmed.endsWith("()") || (trimmed.contains("(") && trimmed.endsWith(")"))) {
            return new ExpressionValue(trimmed);
        }
        // Known SQL date/time expression keywords
        if (SQL_EXPRESSIONS.contains(trimmed.toUpperCase(Locale.ROOT))) {
            return new ExpressionValue(trimmed);
        }
        // Numeric types
        if (isNumericType(javaType, typeName)) {
            return new NumericValue(trimmed);
        }
        // Numeric string on untyped field
        if (NUMERIC_PATTERN.matcher(trimmed).matches() && !isStringType(javaType, typeName)) {
            return new NumericValue(trimmed);
        }
        // String or enum types
        return new StringValue(trimmed);
    }

    static DefaultValue from(String raw, Class<?> javaType) {
        return from(raw, javaType, null);
    }

    static boolean isNumericType(Class<?> javaType, String typeName) {
        if (javaType != null) {
            if (javaType == Integer.class || javaType == int.class
                    || javaType == Long.class || javaType == long.class
                    || javaType == Short.class || javaType == short.class
                    || javaType == Double.class || javaType == double.class
                    || javaType == Float.class || javaType == float.class
                    || javaType == java.math.BigDecimal.class
                    || javaType == java.math.BigInteger.class) {
                return true;
            }
        }
        if (typeName != null) {
            String lower = typeName.toLowerCase(Locale.ROOT);
            return switch (lower) {
                case "int", "integer", "bigint", "smallint", "float", "double", "real", "decimal", "numeric" -> true;
                default -> false;
            };
        }
        return false;
    }

    static boolean isStringType(Class<?> javaType, String typeName) {
        if (javaType == String.class) {
            return true;
        }
        if (typeName != null) {
            String lower = typeName.toLowerCase(Locale.ROOT);
            return switch (lower) {
                case "string", "varchar", "text" -> true;
                default -> false;
            };
        }
        return false;
    }
}
