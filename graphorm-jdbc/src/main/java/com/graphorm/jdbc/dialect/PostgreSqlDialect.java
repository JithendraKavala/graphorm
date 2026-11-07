package com.graphorm.jdbc.dialect;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.jdbc.builder.DeleteBuilder;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.executor.BoundSql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PostgreSQL dialect implementation supporting double-quoted identifiers, positional '?'
 * placeholders, LIMIT/OFFSET pagination, RETURNING clauses, and standard aggregation functions.
 */
public class PostgreSqlDialect implements SqlDialect {

    private static final Pattern EXPLICIT_EXPRESSION_PATTERN =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*\\)(::[A-Za-z0-9_]+)?$");

    @Override
    public String quoteIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "Identifier must not be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be blank");
        }

        if (identifier.equals("*")) {
            return "*";
        }

        if (identifier.contains(";") || identifier.contains("--") || identifier.contains("/*")
                || identifier.contains("*/") || identifier.contains("\0") || identifier.contains("\r")
                || identifier.contains("\n")) {
            throw new IllegalArgumentException("Identifier contains illegal syntax characters: " + identifier);
        }

        // Allow explicit SQL function or aggregate expressions without quoting
        if (EXPLICIT_EXPRESSION_PATTERN.matcher(identifier).matches()) {
            return identifier;
        }

        if (identifier.contains("(") || identifier.contains(")")) {
            throw new IllegalArgumentException("Identifier contains illegal syntax characters (parentheses): " + identifier);
        }

        // Handle composite or qualified identifiers like "students.id" or "s.*"
        if (identifier.contains(".")) {
            String[] parts = identifier.split("\\.");
            return Arrays.stream(parts)
                    .map(this::quoteSingleIdentifier)
                    .collect(Collectors.joining("."));
        }

        return quoteSingleIdentifier(identifier);
    }

    private String quoteSingleIdentifier(String part) {
        if (part.equals("*")) {
            return "*";
        }
        if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2) {
            String inner = part.substring(1, part.length() - 1);
            boolean escaped = true;
            for (int i = 0; i < inner.length(); i++) {
                if (inner.charAt(i) == '"') {
                    if (i + 1 < inner.length() && inner.charAt(i + 1) == '"') {
                        i++;
                    } else {
                        escaped = false;
                        break;
                    }
                }
            }
            if (escaped) {
                return part;
            }
        }
        return "\"" + part.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String formatLimitOffset(Integer limit, Integer offset) {
        if (limit != null && offset != null) {
            return "LIMIT ? OFFSET ?";
        } else if (limit != null) {
            return "LIMIT ?";
        } else if (offset != null) {
            return "OFFSET ?";
        }
        return "";
    }

    @Override
    public BoundSql buildSelectSql(SelectQuery query) {
        return new SelectBuilder(this).build(query);
    }

    @Override
    public BoundSql buildInsertSql(InsertQuery query) {
        return new InsertBuilder(this).build(query);
    }

    @Override
    public BoundSql buildUpdateSql(UpdateQuery query) {
        return new UpdateBuilder(this).build(query);
    }

    @Override
    public BoundSql buildDeleteSql(DeleteQuery query) {
        return new DeleteBuilder(this).build(query);
    }

    @Override
    public String buildAggregateSql(AggregateType type, String expression) {
        Objects.requireNonNull(type, "AggregateType must not be null");
        Objects.requireNonNull(expression, "Expression must not be null");
        return switch (type) {
            case COUNT -> "COUNT(" + expression + ")";
            case SUM -> "SUM(" + expression + ")";
            case AVG -> "AVG(" + expression + ")";
            case MIN -> "MIN(" + expression + ")";
            case MAX -> "MAX(" + expression + ")";
        };
    }

    @Override
    public boolean supportsReturning() {
        return true;
    }

    @Override
    public String renderReturningClause(List<String> columns) {
        if (columns == null || columns.isEmpty() || columns.contains("*")) {
            return "RETURNING *";
        }
        return "RETURNING " + columns.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
    }

    @Override
    public Object convertToDatabaseValue(Object value, FieldMetadata field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof java.util.Date d && !(value instanceof Date) && !(value instanceof Timestamp)) {
            return new Timestamp(d.getTime());
        }
        return value;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object convertFromDatabaseValue(Object dbValue, FieldMetadata field) {
        if (dbValue == null || field == null || field.javaType() == null) {
            return dbValue;
        }

        Class<?> target = field.javaType();
        if (target.isInstance(dbValue)) {
            return dbValue;
        }

        if (target == String.class) {
            return dbValue.toString();
        }
        if (target == Integer.class || target == int.class) {
            return ((Number) dbValue).intValue();
        }
        if (target == Long.class || target == long.class) {
            return ((Number) dbValue).longValue();
        }
        if (target == Double.class || target == double.class) {
            return ((Number) dbValue).doubleValue();
        }
        if (target == Float.class || target == float.class) {
            return ((Number) dbValue).floatValue();
        }
        if (target == Short.class || target == short.class) {
            return ((Number) dbValue).shortValue();
        }
        if (target == Byte.class || target == byte.class) {
            return ((Number) dbValue).byteValue();
        }
        if (target == BigDecimal.class) {
            if (dbValue instanceof BigDecimal bd) return bd;
            if (dbValue instanceof Long l) return BigDecimal.valueOf(l);
            if (dbValue instanceof Integer i) return BigDecimal.valueOf(i);
            return new BigDecimal(dbValue.toString());
        }
        if (target == BigInteger.class) {
            if (dbValue instanceof BigInteger bi) return bi;
            if (dbValue instanceof Long l) return BigInteger.valueOf(l);
            if (dbValue instanceof Integer i) return BigInteger.valueOf(i);
            if (dbValue instanceof BigDecimal bd) return bd.toBigInteger();
            return new BigInteger(dbValue.toString());
        }
        if (target == Boolean.class || target == boolean.class) {
            if (dbValue instanceof Boolean b) return b;
            if (dbValue instanceof Number n) return n.intValue() != 0;
            return Boolean.parseBoolean(dbValue.toString());
        }
        if (target == UUID.class) {
            if (dbValue instanceof UUID u) return u;
            return UUID.fromString(dbValue.toString());
        }
        if (target == LocalDate.class) {
            if (dbValue instanceof Date d) return d.toLocalDate();
            if (dbValue instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
            return LocalDate.parse(dbValue.toString());
        }
        if (target == LocalTime.class) {
            if (dbValue instanceof Time t) return t.toLocalTime();
            return LocalTime.parse(dbValue.toString());
        }
        if (target == LocalDateTime.class) {
            if (dbValue instanceof Timestamp ts) return ts.toLocalDateTime();
            return LocalDateTime.parse(dbValue.toString());
        }
        if (target == Instant.class) {
            if (dbValue instanceof Timestamp ts) return ts.toInstant();
            if (dbValue instanceof OffsetDateTime odt) return odt.toInstant();
            return Instant.parse(dbValue.toString());
        }
        if (target == OffsetDateTime.class) {
            if (dbValue instanceof OffsetDateTime odt) return odt;
            if (dbValue instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
            return OffsetDateTime.parse(dbValue.toString());
        }
        if (target.isEnum()) {
            return Enum.valueOf((Class<Enum>) target, dbValue.toString());
        }

        return dbValue;
    }
}
