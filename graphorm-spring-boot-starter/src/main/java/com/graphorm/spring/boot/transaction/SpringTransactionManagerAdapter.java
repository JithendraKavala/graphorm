package com.graphorm.spring.boot.transaction;

import com.graphorm.jdbc.exception.QueryExecutionException;
import com.graphorm.jdbc.transaction.TransactionCallback;
import com.graphorm.jdbc.transaction.TransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * Adapter integrating GraphORM's {@link TransactionManager} SPI with Spring's
 * {@link PlatformTransactionManager} and {@link DataSourceUtils} for unified
 * declarative {@code @Transactional} boundaries (ADR-014).
 */
public class SpringTransactionManagerAdapter implements TransactionManager {

    private final DataSource dataSource;
    private final PlatformTransactionManager platformTransactionManager;
    private final TransactionTemplate transactionTemplate;

    public SpringTransactionManagerAdapter(DataSource dataSource, PlatformTransactionManager platformTransactionManager) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
        this.platformTransactionManager = platformTransactionManager;
        this.transactionTemplate = platformTransactionManager != null ? new TransactionTemplate(platformTransactionManager) : null;
    }

    public SpringTransactionManagerAdapter(DataSource dataSource) {
        this(dataSource, null);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public PlatformTransactionManager platformTransactionManager() {
        return platformTransactionManager;
    }

    public TransactionTemplate transactionTemplate() {
        return transactionTemplate;
    }

    @Override
    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "TransactionCallback must not be null");

        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> {
                Connection conn = DataSourceUtils.getConnection(dataSource);
                try {
                    return callback.doInTransaction(conn);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Exception e) {
                    throw new QueryExecutionException("Transaction failed: " + e.getMessage(), e);
                } finally {
                    DataSourceUtils.releaseConnection(conn, dataSource);
                }
            });
        }

        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            return callback.doInTransaction(conn);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new QueryExecutionException("Transaction failed: " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }
}
