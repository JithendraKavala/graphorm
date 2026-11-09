package com.graphorm.jdbc.dialect;

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

@DisplayName("MySqlErrorTranslator Unit Tests")
class MySqlErrorTranslatorTest {

    private MySqlErrorTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new MySqlErrorTranslator();
    }

    @Test
    @DisplayName("Should translate MySQL error code 1062 to DuplicateKeyException")
    void shouldTranslateDuplicateKey() {
        SQLException ex = new SQLException("Duplicate entry 'test@example.com' for key 'users.email'", "23000", 1062);
        DataAccessException result = translator.translate("Insert user", "INSERT INTO `users`...", ex);

        assertThat(result).isInstanceOf(DuplicateKeyException.class);
        assertThat(result.getMessage()).contains("Duplicate entry");
        assertThat(result.getSql()).isEqualTo("INSERT INTO `users`...");
    }

    @Test
    @DisplayName("Should translate MySQL error codes 1451 and 1452 to ForeignKeyViolationException")
    void shouldTranslateForeignKeyViolations() {
        SQLException ex1451 = new SQLException("Cannot delete or update a parent row: a foreign key constraint fails", "23000", 1451);
        SQLException ex1452 = new SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452);

        assertThat(translator.translate("Delete dept", "DELETE FROM...", ex1451))
                .isInstanceOf(ForeignKeyViolationException.class);
        assertThat(translator.translate("Insert student", "INSERT INTO...", ex1452))
                .isInstanceOf(ForeignKeyViolationException.class);
    }

    @Test
    @DisplayName("Should translate MySQL error codes 1048 and 1364 to DataIntegrityViolationException")
    void shouldTranslateNotNullViolations() {
        SQLException ex1048 = new SQLException("Column 'name' cannot be null", "23000", 1048);
        SQLException ex1364 = new SQLException("Field 'name' doesn't have a default value", "HY000", 1364);

        assertThat(translator.translate("Insert", "INSERT...", ex1048))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(translator.translate("Insert", "INSERT...", ex1364))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should translate MySQL check constraint violations (3819, 4025)")
    void shouldTranslateCheckConstraints() {
        SQLException ex3819 = new SQLException("Check constraint 'chk_age' is violated.", "HY000", 3819);
        assertThat(translator.translate("Insert", "INSERT...", ex3819))
                .isInstanceOf(CheckConstraintViolationException.class);
    }

    @Test
    @DisplayName("Should fallback to generic DataAccessException for unmapped error codes")
    void shouldFallbackToDataAccessException() {
        SQLException ex = new SQLException("Unknown column 'foo' in 'field list'", "42S22", 1054);
        DataAccessException result = translator.translate("Select", "SELECT foo FROM...", ex);

        assertThat(result).isInstanceOf(DataAccessException.class);
        assertThat(result).isNotInstanceOf(DuplicateKeyException.class);
    }
}
