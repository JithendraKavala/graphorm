package com.graphorm.spring.boot.transaction;

import com.graphorm.jdbc.executor.JdbcExecutor;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * ConnectionProvider integrating with Spring's {@link DataSourceUtils} to participate
 * transparently in declarative {@code @Transactional} boundaries.
 */
public class SpringConnectionProvider implements JdbcExecutor.ConnectionProvider {

    @Override
    public Connection getConnection(DataSource dataSource) throws SQLException {
        return DataSourceUtils.getConnection(dataSource);
    }

    @Override
    public void releaseConnection(Connection connection, DataSource dataSource) throws SQLException {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }
}
