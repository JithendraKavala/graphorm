package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticLockingTest {

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    @Test
    @DisplayName("UpdateBuilder optimisticLock appends auto-incremented version and WHERE clause")
    void optimisticLockIncrementsAndAppendsWhere() {
        UpdateBuilder builder = UpdateBuilder.of(dialect)
                .table("products")
                .set("name", "New Laptop")
                .where(WherePredicate.eq("id", 42L))
                .optimisticLock("version", 3);

        BoundSql bound = builder.build();
        String sql = bound.sql();
        assertThat(sql).isEqualTo("UPDATE \"products\" SET \"name\" = ?, \"version\" = ? WHERE (\"id\" = ? AND \"version\" = ?)");

        List<Object> params = bound.parameters();
        assertThat(params).containsExactly("New Laptop", 4, 42L, 3);
        assertThat(builder.isOptimisticLockingEnabled()).isTrue();
    }

    @Test
    @DisplayName("UpdateBuilder verifyOptimisticLock passes when 1 row is affected")
    void verifyOptimisticLockSucceedsOnRowsAffected() {
        UpdateBuilder builder = UpdateBuilder.of(dialect)
                .table("products")
                .set("name", "New Laptop")
                .where(WherePredicate.eq("id", 42L))
                .optimisticLock("version", 3);

        builder.verifyOptimisticLock(1); // should not throw
    }

    @Test
    @DisplayName("UpdateBuilder verifyOptimisticLock throws OptimisticLockException when 0 rows affected")
    void verifyOptimisticLockThrowsOnZeroRowsAffected() {
        UpdateBuilder builder = UpdateBuilder.of(dialect)
                .table("products")
                .set("name", "New Laptop")
                .where(WherePredicate.eq("id", 42L))
                .optimisticLock("version", 3);

        assertThatThrownBy(() -> builder.verifyOptimisticLock(0))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("Optimistic lock failure: record in 'products'")
                .hasMessageContaining("expected version: 3");
    }

    @Test
    @DisplayName("ModelMetadata correctly detects @version field")
    void modelMetadataDetectsVersionField() {
        FieldMetadata id = FieldMetadata.builder("id", Long.class).primaryKey(true).build();
        FieldMetadata ver = FieldMetadata.builder("version", Integer.class).version(true).build();
        FieldMetadata name = FieldMetadata.builder("name", String.class).build();

        ModelMetadata model = ModelMetadata.builder("Product")
                .tableName("products")
                .fields(List.of(id, ver, name))
                .build();

        assertThat(model.hasVersionField()).isTrue();
        assertThat(model.versionField()).isPresent();
        assertThat(model.versionField().get().name()).isEqualTo("version");
        assertThat(model.versionField().get().isVersion()).isTrue();
    }
}
