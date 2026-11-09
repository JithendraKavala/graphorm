package com.graphorm.jdbc.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Dialect Auto-Detection Unit Tests")
class DialectAutoDetectionTest {

    @Test
    @DisplayName("Should detect PostgreSQL from product names")
    void shouldDetectPostgreSql() {
        assertThat(SqlDialect.fromDatabaseProductName("PostgreSQL")).isInstanceOf(PostgreSqlDialect.class);
        assertThat(SqlDialect.fromDatabaseProductName("postgresql")).isInstanceOf(PostgreSqlDialect.class);
        assertThat(SqlDialect.fromDatabaseProductName("Postgres")).isInstanceOf(PostgreSqlDialect.class);
    }

    @Test
    @DisplayName("Should detect MySQL and MariaDB from product names")
    void shouldDetectMySql() {
        assertThat(SqlDialect.fromDatabaseProductName("MySQL")).isInstanceOf(MySqlDialect.class);
        assertThat(SqlDialect.fromDatabaseProductName("mysql")).isInstanceOf(MySqlDialect.class);
        assertThat(SqlDialect.fromDatabaseProductName("MariaDB")).isInstanceOf(MySqlDialect.class);
    }

    @Test
    @DisplayName("Should detect SQLite from product names")
    void shouldDetectSqlite() {
        assertThat(SqlDialect.fromDatabaseProductName("SQLite")).isInstanceOf(SqliteDialect.class);
        assertThat(SqlDialect.fromDatabaseProductName("sqlite")).isInstanceOf(SqliteDialect.class);
    }

    @Test
    @DisplayName("Should fallback to PostgreSQL for null or unknown database product names")
    void shouldFallbackToPostgreSql() {
        assertThat(SqlDialect.fromDatabaseProductName(null)).isInstanceOf(PostgreSqlDialect.class);
        assertThat(SqlDialect.fromDatabaseProductName("")).isInstanceOf(PostgreSqlDialect.class);
        assertThat(SqlDialect.fromDatabaseProductName("UnknownDB")).isInstanceOf(PostgreSqlDialect.class);
    }
}
