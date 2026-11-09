package com.graphorm.jdbc.builder;

import com.graphorm.core.exception.TenantContextMissingException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewJoinMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.JoinType;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.tenant.TenantBypassContext;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Multi-Tenancy Query Builder and Planner Tests")
class MultiTenancyQueryBuilderTest {

    private final SqlDialect dialect = new PostgreSqlDialect();

    private ModelMetadata tenantModel;
    private ModelMetadata standardModel;
    private ModelMetadata joinedTenantModel;
    private ViewModelMetadata virtualTenantView;
    private ModelRegistry registry;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        TenantBypassContext.clear();

        FieldMetadata idField = FieldMetadata.builder("id", String.class)
                .dbColumnName("id")
                .primaryKey(true)
                .build();
        FieldMetadata tenantIdField = FieldMetadata.builder("tenantId", String.class)
                .dbColumnName("tenant_id")
                .tenantId(true)
                .build();
        FieldMetadata nameField = FieldMetadata.builder("name", String.class)
                .dbColumnName("name")
                .build();
        FieldMetadata statusField = FieldMetadata.builder("status", String.class)
                .dbColumnName("status")
                .build();

        tenantModel = ModelMetadata.builder("TenantEntity")
                .tableName("tenant_entities")
                .fields(List.of(idField, tenantIdField, nameField, statusField))
                .primaryKeyFields(List.of("id"))
                .multiTenant(true)
                .tenantColumn("tenant_id")
                .build();

        standardModel = ModelMetadata.builder("StandardEntity")
                .tableName("standard_entities")
                .fields(List.of(idField, nameField, statusField))
                .primaryKeyFields(List.of("id"))
                .multiTenant(false)
                .build();

        FieldMetadata deptIdField = FieldMetadata.builder("id", String.class)
                .dbColumnName("id")
                .primaryKey(true)
                .build();
        FieldMetadata deptTenantField = FieldMetadata.builder("tenantId", String.class)
                .dbColumnName("tenant_id")
                .tenantId(true)
                .build();
        FieldMetadata deptNameField = FieldMetadata.builder("deptName", String.class)
                .dbColumnName("dept_name")
                .build();

        joinedTenantModel = ModelMetadata.builder("Department")
                .tableName("departments")
                .fields(List.of(deptIdField, deptTenantField, deptNameField))
                .primaryKeyFields(List.of("id"))
                .multiTenant(true)
                .tenantColumn("tenant_id")
                .build();

        FieldMetadata vIdField = FieldMetadata.builder("entityId", String.class)
                .expression("e.id")
                .build();
        FieldMetadata vNameField = FieldMetadata.builder("entityName", String.class)
                .expression("e.name")
                .build();
        FieldMetadata vDeptNameField = FieldMetadata.builder("departmentName", String.class)
                .expression("d.dept_name")
                .build();

        ViewJoinMetadata joinMeta = new ViewJoinMetadata(
                JoinType.INNER,
                "Department",
                "d",
                "e.id == d.id"
        );

        virtualTenantView = ViewModelMetadata.builder("TenantDashboardView")
                .viewName("tenant_dashboard_view")
                .sourceModel("TenantEntity", "e")
                .fields(List.of(vIdField, vNameField, vDeptNameField))
                .joins(List.of(joinMeta))
                .build();

