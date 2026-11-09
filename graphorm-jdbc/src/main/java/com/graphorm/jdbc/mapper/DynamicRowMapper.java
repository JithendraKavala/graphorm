package com.graphorm.jdbc.mapper;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.jdbc.exception.QueryExecutionException;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dynamic row mapper converting JDBC {@link ResultSet} rows into generic {@link Map Maps},
 * Java 21 immutable records, or entity-conforming structures with scalar type conversions.
 * Hardened for Virtual Threads (Project Loom) using {@link ReentrantLock} to avoid carrier thread pinning.
 */
public final class DynamicRowMapper {

    private DynamicRowMapper() {}

    private record CachedRecordMetadata<T>(
            Constructor<T> constructor,
            RecordComponent[] components,
            String[] normalizedNames,
            String[] snakeCaseNormalizedNames
    ) {}

    private static final Map<Class<?>, CachedRecordMetadata<?>> RECORD_METADATA_CACHE = new HashMap<>();
    private static final ReentrantLock CACHE_LOCK = new ReentrantLock();

    @SuppressWarnings("unchecked")
    private static <T> CachedRecordMetadata<T> getRecordMetadata(Class<T> recordClass) {
        CACHE_LOCK.lock();
        try {
            CachedRecordMetadata<?> existing = RECORD_METADATA_CACHE.get(recordClass);
            if (existing != null) {
                return (CachedRecordMetadata<T>) existing;
            }
            CachedRecordMetadata<T> created = buildRecordMetadata(recordClass);
            RECORD_METADATA_CACHE.put(recordClass, created);
            return created;
        } finally {
            CACHE_LOCK.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> CachedRecordMetadata<T> buildRecordMetadata(Class<T> cls) {
        RecordComponent[] components = cls.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        String[] normalizedNames = new String[components.length];
        String[] snakeCaseNormalizedNames = new String[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            String compName = components[i].getName();
            normalizedNames[i] = normalizeKey(compName);
            snakeCaseNormalizedNames[i] = normalizeKey(toSnakeCase(compName));
        }
        try {
            Constructor<T> ctor = (Constructor<T>) cls.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return new CachedRecordMetadata<>(ctor, components, normalizedNames, snakeCaseNormalizedNames);
        } catch (NoSuchMethodException e) {
            throw new QueryExecutionException("Failed to find canonical constructor for record: " + cls.getName(), e);
        }
    }

    /**
     * Maps the current row of the given {@link ResultSet} into an ordered {@link Map} of column
     * labels to values.
     *
     * @param rs the ResultSet positioned at a valid row
     * @return map of column labels (case-preserved) to converted values
     * @throws SQLException if a JDBC error occurs
     */
    public static Map<String, Object> mapRowToMap(ResultSet rs) throws SQLException {
        Objects.requireNonNull(rs, "ResultSet must not be null");
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(columnCount);

        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = meta.getColumnName(i);
            }
            Object value = rs.getObject(i);
            row.put(label, value);
        }
        return row;
    }

    /**
     * Maps an in-memory row map into an immutable Java 21 record instance using cached constructor metadata.
     *
     * @param rowMap      the row map of column names to values
     * @param recordClass the target record class
     * @param <T>         the record type
     * @return the instantiated and populated record
     */
    public static <T> T mapMapToRecord(Map<String, Object> rowMap, Class<T> recordClass) {
        Objects.requireNonNull(rowMap, "Row map must not be null");
        Objects.requireNonNull(recordClass, "Record class must not be null");

        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("Target class must be a Java record: " + recordClass.getName());
        }

        CachedRecordMetadata<T> metadata = getRecordMetadata(recordClass);
        RecordComponent[] components = metadata.components();
        Object[] args = new Object[components.length];

