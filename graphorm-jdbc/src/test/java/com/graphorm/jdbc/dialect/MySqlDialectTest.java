package com.graphorm.jdbc.dialect;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MySqlDialect Unit Tests")
class MySqlDialectTest {

    private MySqlDialect dialect;

    enum Status { ACTIVE, INACTIVE }

    @BeforeEach
    void setUp() {
        dialect = new MySqlDialect();
    }

    @Nested
    @DisplayName("Identifier Quoting Tests")
    class IdentifierQuotingTests {

        @Test
        @DisplayName("Should backtick quote simple table and column names")
        void shouldQuoteSimpleIdentifiers() {
            assertThat(dialect.quoteIdentifier("students")).isEqualTo("`students`");
            assertThat(dialect.quoteIdentifier("first_name")).isEqualTo("`first_name`");
            assertThat(dialect.quoteIdentifier("camelCaseColumn")).isEqualTo("`camelCaseColumn`");
        }

        @Test
        @DisplayName("Should preserve wildcard asterisk without quoting")
        void shouldNotQuoteAsterisk() {
            assertThat(dialect.quoteIdentifier("*")).isEqualTo("*");
        }

        @Test
        @DisplayName("Should quote qualified dotted identifiers correctly")
        void shouldQuoteDottedIdentifiers() {
            assertThat(dialect.quoteIdentifier("s.id")).isEqualTo("`s`.`id`");
            assertThat(dialect.quoteIdentifier("students.first_name")).isEqualTo("`students`.`first_name`");
            assertThat(dialect.quoteIdentifier("s.*")).isEqualTo("`s`.*");
        }

        @Test
        @DisplayName("Should avoid double-quoting already quoted identifiers")
        void shouldNotDoubleQuote() {
            assertThat(dialect.quoteIdentifier("`students`")).isEqualTo("`students`");
            assertThat(dialect.quoteIdentifier("`s`.`id`")).isEqualTo("`s`.`id`");
        }

        @Test
        @DisplayName("Should escape backticks inside identifiers")
        void shouldEscapeInternalBackticks() {
            assertThat(dialect.quoteIdentifier("foo`bar")).isEqualTo("`foo``bar`");
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
        @DisplayName("Should reject illegal control characters and semicolons")
        void shouldRejectIllegalCharacters() {
            assertThatThrownBy(() -> dialect.quoteIdentifier("drop table;"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> dialect.quoteIdentifier("col\u0000name"))
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
            assertThat(dialect.buildAggregateSql(AggregateType.SUM, "`amount`")).isEqualTo("SUM(`amount`)");
            assertThat(dialect.buildAggregateSql(AggregateType.AVG, "`gpa`")).isEqualTo("AVG(`gpa`)");
            assertThat(dialect.buildAggregateSql(AggregateType.MIN, "`score`")).isEqualTo("MIN(`score`)");
            assertThat(dialect.buildAggregateSql(AggregateType.MAX, "`score`")).isEqualTo("MAX(`score`)");
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
        @DisplayName("Should report not supporting RETURNING clause")
        void shouldNotSupportReturning() {
            assertThat(dialect.supportsReturning()).isFalse();
        }

        @Test
        @DisplayName("Should render empty string for renderReturningClause")
        void shouldRenderEmptyReturning() {
            assertThat(dialect.renderReturningClause(null)).isEmpty();
            assertThat(dialect.renderReturningClause(List.of())).isEmpty();
            assertThat(dialect.renderReturningClause(List.of("id", "name"))).isEmpty();
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
        }

        @Test
        @DisplayName("Should convert UUID to String for MySQL binding")
        void shouldConvertUuidToDatabase() {
            FieldMetadata field = FieldMetadata.builder("id", UUID.class).build();
            UUID uuid = UUID.randomUUID();
            assertThat(dialect.convertToDatabaseValue(uuid, field)).isEqualTo(uuid.toString());
        }

        @Test
        @DisplayName("Should preserve modern Java Date/Time objects for MySQL JDBC binding")
        void shouldPreserveDateTimeForDatabase() {
            FieldMetadata field = FieldMetadata.builder("birthDate", LocalDate.class).build();
            LocalDate localDate = LocalDate.of(2000, 5, 20);
            assertThat(dialect.convertToDatabaseValue(localDate, field)).isEqualTo(localDate);

            FieldMetadata timeField = FieldMetadata.builder("classTime", LocalTime.class).build();
            LocalTime localTime = LocalTime.of(14, 30, 0);
            assertThat(dialect.convertToDatabaseValue(localTime, timeField)).isEqualTo(localTime);

            FieldMetadata ldtField = FieldMetadata.builder("created", LocalDateTime.class).build();
            LocalDateTime ldt = LocalDateTime.of(2024, 1, 15, 10, 20, 30);
            assertThat(dialect.convertToDatabaseValue(ldt, ldtField)).isEqualTo(ldt);

            FieldMetadata instField = FieldMetadata.builder("updated", Instant.class).build();
            Instant instant = Instant.parse("2024-01-15T10:20:30Z");
            assertThat(dialect.convertToDatabaseValue(instant, instField)).isEqualTo(instant);
        }

        @Test
        @DisplayName("Should convert database values back to Java domain types")
        void shouldConvertFromDatabase() {
            FieldMetadata uuidField = FieldMetadata.builder("id", UUID.class).build();
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
                    .isEqualTo("SELECT `id`, `name` FROM `students` WHERE `status` = ? LIMIT ? OFFSET ?");
            assertThat(boundSql.parameters()).containsExactly("ACTIVE", 10, 0);
        }

        @Test
        @DisplayName("Should build insert SQL via builder without RETURNING")
        void shouldBuildInsert() {
            InsertQuery query = InsertQuery.into("students")
                    .value("name", "Alice")
                    .value("email", "alice@example.com")
                    .build();

            BoundSql boundSql = dialect.buildInsertSql(query);
            assertThat(boundSql.sql())
                    .isEqualTo("INSERT INTO `students` (`name`, `email`) VALUES (?, ?)");
            assertThat(boundSql.sql()).doesNotContain("RETURNING");
            assertThat(boundSql.parameters()).containsExactly("Alice", "alice@example.com");
        }

        @Test
        @DisplayName("Should build update SQL via builder without RETURNING")
        void shouldBuildUpdate() {
            UpdateQuery query = UpdateQuery.table("students")
                    .set("name", "Alice Updated")
                    .where(WherePredicate.eq("id", 1))
                    .build();

            BoundSql boundSql = dialect.buildUpdateSql(query);
            assertThat(boundSql.sql())
                    .isEqualTo("UPDATE `students` SET `name` = ? WHERE `id` = ?");
            assertThat(boundSql.sql()).doesNotContain("RETURNING");
            assertThat(boundSql.parameters()).containsExactly("Alice Updated", 1);
        }

        @Test
        @DisplayName("Should build delete SQL via builder")
        void shouldBuildDelete() {
            DeleteQuery query = DeleteQuery.from("students")
                    .where(WherePredicate.eq("id", 1))
                    .build();

            BoundSql boundSql = dialect.buildDeleteSql(query);
            assertThat(boundSql.sql())
                    .isEqualTo("DELETE FROM `students` WHERE `id` = ?");
            assertThat(boundSql.parameters()).containsExactly(1);
        }
    }
}
