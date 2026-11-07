package com.graphorm.jdbc.client;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.mapper.RowMapper;
import com.graphorm.jdbc.transaction.TransactionCallback;
import com.graphorm.jdbc.transaction.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GraphOrmClient Unit Tests (Generic Client)")
class GraphOrmClientTest {

    private ModelRegistry registry;
    private StubExecutor executor;
    private StubTransactionManager txManager;
    private GraphOrmClient client;

    @BeforeEach
    void setUp() {
        ModelMetadata studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).dbColumnName("id").build())
                .addField(FieldMetadata.builder("name", String.class).dbColumnName("name").build())
                .addPrimaryKeyField("id")
                .build();

        ViewModelMetadata dashboardView = ViewModelMetadata.builder("StudentDashboard")
                .viewName("vw_student_dashboard")
                .databaseBacked(true)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).dbColumnName("id").build())
                .addField(FieldMetadata.builder("name", String.class).dbColumnName("name").build())
                .build();

        registry = ModelRegistry.builder()
                .addModel(studentModel)
                .addViewModel(dashboardView)
                .build();

        executor = new StubExecutor();
        txManager = new StubTransactionManager();
        client = new GraphOrmClient(executor, registry, new PostgreSqlDialect(), txManager);
    }

    @Test
    @DisplayName("findMany executes select query for model")
    void testFindMany() {
        executor.listResult = List.of(Map.of("id", 1L, "name", "Alice"));

        List<Map<String, Object>> result = client.findMany("Student");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).containsEntry("name", "Alice");
        assertThat(executor.lastSelectQuery).isNotNull();
    }

    @Test
    @DisplayName("findUnique returns mapped object by primary key")
    void testFindUnique() {
        executor.singleResult = Optional.of(Map.of("id", 1L, "name", "Alice"));

        Optional<Map<String, Object>> result = client.findUnique("Student", 1L);
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("id", 1L);
        assertThat(executor.lastSelectQuery).isNotNull();
    }

    @Test
    @DisplayName("create executes insert and returns created map")
    void testCreate() {
        executor.insertResult = Optional.of(Map.of("id", 1L, "name", "Alice"));

        Map<String, Object> result = client.create("Student", Map.of("name", "Alice"));
        assertThat(result).containsEntry("name", "Alice");
        assertThat(executor.lastInsertQuery).isNotNull();
    }

    @Test
    @DisplayName("Strict Read-Only Invariant: create/update/delete on ViewModel throws ReadOnlyViewException")
    void testReadOnlyViewMutationRejection() {
        assertThatThrownBy(() -> client.create("StudentDashboard", Map.of("name", "Bob")))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentDashboard");

        assertThatThrownBy(() -> client.update("StudentDashboard", 1L, Map.of("name", "Bob")))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentDashboard");

        assertThatThrownBy(() -> client.delete("StudentDashboard", 1L))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentDashboard");
    }

    @Test
    @DisplayName("transaction delegates to TransactionManager")
    void testTransaction() {
        String res = client.transaction(conn -> "success");
        assertThat(res).isEqualTo("success");
        assertThat(txManager.executed.get()).isTrue();
    }

    @Test
    @DisplayName("count builds and executes count query")
    void testCount() {
        executor.countResult = Optional.of(42L);

        long count = client.count("Student");
        assertThat(count).isEqualTo(42L);
        assertThat(executor.lastBoundSql).isNotNull();
    }

    private static class StubExecutor extends JdbcExecutor {
        List<Map<String, Object>> listResult = List.of();
        Optional<Map<String, Object>> singleResult = Optional.empty();
        Optional<Map<String, Object>> insertResult = Optional.empty();
        Optional<?> countResult = Optional.empty();
        SelectQuery lastSelectQuery;
        InsertQuery lastInsertQuery;
        BoundSql lastBoundSql;

        StubExecutor() {
            super(createStubDataSource(), new PostgreSqlDialect());
        }

        @Override
        public List<Map<String, Object>> queryForList(SelectQuery query) {
            this.lastSelectQuery = query;
            return listResult;
        }

        @Override
        public Optional<Map<String, Object>> queryForObject(SelectQuery query) {
            this.lastSelectQuery = query;
            return singleResult;
        }

        @Override
        public Optional<Map<String, Object>> executeInsertAndReturn(InsertQuery query) {
            this.lastInsertQuery = query;
            return insertResult;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> queryForObject(BoundSql boundSql, RowMapper<T> rowMapper) {
            this.lastBoundSql = boundSql;
            return (Optional<T>) countResult;
        }
    }

    private static class StubTransactionManager implements TransactionManager {
        final AtomicBoolean executed = new AtomicBoolean(false);

        @Override
        public <T> T executeInTransaction(TransactionCallback<T> callback) {
            executed.set(true);
            try {
                return callback.doInTransaction(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static DataSource createStubDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> null
        );
    }
}
