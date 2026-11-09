package com.graphorm.jdbc.integration;

import com.graphorm.core.exception.TenantContextMissingException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.tenant.TenantBypassContext;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.core.tenant.TenantPartitionKey;
import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.jdbc.dialect.SqliteDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.relation.BatchRelationLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SQLite Multi-Tenancy Data Isolation Integration Tests")
class SqliteMultiTenancyIntegrationTest {

    private static final String DB_URL = "jdbc:sqlite:file:memdb_tenant?mode=memory&cache=shared";
    private static Connection keepAliveConnection;
    private static GraphOrmClient client;
    private static BatchRelationLoader batchRelationLoader;
    private static ModelRegistry registry;
    private static ModelMetadata organizationModel;
    private static ModelMetadata documentModel;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        keepAliveConnection = DriverManager.getConnection(DB_URL);
        try (Statement stmt = keepAliveConnection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS organizations (
                    id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    name TEXT NOT NULL
                );
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    org_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT,
                    FOREIGN KEY (org_id) REFERENCES organizations(id)
                );
            """);
        }

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(DB_URL);

        FieldMetadata orgId = FieldMetadata.builder("id", String.class).primaryKey(true).build();
        FieldMetadata orgTenant = FieldMetadata.builder("tenantId", String.class).dbColumnName("tenant_id").tenantId(true).build();
        FieldMetadata orgName = FieldMetadata.builder("name", String.class).build();

        RelationMetadata orgDocsRel = RelationMetadata.builder("documents")
                .targetModel("Document")
                .sourceModel("Organization")
                .relationType(RelationType.ONE_TO_MANY)
                .foreignKeyFields(List.of("id"))
                .referenceFields(List.of("orgId"))
                .build();

        organizationModel = ModelMetadata.builder("Organization")
                .tableName("organizations")
                .fields(List.of(orgId, orgTenant, orgName))
                .relations(List.of(orgDocsRel))
                .primaryKeyFields(List.of("id"))
                .multiTenant(true)
                .tenantColumn("tenant_id")
                .build();

        FieldMetadata docId = FieldMetadata.builder("id", String.class).primaryKey(true).build();
        FieldMetadata docTenant = FieldMetadata.builder("tenantId", String.class).dbColumnName("tenant_id").tenantId(true).build();
        FieldMetadata docOrgId = FieldMetadata.builder("orgId", String.class).dbColumnName("org_id").build();
        FieldMetadata docTitle = FieldMetadata.builder("title", String.class).build();
        FieldMetadata docContent = FieldMetadata.builder("content", String.class).build();

        RelationMetadata docOrgRel = RelationMetadata.builder("organization")
                .targetModel("Organization")
                .sourceModel("Document")
                .relationType(RelationType.MANY_TO_ONE)
                .foreignKeyFields(List.of("orgId"))
                .referenceFields(List.of("id"))
                .build();

        documentModel = ModelMetadata.builder("Document")
                .tableName("documents")
                .fields(List.of(docId, docTenant, docOrgId, docTitle, docContent))
                .relations(List.of(docOrgRel))
                .primaryKeyFields(List.of("id"))
                .multiTenant(true)
                .tenantColumn("tenant_id")
                .build();

        registry = ModelRegistry.builder()
                .addModel(organizationModel)
                .addModel(documentModel)
                .build();

        SqliteDialect dialect = new SqliteDialect();
        JdbcExecutor executor = new JdbcExecutor(ds, dialect);
        client = new GraphOrmClient(executor, registry, dialect);
        batchRelationLoader = new BatchRelationLoader(executor, registry);
    }

    @AfterAll
    static void tearDownDatabase() throws SQLException {
        if (keepAliveConnection != null && !keepAliveConnection.isClosed()) {
            keepAliveConnection.close();
        }
    }

    @BeforeEach
    void cleanData() throws SQLException {
        TenantContext.clear();
        TenantBypassContext.clear();
        try (Statement stmt = keepAliveConnection.createStatement()) {
            stmt.execute("DELETE FROM documents;");
            stmt.execute("DELETE FROM organizations;");
        }
    }

    @AfterEach
    void resetContext() {
        TenantContext.clear();
        TenantBypassContext.clear();
    }

    @Test
    @DisplayName("Should prevent tenant A from reading tenant B data")
    void shouldPreventCrossTenantReads() {
        // Seed tenant-alpha data
        TenantContext.runAsTenant("tenant-alpha", () -> {
            client.create("Organization", Map.of("id", "org-alpha-1", "name", "Alpha Corp"));
            client.create("Document", Map.of("id", "doc-alpha-1", "org_id", "org-alpha-1", "title", "Alpha Confidential"));
        });

        // Seed tenant-beta data
        TenantContext.runAsTenant("tenant-beta", () -> {
            client.create("Organization", Map.of("id", "org-beta-1", "name", "Beta Corp"));
            client.create("Document", Map.of("id", "doc-beta-1", "org_id", "org-beta-1", "title", "Beta Financials"));
        });

        // Query as tenant-alpha
        TenantContext.runAsTenant("tenant-alpha", () -> {
            List<Map<String, Object>> orgs = client.findMany("Organization");
            assertThat(orgs).hasSize(1);
            assertThat(orgs.getFirst().get("id")).isEqualTo("org-alpha-1");
            assertThat(orgs.getFirst().get("name")).isEqualTo("Alpha Corp");

            List<Map<String, Object>> docs = client.findMany("Document");
            assertThat(docs).hasSize(1);
            assertThat(docs.getFirst().get("title")).isEqualTo("Alpha Confidential");

            // Direct attempt to read beta's doc by ID
            Optional<Map<String, Object>> betaDoc = client.findUnique("Document", "doc-beta-1");
            assertThat(betaDoc).isEmpty();

            long docCount = client.count("Document");
            assertThat(docCount).isEqualTo(1L);
        });

        // Query as tenant-beta
        TenantContext.runAsTenant("tenant-beta", () -> {
            List<Map<String, Object>> orgs = client.findMany("Organization");
            assertThat(orgs).hasSize(1);
            assertThat(orgs.getFirst().get("id")).isEqualTo("org-beta-1");

            List<Map<String, Object>> docs = client.findMany("Document");
            assertThat(docs).hasSize(1);
            assertThat(docs.getFirst().get("title")).isEqualTo("Beta Financials");

            Optional<Map<String, Object>> alphaDoc = client.findUnique("Document", "doc-alpha-1");
            assertThat(alphaDoc).isEmpty();

            long docCount = client.count("Document");
            assertThat(docCount).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("Should prevent tenant A from updating or deleting tenant B data")
    void shouldPreventCrossTenantMutations() {
        // Seed tenant-alpha & tenant-beta data
        TenantContext.runAsTenant("tenant-alpha", () -> {
            client.create("Organization", Map.of("id", "org-alpha-1", "name", "Alpha Corp"));
        });
        TenantContext.runAsTenant("tenant-beta", () -> {
            client.create("Organization", Map.of("id", "org-beta-1", "name", "Beta Corp"));
        });

        // Tenant A tries to update Tenant B's org
        TenantContext.runAsTenant("tenant-alpha", () -> {
            int updated = client.update("Organization", "org-beta-1", Map.of("name", "Hacked By Alpha"));
            assertThat(updated).isEqualTo(0);

            int deleted = client.delete("Organization", "org-beta-1");
            assertThat(deleted).isEqualTo(0);
        });

        // Verify Tenant B's org remains untouched
        TenantContext.runAsTenant("tenant-beta", () -> {
            Optional<Map<String, Object>> betaOrg = client.findUnique("Organization", "org-beta-1");
            assertThat(betaOrg).isPresent();
            assertThat(betaOrg.get().get("name")).isEqualTo("Beta Corp");
        });
    }

    @Test
    @DisplayName("Should throw TenantContextMissingException when accessing multi-tenant model without context")
    void shouldThrowWhenContextMissing() {
        assertThatThrownBy(() -> client.findMany("Organization"))
                .isInstanceOf(TenantContextMissingException.class);

        assertThatThrownBy(() -> client.create("Organization", Map.of("id", "org-1", "name", "Org")))
                .isInstanceOf(TenantContextMissingException.class);

        assertThatThrownBy(() -> client.update("Organization", "org-1", Map.of("name", "Org")))
                .isInstanceOf(TenantContextMissingException.class);

        assertThatThrownBy(() -> client.delete("Organization", "org-1"))
                .isInstanceOf(TenantContextMissingException.class);

        assertThatThrownBy(() -> client.count("Organization"))
                .isInstanceOf(TenantContextMissingException.class);
    }

    @Test
    @DisplayName("Should allow supertenant bypass via TenantBypassContext")
    void shouldAllowSupertenantBypass() {
        TenantContext.runAsTenant("tenant-alpha", () -> {
            client.create("Organization", Map.of("id", "org-alpha-1", "name", "Alpha Corp"));
        });
        TenantContext.runAsTenant("tenant-beta", () -> {
            client.create("Organization", Map.of("id", "org-beta-1", "name", "Beta Corp"));
        });

        TenantBypassContext.runWithBypass(() -> {
            List<Map<String, Object>> allOrgs = client.findMany("Organization");
            assertThat(allOrgs).hasSize(2);

            long totalCount = client.count("Organization");
            assertThat(totalCount).isEqualTo(2L);
        });
    }

    @Test
    @DisplayName("Should partition DataLoader batch relation loading across tenants")
    void shouldPartitionDataLoaderAcrossTenants() {
        // Seed organizations and documents
        TenantContext.runAsTenant("tenant-alpha", () -> {
            client.create("Organization", Map.of("id", "org-alpha-1", "name", "Alpha Corp"));
            client.create("Document", Map.of("id", "doc-alpha-1", "org_id", "org-alpha-1", "title", "Doc Alpha"));
        });
        TenantContext.runAsTenant("tenant-beta", () -> {
            client.create("Organization", Map.of("id", "org-beta-1", "name", "Beta Corp"));
            client.create("Document", Map.of("id", "doc-beta-1", "org_id", "org-beta-1", "title", "Doc Beta"));
        });

        // Batch load relation Document.organization partitioned by tenant
        TenantPartitionKey keyAlpha = TenantPartitionKey.of("tenant-alpha", "org-alpha-1");
        TenantPartitionKey keyBeta = TenantPartitionKey.of("tenant-beta", "org-beta-1");

        RelationMetadata docOrgRel = documentModel.getRelation("organization");

        Map<Object, Object> results = batchRelationLoader.loadByKeys(
                documentModel,
                docOrgRel,
                List.of(keyAlpha, keyBeta),
                null
        );

        assertThat(results).containsKey(keyAlpha);
        assertThat(results).containsKey(keyBeta);

        @SuppressWarnings("unchecked")
        Map<String, Object> orgAlpha = (Map<String, Object>) results.get(keyAlpha);
        assertThat(orgAlpha).isNotNull();
        assertThat(orgAlpha.get("name")).isEqualTo("Alpha Corp");
        assertThat(orgAlpha.getOrDefault("tenantId", orgAlpha.get("tenant_id"))).isEqualTo("tenant-alpha");

        @SuppressWarnings("unchecked")
        Map<String, Object> orgBeta = (Map<String, Object>) results.get(keyBeta);
        assertThat(orgBeta).isNotNull();
        assertThat(orgBeta.get("name")).isEqualTo("Beta Corp");
        assertThat(orgBeta.getOrDefault("tenantId", orgBeta.get("tenant_id"))).isEqualTo("tenant-beta");
    }
}