        // Build case-insensitive lookup
        Map<String, Object> lookup = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
            lookup.put(normalizeKey(entry.getKey()), entry.getValue());
        }

        for (int i = 0; i < components.length; i++) {
            RecordComponent comp = components[i];
            Object rawValue = lookup.get(metadata.normalizedNames()[i]);
            if (rawValue == null && !lookup.containsKey(metadata.normalizedNames()[i])) {
                rawValue = lookup.get(metadata.snakeCaseNormalizedNames()[i]);
            }
            if (rawValue == null && !lookup.containsKey(metadata.normalizedNames()[i])) {
                String normName = metadata.normalizedNames()[i];
                for (Map.Entry<String, Object> entry : lookup.entrySet()) {
                    String k = entry.getKey();
                    if (k.endsWith(normName)) {
                        rawValue = entry.getValue();
                        break;
                    }
                }
            }
            args[i] = convertValue(rawValue, comp.getType());
        }

        try {
            return metadata.constructor().newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new QueryExecutionException("Failed to instantiate record: " + recordClass.getName(), e);
        }
    }

    /**
     * Maps the current row of the given {@link ResultSet} into an immutable Java 21 record instance.
     *
     * @param rs          the ResultSet positioned at a valid row
     * @param recordClass the target record class
     * @param <T>         the record type
     * @return the instantiated and populated record
     * @throws SQLException if a JDBC error occurs
     */
    public static <T> T mapRowToRecord(ResultSet rs, Class<T> recordClass) throws SQLException {
        Objects.requireNonNull(rs, "ResultSet must not be null");
        return mapMapToRecord(mapRowToMap(rs), recordClass);
    }

    /**
     * Maps the current row using schema {@link EntityMetadata} into an ordered {@link Map}.
     *
     * @param rs     the ResultSet positioned at a valid row
     * @param entity the entity metadata
     * @return the mapped field values
     * @throws SQLException if a JDBC error occurs
     */
    public static Map<String, Object> mapRowToEntity(ResultSet rs, EntityMetadata entity) throws SQLException {
        Objects.requireNonNull(rs, "ResultSet must not be null");
        Objects.requireNonNull(entity, "Entity metadata must not be null");

        Map<String, Object> rowMap = mapRowToMap(rs);
        Map<String, Object> lookup = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
            lookup.put(normalizeKey(entry.getKey()), entry.getValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldMetadata field : entity.fields()) {
            if (field.isRelation()) {
                continue;
            }
            String fieldName = field.name();
            String colName = field.dbColumnName();

            Object rawValue = lookup.get(normalizeKey(fieldName));
            if (rawValue == null && !lookup.containsKey(normalizeKey(fieldName))) {
                rawValue = lookup.get(normalizeKey(colName));
            }

            Object converted = convertValue(rawValue, field.javaType());
            result.put(fieldName, converted);
        }
        return result;
    }

    /**
     * Creates a {@link RowMapper} that maps each row to a {@link Map}.
     *
     * @return a RowMapper for maps
     */
    public static RowMapper<Map<String, Object>> mapToMap() {
        return (rs, rowNum) -> mapRowToMap(rs);
    }

    /**
     * Creates a {@link RowMapper} that maps each row to the specified record class.
     *
     * @param recordClass the target record class
     * @param <T>         the record type
     * @return a RowMapper for records
     */
    public static <T> RowMapper<T> mapToRecord(Class<T> recordClass) {
        return (rs, rowNum) -> mapRowToRecord(rs, recordClass);
    }

    /**
     * Creates a {@link RowMapper} using schema {@link EntityMetadata}.
     *
     * @param entity the entity metadata
     * @return a RowMapper for entities
     */
    public static RowMapper<Map<String, Object>> mapToEntityMapper(EntityMetadata entity) {
        return (rs, rowNum) -> mapRowToEntity(rs, entity);
    }

    /**
     * Converts a raw database value to the specified target Java type.
     *
     * @param value      the raw value from ResultSet
     * @param targetType the desired Java class
     * @return the converted value
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType != null && targetType.isPrimitive()) {
                return defaultPrimitiveValue(targetType);
            }
            return null;
        }

        if (targetType == null || targetType == Object.class || targetType.isInstance(value)) {
            return value;
        }

        if (targetType == String.class) {
            return value.toString();
        }

        // Numeric conversions
        if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
        }
        if (targetType == Float.class || targetType == float.class) {
            return value instanceof Number n ? n.floatValue() : Float.parseFloat(value.toString());
        }
        if (targetType == Short.class || targetType == short.class) {
            return value instanceof Number n ? n.shortValue() : Short.parseShort(value.toString());
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return value instanceof Number n ? n.byteValue() : Byte.parseByte(value.toString());
        }
        if (targetType == BigDecimal.class) {
            if (value instanceof BigDecimal bd) return bd;
            if (value instanceof Long l) return BigDecimal.valueOf(l);
            if (value instanceof Integer i) return BigDecimal.valueOf(i);
            return new BigDecimal(value.toString());
        }
        if (targetType == BigInteger.class) {
            if (value instanceof BigInteger bi) return bi;
            if (value instanceof Long l) return BigInteger.valueOf(l);
            if (value instanceof Integer i) return BigInteger.valueOf(i);
            if (value instanceof BigDecimal bd) return bd.toBigInteger();
            return new BigInteger(value.toString());
        }

        // Boolean
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean b) return b;
            if (value instanceof Number n) return n.intValue() != 0;
            return Boolean.parseBoolean(value.toString());
        }

        // UUID
        if (targetType == UUID.class) {
            if (value instanceof UUID u) return u;
            return UUID.fromString(value.toString());
        }

        // Date & Time
        if (targetType == LocalDate.class) {
            if (value instanceof Date d) return d.toLocalDate();
            if (value instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
            return LocalDate.parse(value.toString());
        }
        if (targetType == LocalTime.class) {
            if (value instanceof Time t) return t.toLocalTime();
            return LocalTime.parse(value.toString());
        }
        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp ts) return ts.toLocalDateTime();
            return LocalDateTime.parse(value.toString());
        }
        if (targetType == Instant.class) {
            if (value instanceof Timestamp ts) return ts.toInstant();
            if (value instanceof OffsetDateTime odt) return odt.toInstant();
            return Instant.parse(value.toString());
        }
        if (targetType == OffsetDateTime.class) {
            if (value instanceof OffsetDateTime odt) return odt;
            if (value instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
            return OffsetDateTime.parse(value.toString());
        }

        // Enum
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, value.toString());
        }

        return value;
    }

    private static Object defaultPrimitiveValue(Class<?> primitiveType) {
        if (primitiveType == int.class) return 0;
        if (primitiveType == long.class) return 0L;
        if (primitiveType == boolean.class) return false;
        if (primitiveType == double.class) return 0.0d;
        if (primitiveType == float.class) return 0.0f;
        if (primitiveType == short.class) return (short) 0;
        if (primitiveType == byte.class) return (byte) 0;
        if (primitiveType == char.class) return '\0';
        return null;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase().replace("_", "");
    }

    private static String toSnakeCase(String camel) {
        if (camel == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
