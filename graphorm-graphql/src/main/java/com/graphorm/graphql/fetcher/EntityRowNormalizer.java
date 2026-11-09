package com.graphorm.graphql.fetcher;

import com.graphorm.jdbc.dialect.PostgreSqlDialect;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalizes raw JDBC result rows into field-aligned maps suitable for GraphQL execution,
 * and coerces GraphQL argument values to match schema field types.
 */
public final class EntityRowNormalizer {

    private EntityRowNormalizer() {}

    /**
     * Normalizes a database row map for the specified entity metadata.
     *
     * @param rawRow the raw row from JDBC execution
     * @param entity the target entity metadata
     * @return normalized map
     */
    public static Map<String, Object> normalize(Map<String, Object> rawRow, EntityMetadata entity) {
        if (rawRow == null) {
            return null;
        }

        Map<String, Object> normalized = new LinkedHashMap<>();

        // 1. Copy raw row with normalized values (converting SQL temporal types to Java time if needed)
        for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
            normalized.put(entry.getKey(), convertValue(entry.getValue()));
        }

        // 2. Ensure each declared field name is mapped
        if (entity != null) {
            for (FieldMetadata field : entity.fields()) {
                String fieldName = field.name();
                String colName = field.dbColumnName();

                if (!normalized.containsKey(fieldName)) {
                    if (colName != null && normalized.containsKey(colName)) {
                        normalized.put(fieldName, normalized.get(colName));
                    } else {
                        // Check case-insensitive match
                        for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
                            if (entry.getKey().equalsIgnoreCase(fieldName)
                                    || (colName != null && entry.getKey().equalsIgnoreCase(colName))) {
                                normalized.put(fieldName, convertValue(entry.getValue()));
                                break;
                            }
                        }
                    }
                }
            }
        }

        return normalized;
    }

    /**
     * Coerces a GraphQL argument value into the Java type expected by the field metadata.
     *
     * @param value the raw GraphQL value
     * @param field the field metadata
     * @return typed value suitable for JDBC PreparedStatement binding
     */
    public static Object coerceToFieldType(Object value, FieldMetadata field) {
        if (value == null || field == null) {
            return value;
        }
        Class<?> javaType = field.javaType();
        String typeName = field.typeName();

        if (value instanceof String str) {
            if (isIntegerType(javaType, typeName)) {
                try {
                    return Integer.parseInt(str.trim());
                } catch (NumberFormatException ignored) {}
            }
            if (isLongType(javaType, typeName)) {
                try {
                    return Long.parseLong(str.trim());
                } catch (NumberFormatException ignored) {}
            }
            if (isDoubleType(javaType, typeName)) {
                try {
                    return Double.parseDouble(str.trim());
                } catch (NumberFormatException ignored) {}
            }
            if (isBooleanType(javaType, typeName)) {
                return Boolean.parseBoolean(str.trim());
            }
        } else if (value instanceof Number num) {
            if (isIntegerType(javaType, typeName)) {
                return num.intValue();
            }
            if (isLongType(javaType, typeName)) {
                return num.longValue();
            }
            if (isDoubleType(javaType, typeName)) {
                return num.doubleValue();
            }
        }
        return value;
    }

    private static boolean isIntegerType(Class<?> javaType, String typeName) {
        return javaType == Integer.class || int.class.equals(javaType)
                || "Int".equalsIgnoreCase(typeName) || "Integer".equalsIgnoreCase(typeName);
    }

    private static boolean isLongType(Class<?> javaType, String typeName) {
        return javaType == Long.class || long.class.equals(javaType)
                || "BigInt".equalsIgnoreCase(typeName) || "Long".equalsIgnoreCase(typeName) || "ID".equalsIgnoreCase(typeName);
    }

    private static boolean isDoubleType(Class<?> javaType, String typeName) {
        return javaType == Double.class || double.class.equals(javaType) || javaType == Float.class || float.class.equals(javaType)
                || "Float".equalsIgnoreCase(typeName) || "Double".equalsIgnoreCase(typeName) || "Decimal".equalsIgnoreCase(typeName);
    }

    private static boolean isBooleanType(Class<?> javaType, String typeName) {
        return javaType == Boolean.class || boolean.class.equals(javaType)
                || "Boolean".equalsIgnoreCase(typeName);
    }

    private static Object convertValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (val instanceof Date d) {
            return d.toLocalDate().toString();
        }
        if (val instanceof Time t) {
            return t.toLocalTime().toString();
        }
        return val;
    }
}
