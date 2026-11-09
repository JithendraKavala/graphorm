package com.graphorm.spring.boot.graphql;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphQL SDL Generator Unit Tests")
class GraphQlSdlGeneratorTest {

    @Test
    @DisplayName("Should exclude backend-injected fields (@injected) from CreateInput and UpdateInput")
    void shouldExcludeInjectedFieldsFromInputs() {
        ModelMetadata model = ModelMetadata.builder("Document")
                .tableName("documents")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("title", String.class).build())
                .addField(FieldMetadata.builder("tenantId", String.class).injected("tenantId").build())
                .addField(FieldMetadata.builder("createdBy", String.class).injected("auth.username").build())
                .addPrimaryKeyField("id")
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(model)
                .build();

        String sdl = GraphQlSdlGenerator.generate(registry);

        // Document type must contain all fields
        assertThat(sdl).contains("type Document {");
        assertThat(sdl).contains("title: String!");
        assertThat(sdl).contains("tenantId: String!");
        assertThat(sdl).contains("createdBy: String!");

        // DocumentCreateInput must contain title, but NOT tenantId or createdBy
        int createIdx = sdl.indexOf("input DocumentCreateInput {");
        int updateIdx = sdl.indexOf("input DocumentUpdateInput {");
        int queryIdx = sdl.indexOf("type Query {");

        String createInput = sdl.substring(createIdx, updateIdx);
        assertThat(createInput).contains("title: String!");
        assertThat(createInput).doesNotContain("tenantId");
        assertThat(createInput).doesNotContain("createdBy");

