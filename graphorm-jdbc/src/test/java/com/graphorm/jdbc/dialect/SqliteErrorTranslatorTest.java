package com.graphorm.jdbc.dialect;

import com.graphorm.core.exception.CannotAcquireLockException;
import com.graphorm.core.exception.CheckConstraintViolationException;
import com.graphorm.core.exception.DataAccessException;
import com.graphorm.core.exception.DataIntegrityViolationException;
import com.graphorm.core.exception.DuplicateKeyException;
import com.graphorm.core.exception.ForeignKeyViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SqliteErrorTranslator Unit Tests")
class SqliteErrorTranslatorTest {

    private SqliteErrorTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new SqliteErrorTranslator();
    }

    @Test
    @DisplayName("Should translate UNIQUE constraint failed to DuplicateKeyException")
    void shouldTranslateUniqueConstraint() {
        SQLException ex = new SQLException("[SQLITE_CONSTRAINT_UNIQUE] A UNIQUE constraint failed (UNIQUE constraint failed: users.email)", null, 19);
        DataAccessException result = translator.translate("Insert user", "INSERT INTO \"users\"...", ex);

        assertThat(result).isInstanceOf(DuplicateKeyException.class);
        assertThat(result.getMessage()).contains("UNIQUE constraint failed");
        assertThat(result.getSql()).isEqualTo("INSERT INTO \"users\"...");
    }

    @Test
    @DisplayName("Should translate extended error codes 1555 and 2067 to DuplicateKeyException")
    void shouldTranslateExtendedUniqueConstraintCodes() {
        SQLException ex1555 = new SQLException("PRIMARY KEY failed", null, 1555);
        SQLException ex2067 = new SQLException("UNIQUE constraint failed", null, 2067);

        assertThat(translator.translate("Insert", "INSERT...", ex1555))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(translator.translate("Insert", "INSERT...", ex2067))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("Should translate FOREIGN KEY constraint failed to ForeignKeyViolationException")
    void shouldTranslateForeignKeyConstraint() {
        SQLException ex = new SQLException("[SQLITE_CONSTRAINT_FOREIGNKEY] A FOREIGN KEY constraint failed (FOREIGN KEY constraint failed)", null, 19);
        DataAccessException result = translator.translate("Insert student", "INSERT INTO \"students\"...", ex);

        assertThat(result).isInstanceOf(ForeignKeyViolationException.class);
        assertThat(result.getMessage()).contains("FOREIGN KEY constraint failed");
    }

    @Test
    @DisplayName("Should translate extended error code 787 to ForeignKeyViolationException")
    void shouldTranslateExtendedFkCode() {
        SQLException ex787 = new SQLException("FOREIGN KEY constraint failed", null, 787);
        assertThat(translator.translate("Insert", "INSERT...", ex787))
                .isInstanceOf(ForeignKeyViolationException.class);
    }

    @Test
    @DisplayName("Should translate NOT NULL constraint failed to DataIntegrityViolationException")
    void shouldTranslateNotNullConstraint() {
        SQLException ex = new SQLException("[SQLITE_CONSTRAINT_NOTNULL] A NOT NULL constraint failed (NOT NULL constraint failed: users.name)", null, 19);
        DataAccessException result = translator.translate("Insert user", "INSERT INTO \"users\"...", ex);

        assertThat(result).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(result.getMessage()).contains("NOT NULL constraint failed");
    }

    @Test
    @DisplayName("Should translate extended error code 1299 to DataIntegrityViolationException")
    void shouldTranslateExtendedNotNullCode() {
        SQLException ex1299 = new SQLException("NOT NULL constraint failed", null, 1299);
        assertThat(translator.translate("Insert", "INSERT...", ex1299))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should translate CHECK constraint failed to CheckConstraintViolationException")
    void shouldTranslateCheckConstraint() {
        SQLException ex = new SQLException("[SQLITE_CONSTRAINT_CHECK] A CHECK constraint failed (CHECK constraint failed: age >= 18)", null, 19);
        DataAccessException result = translator.translate("Insert user", "INSERT INTO \"users\"...", ex);

        assertThat(result).isInstanceOf(CheckConstraintViolationException.class);
        assertThat(result.getMessage()).contains("CHECK constraint failed");
    }

    @Test
    @DisplayName("Should translate extended error code 275 to CheckConstraintViolationException")
    void shouldTranslateExtendedCheckCode() {
        SQLException ex275 = new SQLException("CHECK constraint failed", null, 275);
        assertThat(translator.translate("Insert", "INSERT...", ex275))
                .isInstanceOf(CheckConstraintViolationException.class);
    }

    @Test
    @DisplayName("Should translate SQLITE_BUSY (5) and SQLITE_LOCKED (6) to CannotAcquireLockException")
    void shouldTranslateLockErrors() {
        SQLException exBusy = new SQLException("database is locked", null, 5);
        SQLException exLocked = new SQLException("database table is locked", null, 6);

        assertThat(translator.translate("Write", "UPDATE...", exBusy))
                .isInstanceOf(CannotAcquireLockException.class);
        assertThat(translator.translate("Write", "UPDATE...", exLocked))
                .isInstanceOf(CannotAcquireLockException.class);
    }

    @Test
    @DisplayName("Should fallback to generic DataAccessException for other SQLite errors")
    void shouldFallbackToDataAccessException() {
        SQLException ex = new SQLException("[SQLITE_ERROR] SQL error or missing database (no such table: nonexistent)", null, 1);
        DataAccessException result = translator.translate("Select", "SELECT * FROM \"nonexistent\"", ex);

        assertThat(result).isInstanceOf(DataAccessException.class);
        assertThat(result).isNotInstanceOf(DuplicateKeyException.class);
    }
}
