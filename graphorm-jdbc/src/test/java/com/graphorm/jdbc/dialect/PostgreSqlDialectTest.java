package com.graphorm.jdbc.dialect;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostgreSqlDialect Unit Tests")
class PostgreSqlDialectTest {

    private PostgreSqlDialect dialect;

    enum Status { ACTIVE, INACTIVE }

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();
    }

    @Nested
    @DisplayName("Identifier Quoting Tests")
    class IdentifierQuotingTests {

        @Test
        @DisplayName("Should double quote simple table and column names")
        void shouldQuoteSimpleIdentifiers() {
            assertThat(dialect.quoteIdentifier("students")).isEqualTo("\"students\"");
            assertThat(dialect.quoteIdentifier("first_name")).isEqualTo("\"first_name\"");
            assertThat(dialect.quoteIdentifier("camelCaseColumn")).isEqualTo("\"camelCaseColumn\"");
        }

        @Test
        @DisplayName("Should preserve wildcard asterisk without quoting")
        void shouldNotQuoteAsterisk() {
            assertThat(dialect.quoteIdentifier("*")).isEqualTo("*");
        }

        @Test
        @DisplayName("Should quote qualified dotted identifiers correctly")
        void shouldQuoteDottedIdentifiers() {
            assertThat(dialect.quoteIdentifier("s.id")).isEqualTo("\"s\".\"id\"");
            assertThat(dialect.quoteIdentifier("students.first_name")).isEqualTo("\"students\".\"first_name\"");
            assertThat(dialect.quoteIdentifier("s.*")).isEqualTo("\"s\".*");
        }

        @Test
        @DisplayName("Should avoid double-quoting already quoted identifiers")
        void shouldNotDoubleQuote() {
            assertThat(dialect.quoteIdentifier("\"students\"")).isEqualTo("\"students\"");
            assertThat(dialect.quoteIdentifier("\"s\".\"id\"")).isEqualTo("\"s\".\"id\"");
        }

        @Test
        @DisplayName("Should escape double quotes inside identifiers")
        void shouldEscapeInternalDoubleQuotes() {
            assertThat(dialect.quoteIdentifier("foo\"bar")).isEqualTo("\"foo\"\"bar\"");
        }

        @Test
        @DisplayName("Should throw on null or blank identifier")
        void shouldRejectNullOrBlankIdentifier() {
            assertThatThrownBy(() -> dialect.quoteIdentifier(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> dialect.quoteIdentifier(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> dialect.quoteIdentifier("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("SEC-05: Should reject syntax delimiters and safely quote identifiers with spaces")
        void shouldSanitizeIdentifiersSecurely() {
            assertThat(dialect.quoteIdentifier("first name")).isEqualTo("\"first name\"");
            assertThat(dialect.quoteIdentifier("COUNT(e.id)")).isEqualTo("COUNT(e.id)");
            assertThat(dialect.quoteIdentifier("LOWER(email)")).isEqualTo("LOWER(email)");

            assertThatThrownBy(() -> dialect.quoteIdentifier("col; DROP TABLE students; --"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> dialect.quoteIdentifier("students--"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> dialect.quoteIdentifier("foo/*comment*/bar"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }


    @Nested
    @DisplayName("Limit / Offset Pagination Tests")
    class LimitOffsetTests {

        @Test
        @DisplayName("Should format LIMIT and OFFSET clauses with positional '?' placeholders")
        void shouldFormatLimitAndOffset() {
            assertThat(dialect.formatLimitOffset(10, 20)).isEqualTo("LIMIT ? OFFSET ?");
            assertThat(dialect.formatLimitOffset(10, null)).isEqualTo("LIMIT ?");
            assertThat(dialect.formatLimitOffset(null, 20)).isEqualTo("OFFSET ?");
            assertThat(dialect.formatLimitOffset(null, null)).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Aggregation Functions Tests")
    class AggregationTests {

        @Test
        @DisplayName("Should format aggregation SQL expressions")
        void shouldFormatAggregates() {
            assertThat(dialect.buildAggregateSql(AggregateType.COUNT, "*")).isEqualTo("COUNT(*)");
            assertThat(dialect.buildAggregateSql(AggregateType.SUM, "\"amount\"")).isEqualTo("SUM(\"amount\")");
            assertThat(dialect.buildAggregateSql(AggregateType.AVG, "\"gpa\"")).isEqualTo("AVG(\"gpa\")");
            assertThat(dialect.buildAggregateSql(AggregateType.MIN, "\"score\"")).isEqualTo("MIN(\"score\")");
            assertThat(dialect.buildAggregateSql(AggregateType.MAX, "\"score\"")).isEqualTo("MAX(\"score\")");
        }

        @Test
        @DisplayName("Should support aggregateFunction alias")
        void shouldSupportAlias() {
            assertThat(dialect.aggregateFunction(AggregateType.COUNT, "1")).isEqualTo("COUNT(1)");
        }
    }

    @Nested
    @DisplayName("Returning Clause Tests")
    class ReturningClauseTests {

        @Test
        @DisplayName("Should report supporting RETURNING clause")
        void shouldSupportReturning() {
            assertThat(dialect.supportsReturning()).isTrue();
        }

        @Test
        @DisplayName("Should render RETURNING * for null, empty or wildcard")
        void shouldRenderReturningAll() {
            assertThat(dialect.renderReturningClause(null)).isEqualTo("RETURNING *");
            assertThat(dialect.renderReturningClause(List.of())).isEqualTo("RETURNING *");
            assertThat(dialect.renderReturningClause(List.of("*"))).isEqualTo("RETURNING *");
        }

        @Test
        @DisplayName("Should render RETURNING with quoted columns")
        void shouldRenderReturningColumns() {
            assertThat(dialect.renderReturningClause(List.of("id", "name")))
                    .isEqualTo("RETURNING \"id\", \"name\"");
        }
    }

    @Nested
    @DisplayName("Type Conversion Tests")
    class TypeConversionTests {

        @Test
        @DisplayName("Should convert Java enum to name for database binding")
        void shouldConvertEnumToDatabase() {
            FieldMetadata field = FieldMetadata.builder("status", Status.class).build();
            assertThat(dialect.convertToDatabaseValue(Status.ACTIVE, field)).isEqualTo("ACTIVE");
            assertThat(dialect.convertToDatabaseValue(null, field)).isNull();
        }

        @Test
        @DisplayName("Should convert java.util.Date to java.sql.Timestamp for database")
        void shouldConvertDateToTimestamp() {
            java.util.Date date = new java.util.Date(1700000000000L);
            Object converted = dialect.convertToDatabaseValue(date, null);
            assertThat(converted).isInstanceOf(Timestamp.class);
            assertThat(((Timestamp) converted).getTime()).isEqualTo(1700000000000L);
        }

        @Test
        @DisplayName("Should convert database values to target Java types")
        void shouldConvertFromDatabaseValues() {
            FieldMetadata strField = FieldMetadata.builder("name", String.class).build();
            assertThat(dialect.convertFromDatabaseValue("Alice", strField)).isEqualTo("Alice");

            FieldMetadata intField = FieldMetadata.builder("age", Integer.class).build();
            assertThat(dialect.convertFromDatabaseValue(25L, intField)).isEqualTo(25);

            FieldMetadata longField = FieldMetadata.builder("id", Long.class).build();
            assertThat(dialect.convertFromDatabaseValue(100, longField)).isEqualTo(100L);

            FieldMetadata boolField = FieldMetadata.builder("active", Boolean.class).build();
            assertThat(dialect.convertFromDatabaseValue(1, boolField)).isEqualTo(Boolean.TRUE);
            assertThat(dialect.convertFromDatabaseValue(0, boolField)).isEqualTo(Boolean.FALSE);

            FieldMetadata bdField = FieldMetadata.builder("price", BigDecimal.class).build();
            assertThat(dialect.convertFromDatabaseValue(19.99, bdField)).isEqualTo(BigDecimal.valueOf(19.99));

            FieldMetadata biField = FieldMetadata.builder("big", BigInteger.class).build();
            assertThat(dialect.convertFromDatabaseValue(999999L, biField)).isEqualTo(BigInteger.valueOf(999999L));

            FieldMetadata uuidField = FieldMetadata.builder("uuid", UUID.class).build();
            UUID uuid = UUID.randomUUID();
            assertThat(dialect.convertFromDatabaseValue(uuid.toString(), uuidField)).isEqualTo(uuid);

            FieldMetadata dateField = FieldMetadata.builder("dob", LocalDate.class).build();
            Date sqlDate = Date.valueOf("2024-01-15");
            assertThat(dialect.convertFromDatabaseValue(sqlDate, dateField)).isEqualTo(LocalDate.of(2024, 1, 15));

            FieldMetadata timeField = FieldMetadata.builder("time", LocalTime.class).build();
            Time sqlTime = Time.valueOf("14:30:00");
            assertThat(dialect.convertFromDatabaseValue(sqlTime, timeField)).isEqualTo(LocalTime.of(14, 30, 0));

            FieldMetadata tsField = FieldMetadata.builder("created", LocalDateTime.class).build();
            Timestamp sqlTs = Timestamp.valueOf("2024-01-15 10:20:30");
            assertThat(dialect.convertFromDatabaseValue(sqlTs, tsField))
                    .isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 20, 30));

            FieldMetadata instantField = FieldMetadata.builder("inst", Instant.class).build();
            assertThat(dialect.convertFromDatabaseValue(sqlTs, instantField))
                    .isEqualTo(sqlTs.toInstant());

            FieldMetadata enumField = FieldMetadata.builder("status", Status.class).build();
            assertThat(dialect.convertFromDatabaseValue("ACTIVE", enumField)).isEqualTo(Status.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Query Delegation Tests")
    class QueryDelegationTests {

        @Test
        @DisplayName("Should build select SQL via builder")
        void shouldBuildSelect() {
            SelectQuery query = SelectQuery.from("students")
                    .column("id")
                    .column("name")
                    .where(WherePredicate.eq("status", "ACTIVE"))
                    .limit(10)
                    .offset(0)
                    .build();

            BoundSql boundSql = dialect.buildSelectSql(query);
            assertThat(boundSql.sql())
                    .isEqualTo("SELECT \"id\", \"name\" FROM \"students\" WHERE \"status\" = ? LIMIT ? OFFSET ?");
            assertThat(boundSql.parameters()).containsExactly("ACTIVE", 10, 0);
        }

        @Test
        @DisplayName("Should build insert SQL via builder")
        void shouldBuildInsert() {
            InsertQuery query = InsertQuery.into("students")
                    .value("name", "Alice")
                    .value("email", "alice@example.com")
                    .build();

            BoundSql boundSql = dialect.buildInsertSql(query);
            assertThat(boundSql.sql())
                    .isEqualTo("INSERT INTO \"students\" (\"name\", \"email\") VALUES (?, ?)");
            assertThat(boundSql.parameters()).containsExactly("Alice", "alice@example.com");
        }

        @Test
        @DisplayName("Should build update SQL via builder")
        void shouldBuildUpdate() {
            UpdateQuery query = UpdateQuery.table("students")
                    .set("name", "Alice Updated")
                    .where(WherePredicate.eq("id", 1L))
                    .build();

            BoundSql boundSql = dialect.buildUpdateSql(query);
            assertThat(boundSql.sql())
                    .isEqualTo("UPDATE \"students\" SET \"name\" = ? WHERE \"id\" = ?");
            assertThat(boundSql.parameters()).containsExactly("Alice Updated", 1L);
        }

        @Test
        @DisplayName("Should build delete SQL via builder")
        void shouldBuildDelete() {
            DeleteQuery query = DeleteQuery.from("students")
                    .where(WherePredicate.eq("id", 1L))
                    .build();

            BoundSql boundSql = dialect.buildDeleteSql(query);
            assertThat(boundSql.sql())
                    .isEqualTo("DELETE FROM \"students\" WHERE \"id\" = ?");
            assertThat(boundSql.parameters()).containsExactly(1L);
        }
            @Test
        @DisplayName("DB-03: Should preserve BigDecimal numeric precision without doubleValue truncation")
        void shouldPreserveBigDecimalPrecision() {
            FieldMetadata bdField = FieldMetadata.builder("amount", BigDecimal.class).build();
            String preciseVal = "12345678901234567890.1234567890";
            assertThat(dialect.convertFromDatabaseValue(preciseVal, bdField))
                    .isEqualTo(new BigDecimal(preciseVal));
            assertThat(dialect.convertFromDatabaseValue(100L, bdField))
                    .isEqualTo(BigDecimal.valueOf(100L));
            assertThat(dialect.convertFromDatabaseValue(42, bdField))
                    .isEqualTo(BigDecimal.valueOf(42));
        }
    }
}
