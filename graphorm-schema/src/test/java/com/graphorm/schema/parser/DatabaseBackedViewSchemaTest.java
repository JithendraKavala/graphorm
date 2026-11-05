package com.graphorm.schema.parser;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.ViewModelNode;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Database-Backed View Schema & AST Tests (TASK-7.1)")
class DatabaseBackedViewSchemaTest {

    @Test
    @DisplayName("Should parse and validate database-backed view with @@view and @@map")
    void shouldParseDatabaseBackedView() {
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                view StudentSummary {
                    id             Int    @id
                    name           String
                    departmentName String @map("dept_name")
                    totalCredits   Int    @map("total_credits")

                    @@view
                    @@map("vw_student_summary")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        assertThat(doc.viewModels()).hasSize(1);

        ViewModelNode vmNode = doc.viewModels().get(0);
        assertThat(vmNode.name()).isEqualTo("StudentSummary");
        assertThat(vmNode.isDatabaseBacked()).isTrue();
        assertThat(vmNode.hasAttribute("view")).isTrue();
        assertThat(vmNode.hasAttribute("map")).isTrue();
        assertThat(vmNode.fields()).hasSize(4);

        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        assertThat(registry.containsViewModel("StudentSummary")).isTrue();

        ViewModelMetadata vm = registry.getViewModel("StudentSummary");
        assertThat(vm.name()).isEqualTo("StudentSummary");
        assertThat(vm.viewName()).isEqualTo("vw_student_summary");
        assertThat(vm.isDatabaseBacked()).isTrue();
        assertThat(vm.isReadOnly()).isTrue();

        FieldMetadata idField = vm.getField("id");
        assertThat(idField.isPrimaryKey()).isTrue();
        assertThat(idField.dbColumnName()).isEqualTo("id");

        FieldMetadata deptNameField = vm.getField("departmentName");
        assertThat(deptNameField.dbColumnName()).isEqualTo("dept_name");

        FieldMetadata creditsField = vm.getField("totalCredits");
        assertThat(creditsField.dbColumnName()).isEqualTo("total_credits");
    }

    @Test
    @DisplayName("Should support default lowercase view name when @@map is omitted")
    void shouldDefaultViewNameWhenMapOmitted() {
        String schema = """
                view DepartmentSummary {
                    id   Int    @id
                    name String

                    @@view
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        ViewModelMetadata vm = registry.getViewModel("DepartmentSummary");
        assertThat(vm.isDatabaseBacked()).isTrue();
        assertThat(vm.viewName()).isEqualTo("departmentsummary");
    }

    @Test
    @DisplayName("Should support named arguments in @@map and @map")
    void shouldSupportNamedArgumentsInMap() {
        String schema = """
                view StudentSummary {
                    id             Int    @id
                    departmentName String @map(name: "dept_name")

                    @@view
                    @@map(name: "vw_student_summary")
                }
                """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        ViewModelMetadata vm = registry.getViewModel("StudentSummary");
        assertThat(vm.viewName()).isEqualTo("vw_student_summary");
        assertThat(vm.getField("departmentName").dbColumnName()).isEqualTo("dept_name");
    }

    @Test
    @DisplayName("Should reject database-backed view declaring @@from")
    void shouldRejectDatabaseBackedViewWithFrom() {
        String schema = """
                model Student {
                    id Int @id
                }

                view StudentSummary {
                    id Int

                    @@view
                    @@from(Student, as: "s")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot declare @@from"));
    }

    @Test
    @DisplayName("Should reject database-backed view declaring join attributes")
    void shouldRejectDatabaseBackedViewWithJoins() {
        String schema = """
                model Department {
                    id Int @id
                }

                view StudentSummary {
                    id Int

                    @@view
                    @@leftJoin(Department, as: "d", on: "id == d.id")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot declare join attributes"));
    }

    @Test
    @DisplayName("Should reject database-backed view declaring @@groupBy")
    void shouldRejectDatabaseBackedViewWithGroupBy() {
        String schema = """
                view StudentSummary {
                    id Int

                    @@view
                    @@groupBy(["id"])
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot declare @@groupBy"));
    }

    @Test
    @DisplayName("Should reject view with neither @@view nor @@from")
    void shouldRejectViewWithoutStrategy() {
        String schema = """
                view OrphanView {
                    id Int
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("must declare a root source model using @@from"));
    }

    @Test
    @DisplayName("Should reject @default on database-backed view fields")
    void shouldRejectDefaultOnDatabaseBackedViewField() {
        String schema = """
                view StudentSummary {
                    id    Int @id @default(1)
                    score Int

                    @@view
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot declare default value attribute @default"));
    }

    @Test
    @DisplayName("Should reject @unique on database-backed view fields")
    void shouldRejectUniqueOnDatabaseBackedViewField() {
        String schema = """
                view StudentSummary {
                    id    Int    @id
                    email String @unique

                    @@view
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot declare unique constraint @unique"));
    }
}
