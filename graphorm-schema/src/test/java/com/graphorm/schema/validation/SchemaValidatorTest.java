package com.graphorm.schema.validation;

import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.exception.SchemaValidationException;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Semantic Validation Engine Unit Tests")
class SchemaValidatorTest {

    @Test
    @DisplayName("Should pass validation for fully compliant schema")
    void shouldPassValidationForValidSchema() {
        String schema = """
                model Student {
                    id           Int         @id @default(autoincrement())
                    name         String
                    departmentId Int?
                    department   Department? @relation(fields: [departmentId], references: [id])
                }

                model Department {
                    id       Int       @id @default(autoincrement())
                    name     String
                    students Student[]
                }

                view StudentDashboard {
                    studentId   Int    @map("s.id")
                    studentName String @map("s.name")
                    courseCount Int    @aggregate(count: "e.id")

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@groupBy(["s.id", "s.name"])
                }

                type Address {
                    street String
                }

                enum Role {
                    ADMIN
                    USER
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("Should reject duplicate entity declarations")
    void shouldRejectDuplicateEntityNames() {
        String schema = """
                model Student {
                    id Int @id
                }
                view Student {
                    id Int
                    @@view
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Duplicate declaration: entity 'Student'"));
    }

    @Test
    @DisplayName("Should reject duplicate field names within same model")
    void shouldRejectDuplicateFieldNames() {
        String schema = """
                model User {
                    id   Int    @id
                    name String
                    name String
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Duplicate field 'name' in 'User'"));
    }

    @Test
    @DisplayName("Should reject duplicate enum values")
    void shouldRejectDuplicateEnumValues() {
        String schema = """
                enum Status {
                    ACTIVE
                    ACTIVE
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Duplicate enum value 'ACTIVE' in enum 'Status'"));
    }

    @Test
    @DisplayName("Should reject model without any primary key")
    void shouldRejectModelWithoutPrimaryKey() {
        String schema = """
                model User {
                    name String
                    age  Int
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Model 'User' must define a primary key"));
    }

    @Test
    @DisplayName("Should reject model with multiple field-level @id attributes")
    void shouldRejectModelWithMultipleFieldIds() {
        String schema = """
                model User {
                    id    Int    @id
                    email String @id
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot have multiple fields annotated with @id"));
    }

    @Test
    @DisplayName("Should reject model with both field @id and block @@id")
    void shouldRejectModelWithBothFieldAndBlockIds() {
        String schema = """
                model User {
                    id    Int    @id
                    email String
                    @@id([id, email])
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("cannot define both field-level @id and block-level @@id"));
    }

    @Test
    @DisplayName("Should reject compound @@id referencing non-existent field")
    void shouldRejectCompoundIdWithNonExistentField() {
        String schema = """
                model User {
                    id Int
                    @@id([id, nonExistent])
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Field 'nonExistent' specified in @@id does not exist"));
    }

    @Test
    @DisplayName("Should reject optional or list primary key field")
    void shouldRejectOptionalOrListPrimaryKey() {
        String schema = """
                model User {
                    id Int? @id
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Primary key field 'id' in model 'User' cannot be optional"));
    }

    @Test
    @DisplayName("Should reject unknown field types")
    void shouldRejectUnknownFieldType() {
        String schema = """
                model User {
                    id   Int @id
                    role NonExistentRole
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Unknown type 'NonExistentRole'"));
    }

    @Test
    @DisplayName("Should reject relation with mismatched fields and references counts")
    void shouldRejectRelationMismatchedFieldsReferences() {
        String schema = """
                model Student {
                    id           Int         @id
                    departmentId Int?
                    department   Department? @relation(fields: [departmentId], references: [id, name])
                }
                model Department {
                    id   Int    @id
                    name String
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Relation 'fields' size (1) does not match 'references' size (2)"));
    }

    @Test
    @DisplayName("Should reject relation referencing non-existent target field")
    void shouldRejectRelationReferencingNonExistentTargetField() {
        String schema = """
                model Student {
                    id           Int         @id
                    departmentId Int?
                    department   Department? @relation(fields: [departmentId], references: [missingField])
                }
                model Department {
                    id Int @id
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Referenced field 'missingField' declared in @relation does not exist on target model 'Department'"));
    }

    @Test
    @DisplayName("Should enforce read-only view invariant: reject @default on view fields")
    void shouldRejectDefaultOnViewFields() {
        String schema = """
                model Student {
                    id Int @id
                }
                view StudentView {
                    id    Int    @default(autoincrement())
                    score Int
                    @@from(Student, as: "s")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Read-only view 'StudentView' cannot declare default value attribute @default"));
    }

    @Test
    @DisplayName("Should enforce read-only view invariant: reject @unique on view fields")
    void shouldRejectUniqueOnViewFields() {
        String schema = """
                model Student {
                    id Int @id
                }
                view StudentView {
                    id    Int    @unique
                    score Int
                    @@from(Student, as: "s")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Read-only view 'StudentView' cannot declare unique constraint @unique"));
    }

    @Test
    @DisplayName("Should reject virtual view without @@from declaration")
    void shouldRejectVirtualViewWithoutFrom() {
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
    @DisplayName("Should reject virtual view referencing unknown source model")
    void shouldRejectVirtualViewWithUnknownSource() {
        String schema = """
                view Dashboard {
                    id Int
                    @@from(NonExistentModel, as: "nem")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("references unknown source model 'NonExistentModel'"));
    }

    @Test
    @DisplayName("Should reject virtual view with duplicate join alias")
    void shouldRejectDuplicateJoinAlias() {
        String schema = """
                model A {
                    id Int @id
                }
                model B {
                    id Int @id
                }
                view Dashboard {
                    id Int
                    @@from(A, as: "s")
                    @@leftJoin(B, as: "s", on: "s.id == s.id")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Duplicate table alias 's' in view 'Dashboard'"));
    }

    @Test
    @DisplayName("Should detect direct circular view dependencies (A -> B -> A)")
    void shouldDetectDirectCircularViewDependencies() {
        String schema = """
                model Base {
                    id Int @id
                }
                view ViewA {
                    id Int
                    @@from(ViewB, as: "b")
                }
                view ViewB {
                    id Int
                    @@from(ViewA, as: "a")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Circular view dependency detected: ViewA -> ViewB -> ViewA")
                || d.message().contains("Circular view dependency detected: ViewB -> ViewA -> ViewB"));
    }

    @Test
    @DisplayName("Should detect multi-step circular view dependencies (A -> B -> C -> A)")
    void shouldDetectMultiStepCircularViewDependencies() {
        String schema = """
                model Base {
                    id Int @id
                }
                view ViewA {
                    id Int
                    @@from(ViewB, as: "b")
                }
                view ViewB {
                    id Int
                    @@from(ViewC, as: "c")
                }
                view ViewC {
                    id Int
                    @@from(ViewA, as: "a")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Circular view dependency detected"));
    }

    @Test
    @DisplayName("ValidationResult throwIfInvalid should throw SchemaValidationException")
    void shouldThrowIfInvalid() {
        String schema = """
                model Bad {
                    name String
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThatThrownBy(() -> result.throwIfInvalid(schema))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("Model 'Bad' must define a primary key");
    }

    @Test
    @DisplayName("Should pass validation for @@auth, @injected, and @@readonly")
    void shouldPassValidationForNewFeatures() {
        String schema = """
                model SecureLog {
                    id        Int    @id @default(autoincrement())
                    message   String
                    tenantId  String @injected(from: "tenantId")
                    author    String @injected("auth.username")

                    @@auth(roles: ["ADMIN", "AUDITOR"])
                    @@readonly
                }

                view SecureLogView {
                    id      Int    @map("l.id")
                    message String @map("l.message")

                    @@from(SecureLog, as: "l")
                    @@auth("AUDITOR")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid @@auth: empty arguments, empty array, or blank role")
    void shouldRejectInvalidAuth() {
        String schemaEmptyArgs = """
                model Log1 {
                    id Int @id
                    @@auth()
                }
                """;
        ValidationResult r1 = SchemaValidator.validate(GraphOrmParserFacade.parse(schemaEmptyArgs));
        assertThat(r1.hasErrors()).isTrue();
        assertThat(r1.getErrors()).anyMatch(d -> d.message().contains("@@auth on 'Log1' must specify at least one role"));

        String schemaEmptyArray = """
                model Log2 {
                    id Int @id
                    @@auth(roles: [])
                }
                """;
        ValidationResult r2 = SchemaValidator.validate(GraphOrmParserFacade.parse(schemaEmptyArray));
        assertThat(r2.hasErrors()).isTrue();
        assertThat(r2.getErrors()).anyMatch(d -> d.message().contains("roles array cannot be empty"));

        String schemaBlankRole = """
                model Log3 {
                    id Int @id
                    @@auth(roles: [" "])
                }
                """;
        ValidationResult r3 = SchemaValidator.validate(GraphOrmParserFacade.parse(schemaBlankRole));
        assertThat(r3.hasErrors()).isTrue();
        assertThat(r3.getErrors()).anyMatch(d -> d.message().contains("Invalid role value"));

        String schemaBadArg = """
                model Log4 {
                    id Int @id
                    @@auth(unknownArg: "ADMIN")
                }
                """;
        ValidationResult r4 = SchemaValidator.validate(GraphOrmParserFacade.parse(schemaBadArg));
        assertThat(r4.hasErrors()).isTrue();
        assertThat(r4.getErrors()).anyMatch(d -> d.message().contains("Unknown argument 'unknownArg' in @@auth"));
    }

    @Test
    @DisplayName("Should reject invalid @injected: missing from, blank from, or unknown argument")
    void shouldRejectInvalidInjected() {
        String schemaEmpty = """
                model Doc1 {
                    id  Int    @id
                    val String @injected()
                }
                """;
        ValidationResult r1 = SchemaValidator.validate(GraphOrmParserFacade.parse(schemaEmpty));
        assertThat(r1.hasErrors()).isTrue();
        assertThat(r1.getErrors()).anyMatch(d -> d.message().contains("Directive @injected on field 'val' in 'Doc1' must specify a non-blank 'from' string"));

        String schemaBlank = """
                model Doc2 {
                    id  Int    @id
                    val String @injected(from: "   ")
                }
                """;
        ValidationResult r2 = SchemaValidator.validate(GraphOrmParserFacade.parse(schemaBlank));
        assertThat(r2.hasErrors()).isTrue();
        assertThat(r2.getErrors()).anyMatch(d -> d.message().contains("must specify a non-blank 'from' string"));

        String schemaBadArg = """
                model Doc3 {
                    id  Int    @id
                    val String @injected(wrong: "tenant")
                }
                """;
        ValidationResult r3 = SchemaValidator.validate(GraphOrmParserFacade.parse(schemaBadArg));
        assertThat(r3.hasErrors()).isTrue();
        assertThat(r3.getErrors()).anyMatch(d -> d.message().contains("Unknown argument 'wrong' in @injected"));
    }
}
