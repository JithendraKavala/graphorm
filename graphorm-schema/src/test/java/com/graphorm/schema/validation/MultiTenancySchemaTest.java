package com.graphorm.schema.validation;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.schema.ast.FieldNode;
import com.graphorm.schema.ast.ModelNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiTenancySchemaTest {

    @Test
    @DisplayName("Should parse and validate model with field-level @tenantId directive")
    void testFieldLevelTenantId() {
        String schema = """
                model OrganizationUser {
                    id String @id
                    tenantId String @tenantId
                    username String
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ModelNode model = doc.findModel("OrganizationUser").orElseThrow();
        FieldNode tenantField = model.findField("tenantId").orElseThrow();

        assertThat(tenantField.isTenantId()).isTrue();
        assertThat(model.isMultiTenant()).isTrue();

        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        ModelMetadata meta = registry.getModel("OrganizationUser");
        assertThat(meta.isMultiTenant()).isTrue();
        assertThat(meta.tenantColumn()).isEqualTo("tenantId");

        FieldMetadata tfMeta = meta.getField("tenantId");
        assertThat(tfMeta.isTenantId()).isTrue();
    }

    @Test
    @DisplayName("Should parse and validate model with block @@tenant(field: \"...\") and custom column mapping")
    void testBlockLevelTenantWithFieldAndMap() {
        String schema = """
                model CustomerOrder {
                    id String @id
                    orgId String @map("org_id")
                    orderTotal Float
                    @@tenant(field: "orgId")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ModelNode model = doc.findModel("CustomerOrder").orElseThrow();
        assertThat(model.isMultiTenant()).isTrue();
        assertThat(model.tenantFieldName()).contains("orgId");

        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        ModelMetadata meta = registry.getModel("CustomerOrder");
        assertThat(meta.isMultiTenant()).isTrue();
        assertThat(meta.tenantColumn()).isEqualTo("org_id");

        FieldMetadata orgIdField = meta.getField("orgId");
        assertThat(orgIdField.isTenantId()).isTrue();
    }

    @Test
    @DisplayName("Should parse and validate model with block @@tenant(column: \"tenant_id\")")
    void testBlockLevelTenantWithColumn() {
        String schema = """
                model Invoice {
                    id String @id
                    tenant_id String
                    amount Float
                    @@tenant(column: "tenant_id")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ModelNode model = doc.findModel("Invoice").orElseThrow();
        assertThat(model.isMultiTenant()).isTrue();

        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        ModelMetadata meta = registry.getModel("Invoice");
        assertThat(meta.isMultiTenant()).isTrue();
        assertThat(meta.tenantColumn()).isEqualTo("tenant_id");
    }

    @Test
    @DisplayName("Should accept UUID, Int, and BigInt as valid tenant field types")
    void testScalarTenantTypes() {
        String schema = """
                model TenantWithInt {
                    id String @id
                    tenantId Int @tenantId
                }

                model TenantWithBigInt {
                    id String @id
                    tenantId BigInt @tenantId
                }

                model TenantWithUuid {
                    id String @id
                    tenantId UUID @tenantId
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should reject optional tenant field")
    void testRejectOptionalTenantField() {
        String schema = """
                model InvalidTenant {
                    id String @id
                    tenantId String? @tenantId
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot be optional"));
    }

    @Test
    @DisplayName("Should reject list tenant field")
    void testRejectListTenantField() {
        String schema = """
                model InvalidTenant {
                    id String @id
                    tenantId String[] @tenantId
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot be a list"));
    }

    @Test
    @DisplayName("Should reject invalid type for tenant field")
    void testRejectInvalidTenantType() {
        String schema = """
                model InvalidTenant {
                    id String @id
                    tenantId Boolean @tenantId
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("must be of type String, UUID, Int, or BigInt"));
    }

    @Test
    @DisplayName("Should reject multiple @tenantId fields on the same model")
    void testRejectMultipleTenantFields() {
        String schema = """
                model InvalidTenant {
                    id String @id
                    tenant1 String @tenantId
                    tenant2 String @tenantId
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot have multiple @tenantId fields"));
    }

    @Test
    @DisplayName("Should reject @@tenant referencing non-existent field")
    void testRejectNonExistentFieldInBlockTenant() {
        String schema = """
                model InvalidTenant {
                    id String @id
                    name String
                    @@tenant(field: "missingField")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("does not exist on model"));
    }
}
