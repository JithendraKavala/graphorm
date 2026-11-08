package com.graphorm.jdbc.dialect;

import com.graphorm.core.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlErrorTranslatorTest {

    private final PostgreSqlErrorTranslator translator = new PostgreSqlErrorTranslator();

    @Test
    @DisplayName("Translates SQLSTATE 23505 to DuplicateKeyException")
    void translatesDuplicateKey() {
        SQLException sqlEx = new SQLException("duplicate key value violates unique constraint \"users_email_key\"", "23505", 0);
        DataAccessException ex = translator.translate("execute insert", "INSERT INTO users...", sqlEx);

        assertThat(ex).isInstanceOf(DuplicateKeyException.class);
        assertThat(ex.getMessage()).contains("duplicate key value violates unique constraint");
        assertThat(ex.getCause()).isSameAs(sqlEx);
        assertThat(ex.getSqlState()).isEqualTo("23505");
        assertThat(ex.getSql()).isEqualTo("INSERT INTO users...");
    }

    @Test
    @DisplayName("Translates SQLSTATE 23503 to ForeignKeyViolationException")
    void translatesForeignKeyViolation() {
        SQLException sqlEx = new SQLException("insert or update violates foreign key constraint", "23503", 0);
        DataAccessException ex = translator.translate("execute insert", "INSERT INTO orders...", sqlEx);

        assertThat(ex).isInstanceOf(ForeignKeyViolationException.class);
        assertThat(ex.getMessage()).contains("violates foreign key constraint");
        assertThat(ex.getSqlState()).isEqualTo("23503");
    }

    @Test
    @DisplayName("Translates SQLSTATE 23514 to CheckConstraintViolationException")
    void translatesCheckConstraintViolation() {
        SQLException sqlEx = new SQLException("new row violates check constraint \"positive_price\"", "23514", 0);
        DataAccessException ex = translator.translate("execute update", "UPDATE products...", sqlEx);

        assertThat(ex).isInstanceOf(CheckConstraintViolationException.class);
        assertThat(ex.getMessage()).contains("violates check constraint");
        assertThat(ex.getSqlState()).isEqualTo("23514");
    }

    @Test
    @DisplayName("Translates other 23xxx SQLSTATE to DataIntegrityViolationException")
    void translatesOtherIntegrityViolations() {
        SQLException sqlEx = new SQLException("not-null constraint violation", "23502", 0);
        DataAccessException ex = translator.translate("execute insert", "INSERT INTO items...", sqlEx);

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(ex.getMessage()).contains("not-null constraint violation");
        assertThat(ex.getSqlState()).isEqualTo("23502");
    }

    @Test
    @DisplayName("Translates general SQLSTATE to DataAccessException")
    void translatesGeneralSqlException() {
        SQLException sqlEx = new SQLException("connection terminated", "08006", 0);
        DataAccessException ex = translator.translate("execute query", "SELECT 1", sqlEx);

        assertThat(ex).isInstanceOf(DataAccessException.class);
        assertThat(ex).isNotInstanceOf(DataIntegrityViolationException.class);
        assertThat(ex.getMessage()).contains("connection terminated");
        assertThat(ex.getSqlState()).isEqualTo("08006");
    }
}
