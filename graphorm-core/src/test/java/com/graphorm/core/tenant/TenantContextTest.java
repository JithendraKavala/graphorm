package com.graphorm.core.tenant;

import com.graphorm.core.exception.TenantContextMissingException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    private ModelMetadata multiTenantModel;
    private ModelMetadata regularModel;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        TenantBypassContext.clear();
        TenantContext.setProvider(null);

        FieldMetadata idField = FieldMetadata.builder("id", String.class).primaryKey(true).build();
        FieldMetadata tenantField = FieldMetadata.builder("tenantId", String.class).dbColumnName("tenant_id").tenantId(true).build();
        FieldMetadata nameField = FieldMetadata.builder("name", String.class).build();

        multiTenantModel = ModelMetadata.builder("TenantUser")
                .tableName("tenant_users")
                .addField(idField)
                .addField(tenantField)
                .addField(nameField)
                .addPrimaryKeyField("id")
                .tenantColumn("tenant_id")
                .multiTenant(true)
                .build();

        regularModel = ModelMetadata.builder("GlobalConfig")
                .tableName("global_configs")
                .addField(idField)
                .addField(nameField)
                .addPrimaryKeyField("id")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantBypassContext.clear();
        TenantContext.setProvider(null);
    }

    @Test
    @DisplayName("TenantContext should store and clear tenant identifier in thread local")
    void testTenantContextBasicLifecycle() {
        assertThat(TenantContext.getTenantId()).isEmpty();

        TenantContext.setTenantId("tenant-alpha");
        assertThat(TenantContext.getTenantId()).contains("tenant-alpha");

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    @DisplayName("runAsTenant should bind tenant for duration of action and restore previous")
    void testRunAsTenantRestoresContext() {
        TenantContext.setTenantId("outer-tenant");

        TenantContext.runAsTenant("inner-tenant", () -> {
            assertThat(TenantContext.getTenantId()).contains("inner-tenant");
        });

        assertThat(TenantContext.getTenantId()).contains("outer-tenant");
    }

    @Test
    @DisplayName("supplyAsTenant should bind tenant, return result, and restore previous context")
    void testSupplyAsTenant() {
        TenantContext.setTenantId("tenant-1");

        String result = TenantContext.supplyAsTenant("tenant-2", () -> {
            assertThat(TenantContext.getTenantId()).contains("tenant-2");
            return "computed-" + TenantContext.getTenantId().orElse("");
        });

        assertThat(result).isEqualTo("computed-tenant-2");
        assertThat(TenantContext.getTenantId()).contains("tenant-1");
    }

    @Test
    @DisplayName("TenantContext should isolate contexts across concurrent threads")
    void testThreadIsolation() throws InterruptedException {
        TenantContext.setTenantId("main-thread-tenant");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> workerTenant = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            TenantContext.setTenantId("worker-thread-tenant");
            workerTenant.set(TenantContext.getTenantId().orElse(null));
            latch.countDown();
        });
        worker.start();
        latch.await();

        assertThat(workerTenant.get()).isEqualTo("worker-thread-tenant");
        assertThat(TenantContext.getTenantId()).contains("main-thread-tenant");
    }

    @Test
    @DisplayName("DefaultTenantContextProvider should resolve current tenant from TenantContext")
    void testDefaultTenantContextProvider() {
        TenantContextProvider provider = new DefaultTenantContextProvider();
        assertThat(provider.getCurrentTenantId()).isEmpty();

        TenantContext.setTenantId("tenant-spi");
        assertThat(provider.getCurrentTenantId()).contains("tenant-spi");
    }

    @Test
    @DisplayName("TenantBypassContext should allow temporary bypass for super-tenant operations")
    void testTenantBypassContext() {
        assertThat(TenantBypassContext.isBypassed()).isFalse();

        TenantBypassContext.runWithBypass(() -> {
            assertThat(TenantBypassContext.isBypassed()).isTrue();
        });

        assertThat(TenantBypassContext.isBypassed()).isFalse();

        String supplied = TenantBypassContext.supplyWithBypass(() -> {
            assertThat(TenantBypassContext.isBypassed()).isTrue();
            return "bypassed-data";
        });
        assertThat(supplied).isEqualTo("bypassed-data");
        assertThat(TenantBypassContext.isBypassed()).isFalse();
    }

    @Test
    @DisplayName("QueryGuards should throw TenantContextMissingException when context missing on multi-tenant model")
    void testQueryGuardsEnforcesContext() {
        // Missing context
        assertThatThrownBy(() -> QueryGuards.requireTenantContext(multiTenantModel, "SELECT"))
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("Tenant context is required for SELECT on multi-tenant entity 'TenantUser'");

        // Regular non-tenant model does not require tenant context
        String regularCheck = QueryGuards.requireTenantContext(regularModel, "SELECT");
        assertThat(regularCheck).isNull();

        // Valid context
        TenantContext.setTenantId("tenant-xyz");
        String resolved = QueryGuards.requireTenantContext(multiTenantModel, "SELECT");
        assertThat(resolved).isEqualTo("tenant-xyz");
    }

    @Test
    @DisplayName("QueryGuards should allow operation without tenant if bypass is active")
    void testQueryGuardsAllowsBypass() {
        TenantBypassContext.runWithBypass(() -> {
            String resolved = QueryGuards.requireTenantContext(multiTenantModel, "SELECT");
            assertThat(resolved).isNull();
        });
    }

    @Test
    @DisplayName("QueryGuards.createInsert auto-populates tenant_id")
    void testCreateInsertInjectsTenant() {
        TenantContext.setTenantId("tenant-42");

        InsertQuery query = QueryGuards.createInsert(multiTenantModel, Map.of("id", "u-1", "name", "Alice"));
        assertThat(query.values()).containsEntry("tenant_id", "tenant-42");
        assertThat(query.values()).containsEntry("name", "Alice");
    }

    @Test
    @DisplayName("QueryGuards.createUpdate and createDelete inject tenant equality filter")
    void testCreateUpdateAndDeleteInjectsTenantWhere() {
        TenantContext.setTenantId("tenant-99");

        UpdateQuery updateQuery = QueryGuards.createUpdate(multiTenantModel, Map.of("name", "Bob"), WherePredicate.eq("id", "u-1"));
        assertThat(updateQuery.where()).isNotNull();

        DeleteQuery deleteQuery = QueryGuards.createDelete(multiTenantModel, WherePredicate.eq("id", "u-1"));
        assertThat(deleteQuery.where()).isNotNull();
    }

    @Test
    @DisplayName("TenantPartitionKey correctly encapsulates tenant and key")
    void testTenantPartitionKey() {
        TenantPartitionKey key1 = TenantPartitionKey.of("t-1", "key-123");
        TenantPartitionKey key2 = new TenantPartitionKey("t-1", "key-123");
        TenantPartitionKey key3 = new TenantPartitionKey("t-2", "key-123");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1).isNotEqualTo(key3);
        assertThat(key1.tenantId()).isEqualTo("t-1");
        assertThat(key1.key()).isEqualTo("key-123");
    }
}
