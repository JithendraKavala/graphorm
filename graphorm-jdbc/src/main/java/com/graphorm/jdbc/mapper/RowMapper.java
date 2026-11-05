package com.graphorm.jdbc.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional interface for mapping rows of a {@link ResultSet} on a per-row basis.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps the current row of the given {@link ResultSet} to a target object.
     *
     * @param rs     the ResultSet positioned at the current row
     * @param rowNum the 0-based index of the current row
     * @return the mapped object (may be null)
     * @throws SQLException if a JDBC error occurs while reading the row
     */
    T mapRow(ResultSet rs, int rowNum) throws SQLException;
}
