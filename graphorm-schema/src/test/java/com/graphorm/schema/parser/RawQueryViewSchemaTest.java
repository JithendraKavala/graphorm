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

@DisplayName("Raw SQL Query View Schema & AST Tests (Strategy C: @@query)")
class RawQueryViewSchemaTest {

    @Test
    @DisplayName("Should parse and validate raw SQL view with single-line @@query")
    void shouldParseRawQueryViewWithSingleLine() {
        String schema = """
                view UserUnion {
                    id   Int    @id
                    name String
                    role String

                    @@query("SELECT id, name, 'student' AS role FROM student UNION ALL SELECT id, name, 'instructor' AS role FROM instructor")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        assertThat(doc.viewModels()).hasSize(1);

        ViewModelNode vmNode = doc.viewModels().get(0);
        assertThat(vmNode.name()).isEqualTo("UserUnion");
        assertThat(vmNode.isRawQuery()).isTrue();
        assertThat(vmNode.hasAttribute("query")).isTrue();
        assertThat(vmNode.queryClause()).isPresent();
        assertThat(vmNode.queryClause().get()).contains("UNION ALL");

        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        assertThat(registry.containsViewModel("UserUnion")).isTrue();

        ViewModelMetadata vm = registry.getViewModel("UserUnion");
        assertThat(vm.name()).isEqualTo("UserUnion");
        assertThat(vm.isRawQuery()).isTrue();
        assertThat(vm.rawQuery()).contains("UNION ALL");
        assertThat(vm.isReadOnly()).isTrue();
        assertThat(vm.isDatabaseBacked()).isFalse();
        assertThat(vm.isVirtual()).isFalse();

        FieldMetadata idField = vm.getField("id");
        assertThat(idField.isPrimaryKey()).isTrue();
    }

    @Test
    @DisplayName("Should parse and validate raw SQL view with multiline triple-quoted @@query")
    void shouldParseRawQueryViewWithTripleQuotes() {
        String schema = """
                view ActiveMembers {
                    id    Int    @id
                    title String

                    @@query(\"\"\"
                        SELECT s.id, s.name AS title
                        FROM student s
                        WHERE s.gpa > 3.5
                        UNION
                        SELECT i.id, i.name AS title
                        FROM instructor i
                    \"\"\")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ViewModelNode vmNode = doc.viewModels().get(0);
        assertThat(vmNode.isRawQuery()).isTrue();
        assertThat(vmNode.queryClause().orElse("")).contains("WHERE s.gpa > 3.5").contains("UNION");

        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        ViewModelMetadata vm = registry.getViewModel("ActiveMembers");
        assertThat(vm.isRawQuery()).isTrue();
        assertThat(vm.rawQuery()).contains("WHERE s.gpa > 3.5");
    }

    @Test
    @DisplayName("Should parse and validate CTE (WITH clause) query view")
    void shouldSupportWithCteQueryView() {
        String schema = """
                view CteSummary {
                    id    Int @id
                    total Int

                    @@query(\"\"\"
                        WITH totals AS (
                            SELECT department_id, COUNT(*) as cnt
                            FROM student
                            GROUP BY department_id
                        )
                        SELECT department_id AS id, cnt AS total
                        FROM totals
                    \"\"\")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        ViewModelMetadata vm = registry.getViewModel("CteSummary");
        assertThat(vm.isRawQuery()).isTrue();
        assertThat(vm.rawQuery()).startsWith("WITH totals AS");
    }

    @Test
    @DisplayName("Should reject @@query combined with @@view")
    void shouldRejectQueryWithDatabaseBackedView() {
        String schema = """
                view ConflictView {
                    id Int @id
                    @@view
                    @@query("SELECT id FROM student")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.message().contains("cannot declare @@query"));
    }

    @Test
    @DisplayName("Should reject @@query combined with @@from")
    void shouldRejectQueryWithVirtualFrom() {
        String schema = """
                model Student {
                    id Int @id
                }
                view ConflictView {
                    id Int @id
                    @@from(Student, as: "s")
                    @@query("SELECT id FROM student")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.message().contains("cannot declare @@from"));
    }

    @Test
    @DisplayName("Should reject @@query combined with @@groupBy")
    void shouldRejectQueryWithGroupBy() {
        String schema = """
                view ConflictView {
                    id Int @id
                    @@groupBy([id])
                    @@query("SELECT id FROM student")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.message().contains("cannot declare @@groupBy"));
    }

    @Test
    @DisplayName("Should reject @@query not starting with SELECT or WITH")
    void shouldRejectInvalidQueryStartingKeyword() {
        String schema = """
                view BadQueryView {
                    id Int @id
                    @@query("EXEC get_students")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.message().contains("starting with SELECT or WITH"));
    }

    @Test
    @DisplayName("Should reject @@query containing forbidden DDL/DML keywords")
    void shouldRejectForbiddenDdlDml() {
        String schema = """
                view MaliciousView {
                    id Int @id
                    @@query("SELECT id FROM student; DROP TABLE student")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.message().contains("forbidden DDL/DML statement: DROP"));
    }
}
