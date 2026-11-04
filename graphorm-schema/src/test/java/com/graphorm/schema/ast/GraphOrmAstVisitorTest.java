package com.graphorm.schema.ast;

import com.graphorm.schema.parser.GraphOrmParserFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GraphOrm AST Hierarchy & Visitor Unit Tests")
class GraphOrmAstVisitorTest {

    @Test
    @DisplayName("Should visit and construct strongly typed AST with source coordinates")
    void shouldConstructStronglyTypedAstWithCoordinates() {
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                model Student {
                    id           Int         @id @default(autoincrement())
                    name         String      @map("full_name")
                    departmentId Int?
                    department   Department? @relation(fields: [departmentId], references: [id])

                    @@map("students")
                    @@index([departmentId])
                }

                model Department {
                    id       Int       @id
                    name     String
                    students Student[]
                }

                view StudentDashboard {
                    studentId   Int    @map("s.id")
                    studentName String @map("s.name")
                    courseCount Int    @aggregate(count: "e.id")
                    gpa         Float? @expression("AVG(e.grade)")

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@groupBy(["s.id", "s.name"])
                }

                type Address {
                    street String
                    city   String
                }

                enum Status {
                    ACTIVE @map("A")
                    INACTIVE
                    @@map("user_status")
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema, "test_schema.graphorm");

        assertThat(doc.position().sourceFile()).isEqualTo("test_schema.graphorm");
        assertThat(doc.position().line()).isGreaterThanOrEqualTo(1);

        // Models
        assertThat(doc.models()).hasSize(2);
        ModelNode student = doc.findModel("Student").orElseThrow();
        assertThat(student.name()).isEqualTo("Student");
        assertThat(student.position().line()).isEqualTo(5);
        assertThat(student.fields()).hasSize(4);
        assertThat(student.attributes()).hasSize(2);
        assertThat(student.mapAttribute()).isPresent();
        assertThat(student.findField("id")).isPresent();

        FieldNode idField = student.findField("id").orElseThrow();
        assertThat(idField.isPrimaryKey()).isTrue();
        assertThat(idField.isOptional()).isFalse();
        assertThat(idField.isList()).isFalse();
        assertThat(idField.defaultAttribute()).isPresent();

        FieldNode nameField = student.findField("name").orElseThrow();
        assertThat(nameField.mapAttribute()).isPresent();

        FieldNode deptRel = student.findField("department").orElseThrow();
        assertThat(deptRel.relationAttribute()).isPresent();
        assertThat(deptRel.isOptional()).isTrue();

        // View model
        assertThat(doc.viewModels()).hasSize(1);
        ViewModelNode vm = doc.findViewModel("StudentDashboard").orElseThrow();
        assertThat(vm.name()).isEqualTo("StudentDashboard");
        assertThat(vm.isDatabaseBacked()).isFalse();
        assertThat(vm.fromAttribute()).isPresent();
        assertThat(vm.joinAttributes()).hasSize(1);
        assertThat(vm.groupByAttribute()).isPresent();
        assertThat(vm.fields()).hasSize(4);

        FieldNode courseCount = vm.findField("courseCount").orElseThrow();
        assertThat(courseCount.aggregateAttribute()).isPresent();

        FieldNode gpa = vm.findField("gpa").orElseThrow();
        assertThat(gpa.expressionAttribute()).isPresent();

        // Composite type
        assertThat(doc.compositeTypes()).hasSize(1);
        CompositeTypeNode addr = doc.findCompositeType("Address").orElseThrow();
        assertThat(addr.name()).isEqualTo("Address");
        assertThat(addr.fields()).hasSize(2);

        // Enum
        assertThat(doc.enums()).hasSize(1);
        EnumNode status = doc.findEnum("Status").orElseThrow();
        assertThat(status.name()).isEqualTo("Status");
        assertThat(status.values()).hasSize(2);
        assertThat(status.findValue("ACTIVE").orElseThrow().findAttribute("map")).isPresent();
        assertThat(status.mapAttribute()).isPresent();
    }

    @Test
    @DisplayName("Should enforce strict immutability on AST collections")
    void shouldEnforceStrictImmutability() {
        String schema = """
                model User {
                    id Int @id
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ModelNode model = doc.models().get(0);

        assertThatThrownBy(() -> doc.models().add(model))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.fields().add(model.fields().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.attributes().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should support traversing AST with AstVisitor")
    void shouldSupportAstVisitorTraversal() {
        String schema = """
                model A {
                    id Int @id
                }
                model B {
                    id Int @id
                }
                view V {
                    id Int
                    @@view
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);

        AtomicInteger modelCount = new AtomicInteger();
        AtomicInteger viewCount = new AtomicInteger();

        AstVisitor<Void> visitor = new AstVisitor<>() {
            @Override
            public Void visitModel(ModelNode model) {
                modelCount.incrementAndGet();
                return null;
            }

            @Override
            public Void visitViewModel(ViewModelNode viewModel) {
                viewCount.incrementAndGet();
                return null;
            }
        };

        for (ModelNode m : doc.models()) {
            m.accept(visitor);
        }
        for (ViewModelNode vm : doc.viewModels()) {
            vm.accept(visitor);
        }

        assertThat(modelCount.get()).isEqualTo(2);
        assertThat(viewCount.get()).isEqualTo(1);
    }
}