        registry = ModelRegistry.builder()
                .addModel(tenantModel)
                .addModel(standardModel)
                .addModel(joinedTenantModel)
                .addViewModel(virtualTenantView)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantBypassContext.clear();
    }

    @Test
    @DisplayName("SelectBuilder should inject tenant predicate when context is active")
    void selectBuilderShouldInjectTenantPredicate() {
        TenantContext.runAsTenant("tenant-corp-1", () -> {
            SelectQuery query = SelectBuilder.forEntity(tenantModel, registry)
                    .where(WherePredicate.eq("status", "ACTIVE"))
                    .build();

            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            assertThat(boundSql.sql()).contains("\"tenant_id\" = ?");
            assertThat(boundSql.sql()).contains("\"status\" = ?");
            assertThat(boundSql.parameters()).contains("tenant-corp-1", "ACTIVE");
        });
    }

    @Test
    @DisplayName("SelectBuilder should throw TenantContextMissingException when context is missing")
    void selectBuilderShouldThrowWhenContextMissing() {
        assertThatThrownBy(() -> SelectBuilder.forEntity(tenantModel, registry))
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("Tenant context is required for SELECT on multi-tenant entity 'TenantEntity'");
    }

    @Test
    @DisplayName("SelectBuilder should allow bypass when TenantBypassContext is active")
    void selectBuilderShouldAllowBypass() {
        TenantBypassContext.runWithBypass(() -> {
            SelectQuery query = SelectBuilder.forEntity(tenantModel, registry)
                    .where(WherePredicate.eq("status", "ACTIVE"))
                    .build();

            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            assertThat(boundSql.sql()).doesNotContain("\"tenant_id\" = ?");
            assertThat(boundSql.sql()).contains("\"status\" = ?");
            assertThat(boundSql.parameters()).containsExactly("ACTIVE");
        });
    }

    @Test
    @DisplayName("InsertBuilder should auto-populate tenant column from active context")
    void insertBuilderShouldAutoPopulateTenantColumn() {
        TenantContext.runAsTenant("tenant-corp-1", () -> {
            BoundSql boundSql = InsertBuilder.forEntity(dialect, tenantModel)
                    .value("name", "Test Item")
                    .build();

            assertThat(boundSql.sql()).contains("\"tenant_id\"");
            assertThat(boundSql.parameters()).contains("tenant-corp-1", "Test Item");
        });
    }

    @Test
    @DisplayName("InsertBuilder should throw TenantContextMissingException when context is missing")
    void insertBuilderShouldThrowWhenContextMissing() {
        assertThatThrownBy(() -> InsertBuilder.forEntity(dialect, tenantModel))
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("Tenant context is required for INSERT on multi-tenant entity 'TenantEntity'");
    }

    @Test
    @DisplayName("UpdateBuilder should inject tenant predicate into WHERE and values")
    void updateBuilderShouldInjectTenantPredicate() {
        TenantContext.runAsTenant("tenant-corp-1", () -> {
            BoundSql boundSql = UpdateBuilder.forEntity(dialect, tenantModel)
                    .id("id", "item-1")
                    .set("name", "Updated Item")
                    .build();

            assertThat(boundSql.sql()).contains("\"tenant_id\" = ?");
            assertThat(boundSql.parameters()).contains("tenant-corp-1");
        });
    }

    @Test
    @DisplayName("UpdateBuilder should throw TenantContextMissingException when context is missing")
    void updateBuilderShouldThrowWhenContextMissing() {
        assertThatThrownBy(() -> UpdateBuilder.forEntity(dialect, tenantModel))
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("Tenant context is required for UPDATE on multi-tenant entity 'TenantEntity'");
    }

    @Test
    @DisplayName("DeleteBuilder should inject tenant predicate into WHERE clause")
    void deleteBuilderShouldInjectTenantPredicate() {
        TenantContext.runAsTenant("tenant-corp-1", () -> {
            BoundSql boundSql = DeleteBuilder.forEntity(dialect, tenantModel)
                    .where(WherePredicate.eq("id", "item-1"))
                    .build();

            assertThat(boundSql.sql()).contains("\"tenant_id\" = ?");
            assertThat(boundSql.parameters()).contains("tenant-corp-1", "item-1");
        });
    }

    @Test
    @DisplayName("DeleteBuilder should throw TenantContextMissingException when context is missing")
    void deleteBuilderShouldThrowWhenContextMissing() {
        assertThatThrownBy(() -> DeleteBuilder.forEntity(dialect, tenantModel))
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("Tenant context is required for DELETE on multi-tenant entity 'TenantEntity'");
    }

    @Test
    @DisplayName("ViewQueryPlanner should push down tenant predicates to source and joined tables")
    void viewQueryPlannerShouldPushDownTenantPredicates() {
        TenantContext.runAsTenant("tenant-corp-1", () -> {
            SelectQuery query = ViewQueryPlanner.plan(virtualTenantView, registry, dialect).build();
            BoundSql boundSql = SelectBuilder.of(dialect).build(query);

            // Source model pushdown
            assertThat(boundSql.sql()).contains("\"e\".\"tenant_id\" = ?");
            // Joined model pushdown in ON condition
            assertThat(boundSql.sql()).contains("\"d\".\"tenant_id\" = ?");
            assertThat(boundSql.parameters()).contains("tenant-corp-1");
        });
    }

    @Test
    @DisplayName("ViewQueryPlanner should throw TenantContextMissingException when multi-tenant view source context is missing")
    void viewQueryPlannerShouldThrowWhenContextMissing() {
        assertThatThrownBy(() -> ViewQueryPlanner.plan(virtualTenantView, registry, dialect).build())
                .isInstanceOf(TenantContextMissingException.class);
    }
}