        // DocumentUpdateInput must NOT contain tenantId or createdBy
        String updateInput = sdl.substring(updateIdx, queryIdx);
        assertThat(updateInput).contains("title: String");
        assertThat(updateInput).doesNotContain("tenantId");
        assertThat(updateInput).doesNotContain("createdBy");
    }

    @Test
    @DisplayName("Should suppress input types and mutations for read-only physical models (@@readonly)")
    void shouldSuppressMutationsForReadOnlyModel() {
        ModelMetadata readOnlyModel = ModelMetadata.builder("SystemAudit")
                .tableName("system_audits")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("event", String.class).build())
                .addPrimaryKeyField("id")
                .readOnly(true)
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(readOnlyModel)
                .build();

        String sdl = GraphQlSdlGenerator.generate(registry);

        // SystemAudit type exists
        assertThat(sdl).contains("type SystemAudit {");

        // Input types must NOT be generated
        assertThat(sdl).doesNotContain("SystemAuditCreateInput");
        assertThat(sdl).doesNotContain("SystemAuditUpdateInput");

        // Mutations must NOT be generated
        assertThat(sdl).doesNotContain("createSystemAudit");
        assertThat(sdl).doesNotContain("updateSystemAudit");
        assertThat(sdl).doesNotContain("deleteSystemAudit");
        assertThat(sdl).doesNotContain("type Mutation");

        // Queries MUST be generated
        assertThat(sdl).contains("type Query {");
        assertThat(sdl).contains("systemAudits: [SystemAudit!]!");
        assertThat(sdl).contains("systemAudit(id: ID!): SystemAudit");
    }

    @Test
    @DisplayName("Should generate @auth(roles: [...]) on top-level queries and mutations for models and views with authRoles")
    void shouldGenerateAuthDirectivesOnOperations() {
        ModelMetadata protectedModel = ModelMetadata.builder("FinancialRecord")
                .tableName("financial_records")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("amount", Double.class).build())
                .addPrimaryKeyField("id")
                .authRoles(List.of("FINANCE_ADMIN", "AUDITOR"))
                .build();

        ViewModelMetadata protectedView = ViewModelMetadata.builder("QuarterlyReport")
                .viewName("vw_quarterly_report")
                .databaseBacked(true)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("total", Double.class).build())
                .authRoles(List.of("CFO"))
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(protectedModel)
                .addViewModel(protectedView)
                .build();

        String sdl = GraphQlSdlGenerator.generate(registry);

        // Directive declaration
        assertThat(sdl).contains("directive @auth(roles: [String!]!) on FIELD_DEFINITION");

        // Model queries with @auth
        assertThat(sdl).contains("financialRecords: [FinancialRecord!]! @auth(roles: [\"FINANCE_ADMIN\", \"AUDITOR\"])");
        assertThat(sdl).contains("financialRecord(id: ID!): FinancialRecord @auth(roles: [\"FINANCE_ADMIN\", \"AUDITOR\"])");

        // Model mutations with @auth
        assertThat(sdl).contains("createFinancialRecord(input: FinancialRecordCreateInput!): FinancialRecord! @auth(roles: [\"FINANCE_ADMIN\", \"AUDITOR\"])");
        assertThat(sdl).contains("updateFinancialRecord(id: ID!, input: FinancialRecordUpdateInput!): FinancialRecord! @auth(roles: [\"FINANCE_ADMIN\", \"AUDITOR\"])");
        assertThat(sdl).contains("deleteFinancialRecord(id: ID!): Boolean! @auth(roles: [\"FINANCE_ADMIN\", \"AUDITOR\"])");

        // View queries with @auth
        assertThat(sdl).contains("quarterlyReports: [QuarterlyReport!]! @auth(roles: [\"CFO\"])");
        assertThat(sdl).contains("quarterlyReport(id: ID!): QuarterlyReport @auth(roles: [\"CFO\"])");

        // Views must NEVER have mutations
        assertThat(sdl).doesNotContain("createQuarterlyReport");
        assertThat(sdl).doesNotContain("updateQuarterlyReport");
        assertThat(sdl).doesNotContain("deleteQuarterlyReport");
    }

    @Test
    @DisplayName("Should generate Subscription type for models with @notify metadata")
    void shouldGenerateSubscriptionTypeForNotifiedModels() {
        ModelMetadata studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addPrimaryKeyField("id")
                .notifyMetadata(com.graphorm.core.metadata.NotifyMetadata.of(java.util.Set.of(
                        com.graphorm.core.event.NotifyEventType.CREATE,
                        com.graphorm.core.event.NotifyEventType.UPDATE,
                        com.graphorm.core.event.NotifyEventType.DELETE
                )))
                .build();

        ModelMetadata logModel = ModelMetadata.builder("AuditLog")
                .tableName("audit_logs")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("msg", String.class).build())
                .addPrimaryKeyField("id")
                .notifyMetadata(com.graphorm.core.metadata.NotifyMetadata.DISABLED)
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(studentModel)
                .addModel(logModel)
                .build();

        String sdl = GraphQlSdlGenerator.generate(registry);

        assertThat(sdl).contains("type Subscription {");
        assertThat(sdl).contains("studentCreated: Student!");
        assertThat(sdl).contains("studentUpdated(id: ID): Student!");
        assertThat(sdl).contains("studentDeleted: ID!");
        assertThat(sdl).doesNotContain("auditLogCreated");
        assertThat(sdl).doesNotContain("auditLogUpdated");
        assertThat(sdl).doesNotContain("auditLogDeleted");
    }

    @Test
    @DisplayName("Should generate standard SDL without @auth for unannotated models")
    void shouldGenerateStandardSdlWithoutAuth() {
        ModelMetadata normalModel = ModelMetadata.builder("Product")
                .tableName("products")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("title", String.class).build())
                .addPrimaryKeyField("id")
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(normalModel)
                .build();

        String sdl = GraphQlSdlGenerator.generate(registry);

        assertThat(sdl).contains("type Product {");
        assertThat(sdl).contains("products: [Product!]!");
        assertThat(sdl).doesNotContain("products: [Product!]! @auth");
        assertThat(sdl).contains("createProduct(input: ProductCreateInput!): Product!");
        assertThat(sdl).doesNotContain("createProduct(input: ProductCreateInput!): Product! @auth");
    }
}
