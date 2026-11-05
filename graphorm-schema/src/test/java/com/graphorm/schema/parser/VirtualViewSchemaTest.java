package com.graphorm.schema.parser;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewJoinMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.JoinType;
import com.graphorm.schema.ast.AstVisitor;
import com.graphorm.schema.ast.FromAttributeNode;
import com.graphorm.schema.ast.JoinAttributeNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.ViewModelNode;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Virtual GraphORM-Defined View Schema & AST Tests (TASK-8.1)")
class VirtualViewSchemaTest {

    @Test
    @DisplayName("Should parse and validate virtual view with @@from, @@leftJoin, and @@innerJoin")
    void shouldParseVirtualJoinedView() {
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                model Student {
                    id           Int    @id
                    name         String
                    departmentId Int?

                    @@map("students")
                }

                model Department {
                    id   Int    @id
                    name String

                    @@map("departments")
                }

                model Enrollment {
                    id        Int @id
                    studentId Int
                    courseId  Int

                    @@map("enrollments")
                }

                view StudentDashboard {
                    studentId      Int     @map("s.id")
                    studentName    String  @map("s.name")
                    departmentName String? @map("d.name")
                    courseId       Int?    @map("e.course_id")

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@innerJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        assertThat(doc.viewModels()).hasSize(1);

        ViewModelNode vmNode = doc.viewModels().get(0);
        assertThat(vmNode.name()).isEqualTo("StudentDashboard");
        assertThat(vmNode.isDatabaseBacked()).isFalse();

        // 1. Verify FromAttributeNode
        assertThat(vmNode.fromClause()).isPresent();
        FromAttributeNode fromClause = vmNode.fromClause().get();
        assertThat(fromClause.modelName()).isEqualTo("Student");
        assertThat(fromClause.alias()).isEqualTo("s");

        // 2. Verify JoinAttributeNodes
        List<JoinAttributeNode> joins = vmNode.joinClauses();
        assertThat(joins).hasSize(2);

        JoinAttributeNode leftJoin = joins.get(0);
        assertThat(leftJoin.joinType()).isEqualTo(JoinType.LEFT);
        assertThat(leftJoin.modelName()).isEqualTo("Department");
        assertThat(leftJoin.alias()).isEqualTo("d");
        assertThat(leftJoin.onCondition()).isEqualTo("s.departmentId == d.id");

        JoinAttributeNode innerJoin = joins.get(1);
        assertThat(innerJoin.joinType()).isEqualTo(JoinType.INNER);
        assertThat(innerJoin.modelName()).isEqualTo("Enrollment");
        assertThat(innerJoin.alias()).isEqualTo("e");
        assertThat(innerJoin.onCondition()).isEqualTo("s.id == e.studentId");

        // 3. Verify AstVisitor traversal
        List<String> visitedNodes = new ArrayList<>();
        AstVisitor<Void> visitor = new AstVisitor<>() {
            @Override
            public Void visitFromAttribute(FromAttributeNode fromAttribute) {
                visitedNodes.add("from:" + fromAttribute.modelName() + " as " + fromAttribute.alias());
                return null;
            }

            @Override
            public Void visitJoinAttribute(JoinAttributeNode joinAttribute) {
                visitedNodes.add("join:" + joinAttribute.joinType() + " " + joinAttribute.modelName() + " as " + joinAttribute.alias());
                return null;
            }
        };

        fromClause.accept(visitor);
        leftJoin.accept(visitor);
        innerJoin.accept(visitor);

        assertThat(visitedNodes).containsExactly(
                "from:Student as s",
                "join:LEFT Department as d",
                "join:INNER Enrollment as e"
        );

        // 4. Validate schema
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.isValid()).isTrue();
        assertThat(validation.hasErrors()).isFalse();

        // 5. Transform to ModelRegistry & ViewModelMetadata
        ModelRegistry registry = SchemaToMetadataTransformer.transform(doc);
        ViewModelMetadata vm = registry.getViewModel("StudentDashboard");
        assertThat(vm.isDatabaseBacked()).isFalse();
        assertThat(vm.isReadOnly()).isTrue();
        assertThat(vm.sourceModelName()).isEqualTo("Student");
        assertThat(vm.sourceAlias()).isEqualTo("s");
        assertThat(vm.joinDefinitions()).hasSize(2);

        ViewJoinMetadata join1 = vm.joinDefinitions().get(0);
        assertThat(join1.joinType()).isEqualTo(JoinType.LEFT);
        assertThat(join1.targetModel()).isEqualTo("Department");
        assertThat(join1.alias()).isEqualTo("d");
        assertThat(join1.onCondition()).isEqualTo("s.departmentId == d.id");

        ViewJoinMetadata join2 = vm.joinDefinitions().get(1);
        assertThat(join2.joinType()).isEqualTo(JoinType.INNER);
        assertThat(join2.targetModel()).isEqualTo("Enrollment");
        assertThat(join2.alias()).isEqualTo("e");
        assertThat(join2.onCondition()).isEqualTo("s.id == e.studentId");
    }

    @Test
    @DisplayName("Should reject virtual view with unknown source model in @@from")
    void shouldRejectUnknownSourceModel() {
        String schema = """
                view InvalidView {
                    id Int

                    @@from(UnknownModel, as: "u")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("references unknown source model 'UnknownModel'"));
    }

    @Test
    @DisplayName("Should reject virtual view with unknown target model in join")
    void shouldRejectUnknownJoinTargetModel() {
        String schema = """
                model Student {
                    id Int @id
                }

                view InvalidView {
                    id Int

                    @@from(Student, as: "s")
                    @@leftJoin(UnknownDepartment, as: "d", on: "s.id == d.studentId")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("references unknown model 'UnknownDepartment'"));
    }

    @Test
    @DisplayName("Should reject duplicate aliases across from and joins")
    void shouldRejectDuplicateAlias() {
        String schema = """
                model Student {
                    id Int @id
                }

                model Department {
                    id Int @id
                }

                view InvalidView {
                    id Int

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "s", on: "s.id == s.id")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Duplicate table alias 's'"));
    }
}
