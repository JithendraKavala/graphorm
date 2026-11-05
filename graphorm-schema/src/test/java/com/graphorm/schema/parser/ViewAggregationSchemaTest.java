package com.graphorm.schema.parser;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.schema.ast.AggregateAttributeNode;
import com.graphorm.schema.ast.ExpressionAttributeNode;
import com.graphorm.schema.ast.FieldNode;
import com.graphorm.schema.ast.GroupByAttributeNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.ViewModelNode;
import com.graphorm.schema.exception.SchemaValidationException;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import com.graphorm.schema.validation.Diagnostic;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("View Aggregations, Computed Fields & GROUP BY Schema Tests (TASK-9.1)")
class ViewAggregationSchemaTest {

    @Nested
    @DisplayName("AST & Parser Tests for Stage 4 Views")
    class AstAndParserTests {

        @Test
        @DisplayName("Should parse @aggregate, @expression, and @@groupBy into strongly typed AST nodes")
        void shouldParseAggregationsAndGroupBy() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }

                    model Student {
                        id   Int    @id
                        name String
                    }

                    model Enrollment {
                        id          Int   @id
                        studentId   Int
                        credits     Int
                        gradePoints Float
                    }

                    view StudentDashboard {
                        studentId    Int    @map("s.id")
                        studentName  String @map("s.name")
                        courseCount  Int    @aggregate(count: "e.id")
                        totalCredits Int    @aggregate(sum: "e.credits")
                        avgGrade     Float  @aggregate(avg: "e.gradePoints")
                        minGrade     Float  @aggregate(min: "e.gradePoints")
                        maxGrade     Float  @aggregate(max: "e.gradePoints")
                        gpa          Float? @expression("AVG(e.grade_points)")

                        @@from(Student, as: "s")
                        @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                        @@groupBy(["s.id", "s.name"])
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            Optional<ViewModelNode> vmOpt = doc.findViewModel("StudentDashboard");
            assertThat(vmOpt).isPresent();

            ViewModelNode vm = vmOpt.get();
            assertThat(vm.groupByClause()).isPresent();
            GroupByAttributeNode groupByNode = vm.groupByClause().get();
            assertThat(groupByNode.columns()).containsExactly("s.id", "s.name");

            // Check courseCount @aggregate(count: "e.id")
            FieldNode courseCount = vm.findField("courseCount").orElseThrow();
            assertThat(courseCount.isAggregate()).isTrue();
            Optional<AggregateAttributeNode> aggClause = courseCount.aggregateClause();
            assertThat(aggClause).isPresent();
            assertThat(aggClause.get().function()).isEqualTo("count");
            assertThat(aggClause.get().expression()).isEqualTo("e.id");

            // Check totalCredits @aggregate(sum: "e.credits")
            FieldNode totalCredits = vm.findField("totalCredits").orElseThrow();
            assertThat(totalCredits.isAggregate()).isTrue();
            assertThat(totalCredits.aggregateClause()).isPresent();
            assertThat(totalCredits.aggregateClause().get().function()).isEqualTo("sum");
            assertThat(totalCredits.aggregateClause().get().expression()).isEqualTo("e.credits");

            // Check gpa @expression("AVG(e.grade_points)")
            FieldNode gpa = vm.findField("gpa").orElseThrow();
            assertThat(gpa.isExpression()).isTrue();
            Optional<ExpressionAttributeNode> exprClause = gpa.expressionClause();
            assertThat(exprClause).isPresent();
            assertThat(exprClause.get().expression()).isEqualTo("AVG(e.grade_points)");
        }
    }

    @Nested
    @DisplayName("Semantic Validation Tests for Aggregations and Group By")
    class SemanticValidationTests {

        @Test
        @DisplayName("Should reject unsupported aggregate function")
        void shouldRejectUnsupportedAggregateFunction() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    model Student {
                        id Int @id
                    }
                    view Dashboard {
                        studentId Int @map("s.id")
                        metric    Int @aggregate(median: "s.id")
                        @@from(Student, as: "s")
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("Unsupported aggregate function 'median'"));
        }

        @Test
        @DisplayName("Should reject @aggregate without target expression")
        void shouldRejectAggregateWithoutExpression() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    model Student {
                        id Int @id
                    }
                    view Dashboard {
                        studentId Int @map("s.id")
                        metric    Int @aggregate
                        @@from(Student, as: "s")
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("Aggregate directive @aggregate on field 'metric' must specify a function"));
        }

        @Test
        @DisplayName("Should reject both @aggregate and @expression on the same field")
        void shouldRejectBothAggregateAndExpression() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    model Student {
                        id Int @id
                    }
                    view Dashboard {
                        studentId Int @map("s.id")
                        metric    Int @aggregate(count: "s.id") @expression("COUNT(s.id)")
                        @@from(Student, as: "s")
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("cannot declare both @aggregate and @expression"));
        }

        @Test
        @DisplayName("Should reject @aggregate on regular models")
        void shouldRejectAggregateOnModel() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    model Student {
                        id          Int @id
                        courseCount Int @aggregate(count: "id")
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("Model 'Student' cannot declare @aggregate on field 'courseCount'"));
        }

        @Test
        @DisplayName("Should reject @expression on regular models")
        void shouldRejectExpressionOnModel() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    model Student {
                        id       Int    @id
                        fullName String @expression("first_name || ' ' || last_name")
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("Model 'Student' cannot declare @expression on field 'fullName'"));
        }

        @Test
        @DisplayName("Should reject @@groupBy on regular models")
        void shouldRejectGroupByOnModel() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    model Student {
                        id Int @id
                        @@groupBy(["id"])
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("Model 'Student' cannot declare block @@groupBy"));
        }

        @Test
        @DisplayName("Should reject empty @@groupBy in view")
        void shouldRejectEmptyGroupBy() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    model Student {
                        id Int @id
                    }
                    view Dashboard {
                        studentId Int @map("s.id")
                        @@from(Student, as: "s")
                        @@groupBy([])
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("Block @@groupBy in view 'Dashboard' cannot be empty"));
        }

        @Test
        @DisplayName("Should reject @@groupBy on database-backed views")
        void shouldRejectGroupByOnDbBackedView() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }
                    view StudentSummary {
                        id   Int @id
                        name String
                        @@view
                        @@groupBy(["id"])
                    }
                    """;

            SchemaDocument doc = GraphOrmParserFacade.parse(schema);
            ValidationResult result = SchemaValidator.validate(doc);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .extracting(Diagnostic::message)
                    .anyMatch(m -> m.contains("Database-backed view 'StudentSummary' with @@view cannot declare @@groupBy"));
        }
    }

    @Nested
    @DisplayName("Metadata Transformation Tests")
    class MetadataTransformationTests {

        @Test
        @DisplayName("Should transform @aggregate, @expression, and @@groupBy into ViewModelMetadata")
        void shouldTransformToViewModelMetadata() {
            String schema = """
                    datasource db {
                        provider = "postgresql"
                    }

                    model Department {
                        id   Int    @id
                        name String
                    }

                    model Student {
                        id           Int    @id
                        name         String
                        departmentId Int?
                    }

                    model Enrollment {
                        id          Int   @id
                        studentId   Int
                        gradePoints Float
                    }

                    view StudentDashboard {
                        studentId      Int     @map("s.id")
                        studentName    String  @map("s.name")
                        departmentName String? @map("d.name")
                        courseCount    Int     @aggregate(count: "e.id")
                        gpa            Float?  @expression("AVG(e.grade_points)")

                        @@from(Student, as: "s")
                        @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                        @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                        @@groupBy(["s.id", "s.name", "d.name"])
                    }
                    """;

            ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
            ViewModelMetadata vm = registry.getViewModel("StudentDashboard");

            assertThat(vm.groupByColumns()).containsExactly("s.id", "s.name", "d.name");

            FieldMetadata courseCount = vm.getField("courseCount");
            assertThat(courseCount.isAggregate()).isTrue();
            assertThat(courseCount.aggregateFunction()).isEqualTo("COUNT");
            assertThat(courseCount.expression()).isEqualTo("e.id");

            FieldMetadata gpa = vm.getField("gpa");
            assertThat(gpa.isAggregate()).isFalse();
            assertThat(gpa.expression()).isEqualTo("AVG(e.grade_points)");

            FieldMetadata studentName = vm.getField("studentName");
            assertThat(studentName.isAggregate()).isFalse();
            assertThat(studentName.expression()).isNull();
            assertThat(studentName.dbColumnName()).isEqualTo("s.name");
        }
    }
}
