package com.graphorm.jdbc.mapper;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DynamicRowMapper Unit Tests")
class DynamicRowMapperTest {

    enum UserRole { ADMIN, USER, GUEST }

    record StudentRecord(
            Long id,
            String firstName,
            String email,
            int age,
            double gpa,
            boolean active,
            UUID accountId,
            LocalDate birthDate,
            LocalDateTime createdAt,
            UserRole role
    ) {}

    record PrimitiveRecord(
            int i,
            long l,
            boolean b,
            double d,
            float f,
            short s,
            byte by,
            char c
    ) {}

    @Nested
    @DisplayName("Map and Record Mapping Tests")
    class MappingTests {

        @Test
        @DisplayName("Should map ResultSet row to a generic Map")
        void shouldMapRowToMap() throws SQLException {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", 101L);
            data.put("name", "Alice");
            data.put("is_active", true);

            ResultSet rs = createMockResultSet(data);
            Map<String, Object> result = DynamicRowMapper.mapRowToMap(rs);

            assertThat(result)
                    .containsEntry("id", 101L)
                    .containsEntry("name", "Alice")
                    .containsEntry("is_active", true);
        }

        @Test
        @DisplayName("Should map ResultSet row to a Java 21 Record with camelCase and snake_case matching")
        void shouldMapRowToRecord() throws SQLException {
            UUID accountId = UUID.randomUUID();
            LocalDate dob = LocalDate.of(2000, 5, 20);
            LocalDateTime now = LocalDateTime.of(2024, 1, 15, 12, 30, 45);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", 1L);
            data.put("first_name", "Bob");
            data.put("email", "bob@example.com");
            data.put("age", 22);
            data.put("gpa", 3.85);
            data.put("active", 1); // numeric boolean in DB
            data.put("account_id", accountId.toString()); // String in DB, UUID in record
            data.put("birth_date", Date.valueOf(dob)); // SQL Date in DB, LocalDate in record
            data.put("created_at", Timestamp.valueOf(now)); // Timestamp in DB, LocalDateTime in record
            data.put("role", "ADMIN");

            ResultSet rs = createMockResultSet(data);
            StudentRecord record = DynamicRowMapper.mapRowToRecord(rs, StudentRecord.class);

            assertThat(record.id()).isEqualTo(1L);
            assertThat(record.firstName()).isEqualTo("Bob");
            assertThat(record.email()).isEqualTo("bob@example.com");
            assertThat(record.age()).isEqualTo(22);
            assertThat(record.gpa()).isEqualTo(3.85);
            assertThat(record.active()).isTrue();
            assertThat(record.accountId()).isEqualTo(accountId);
            assertThat(record.birthDate()).isEqualTo(dob);
            assertThat(record.createdAt()).isEqualTo(now);
            assertThat(record.role()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        @DisplayName("Should handle primitive nulls by assigning default values")
        void shouldHandlePrimitiveDefaults() throws SQLException {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("i", null);
            data.put("l", null);
            data.put("b", null);
            data.put("d", null);
            data.put("f", null);
            data.put("s", null);
            data.put("by", null);
            data.put("c", null);

            ResultSet rs = createMockResultSet(data);
            PrimitiveRecord record = DynamicRowMapper.mapRowToRecord(rs, PrimitiveRecord.class);

            assertThat(record.i()).isZero();
            assertThat(record.l()).isZero();
            assertThat(record.b()).isFalse();
            assertThat(record.d()).isZero();
            assertThat(record.f()).isZero();
            assertThat(record.s()).isZero();
            assertThat(record.by()).isZero();
            assertThat(record.c()).isEqualTo('\0');
        }

        @Test
        @DisplayName("Should reject non-record classes in mapRowToRecord")
        void shouldRejectNonRecord() {
            ResultSet rs = createMockResultSet(Map.of());
            assertThatThrownBy(() -> DynamicRowMapper.mapRowToRecord(rs, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be a Java record");
        }

        @Test
        @DisplayName("Should map row using EntityMetadata")
        void shouldMapRowUsingEntityMetadata() throws SQLException {
            ModelMetadata model = ModelMetadata.builder("Course")
                    .tableName("courses")
                    .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                    .addField(FieldMetadata.builder("title", String.class).dbColumnName("course_title").build())
                    .addField(FieldMetadata.builder("credits", Integer.class).build())
                    .build();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", 42L);
            data.put("course_title", "Database Systems");
            data.put("credits", 3);

            ResultSet rs = createMockResultSet(data);
            Map<String, Object> entityMap = DynamicRowMapper.mapRowToEntity(rs, model);

            assertThat(entityMap)
                    .containsEntry("id", 42L)
                    .containsEntry("title", "Database Systems")
                    .containsEntry("credits", 3);
        }
    }

    @Nested
    @DisplayName("Scalar Type Conversions Tests")
    class TypeConversionTests {

        @Test
        @DisplayName("Should convert numbers accurately across types")
        void shouldConvertNumbers() {
            assertThat(DynamicRowMapper.convertValue(123, Long.class)).isEqualTo(123L);
            assertThat(DynamicRowMapper.convertValue(123L, Integer.class)).isEqualTo(123);
            assertThat(DynamicRowMapper.convertValue("123", Integer.class)).isEqualTo(123);
            assertThat(DynamicRowMapper.convertValue(12.5, Float.class)).isEqualTo(12.5f);
            assertThat(DynamicRowMapper.convertValue(12.5f, Double.class)).isEqualTo(12.5);
            assertThat(DynamicRowMapper.convertValue("123.45", BigDecimal.class)).isEqualTo(new BigDecimal("123.45"));
            String highPrecision = "12345678901234567890.12345678901234567890";
            assertThat(DynamicRowMapper.convertValue(highPrecision, BigDecimal.class)).isEqualTo(new BigDecimal(highPrecision));
            assertThat(DynamicRowMapper.convertValue("9999999999", BigInteger.class)).isEqualTo(new BigInteger("9999999999"));
        }

        @Test
        @DisplayName("Should convert booleans from numbers, strings, and booleans")
        void shouldConvertBooleans() {
            assertThat(DynamicRowMapper.convertValue(1, Boolean.class)).isEqualTo(Boolean.TRUE);
            assertThat(DynamicRowMapper.convertValue(0, Boolean.class)).isEqualTo(Boolean.FALSE);
            assertThat(DynamicRowMapper.convertValue("true", Boolean.class)).isEqualTo(Boolean.TRUE);
            assertThat(DynamicRowMapper.convertValue("false", Boolean.class)).isEqualTo(Boolean.FALSE);
            assertThat(DynamicRowMapper.convertValue(true, boolean.class)).isEqualTo(true);
        }

        @Test
        @DisplayName("Should convert temporal types")
        void shouldConvertTemporal() {
            Date sqlDate = Date.valueOf("2024-03-01");
            assertThat(DynamicRowMapper.convertValue(sqlDate, LocalDate.class)).isEqualTo(LocalDate.of(2024, 3, 1));

            Time sqlTime = Time.valueOf("15:45:00");
            assertThat(DynamicRowMapper.convertValue(sqlTime, LocalTime.class)).isEqualTo(LocalTime.of(15, 45, 0));

            Timestamp sqlTs = Timestamp.valueOf("2024-03-01 15:45:00");
            assertThat(DynamicRowMapper.convertValue(sqlTs, LocalDateTime.class))
                    .isEqualTo(LocalDateTime.of(2024, 3, 1, 15, 45, 0));
            assertThat(DynamicRowMapper.convertValue(sqlTs, Instant.class))
                    .isEqualTo(sqlTs.toInstant());
        }

        @Test
        @DisplayName("Should convert UUID and Enum types")
        void shouldConvertUuidAndEnum() {
            UUID uuid = UUID.randomUUID();
            assertThat(DynamicRowMapper.convertValue(uuid.toString(), UUID.class)).isEqualTo(uuid);
            assertThat(DynamicRowMapper.convertValue("USER", UserRole.class)).isEqualTo(UserRole.USER);
        }
    }

    /**
     * Helper to create a proxy-based mock ResultSet for unit testing.
     */
    private static ResultSet createMockResultSet(Map<String, Object> columnData) {
        List<String> labels = new ArrayList<>(columnData.keySet());
        List<Object> values = new ArrayList<>(columnData.values());

        ResultSetMetaData metaData = (ResultSetMetaData) Proxy.newProxyInstance(
                DynamicRowMapperTest.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getColumnCount".equals(methodName)) {
                        return labels.size();
                    }
                    if ("getColumnLabel".equals(methodName) || "getColumnName".equals(methodName)) {
                        int index = (Integer) args[0] - 1;
                        return labels.get(index);
                    }
                    return null;
                }
        );

        return (ResultSet) Proxy.newProxyInstance(
                DynamicRowMapperTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getMetaData".equals(methodName)) {
                        return metaData;
                    }
                    if ("getObject".equals(methodName)) {
                        if (args[0] instanceof Integer idx) {
                            return values.get(idx - 1);
                        }
                        if (args[0] instanceof String colName) {
                            for (int i = 0; i < labels.size(); i++) {
                                if (labels.get(i).equalsIgnoreCase(colName)) {
                                    return values.get(i);
                                }
                            }
                            return null;
                        }
                    }
                    return null;
                }
        );
    }
}
