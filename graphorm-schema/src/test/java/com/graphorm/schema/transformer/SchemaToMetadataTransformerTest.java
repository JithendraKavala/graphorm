package com.graphorm.schema.transformer;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.*;
import com.graphorm.core.query.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SchemaToMetadataTransformer Unit Tests")
class SchemaToMetadataTransformerTest {

    @Test
    @DisplayName("Milestone 3 Deliverable: Parse, validate, and transform full schema with Student, Department, StudentDashboard, and Address")
    void shouldTransformFullMilestoneSchema() {
        String schema = """
                datasource db {
                    provider = "postgresql"
                }

                model Student {
                    id           Int         @id @default(autoincrement())
                    name         String
                    email        String      @unique
                    departmentId Int?
                    department   Department? @relation(fields: [departmentId], references: [id])
                    enrollments  Enrollment[]

                    @@map("students")
                    @@index([departmentId])
                }

                model Department {
                    id       Int       @id @default(autoincrement())
                    name     String
                    students Student[]

                    @@map("departments")
                }

                model Enrollment {
                    id          Int   @id @default(autoincrement())
                    studentId   Int
                    gradePoints Float

                    @@map("enrollments")
                }

                view StudentDashboard {
                    studentId      Int     @map("s.id")
                    studentName    String  @map("s.name")
                    departmentName String? @map("d.name")
                    courseCount    Int     @aggregate(count: "e.id")
                    gpa            Float?  @expression("AVG(e.grade_points)")
                    primaryAddress Address?

                    @@from(Student, as: "s")
                    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
                    @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
                    @@groupBy(["s.id", "s.name", "d.name"])
                }

                type Address {
                    street String
                    city   String
                    state  String
                }

                enum Status {
                    ACTIVE
                    INACTIVE
                }
                """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);

        // 1. Verify ModelRegistry general contents
        assertThat(registry.size()).isEqualTo(5); // 3 models + 1 view + 1 composite type
        assertThat(registry.getAllModels()).hasSize(3);
        assertThat(registry.getAllViewModels()).hasSize(1);
        assertThat(registry.getAllCompositeTypes()).hasSize(1);
        assertThat(registry.getAllEnums()).hasSize(1);

        // 2. Verify Student Model
        ModelMetadata student = registry.getModel("Student");
        assertThat(student.name()).isEqualTo("Student");
        assertThat(student.tableName()).isEqualTo("students");
        assertThat(student.entityType()).isEqualTo(EntityType.MODEL);
        assertThat(student.isReadOnly()).isFalse();
        assertThat(student.primaryKeyFields()).containsExactly("id");
        assertThat(student.isPrimaryKey("id")).isTrue();

        FieldMetadata studentId = student.getField("id");
        assertThat(studentId.javaType()).isEqualTo(Integer.class);
        assertThat(studentId.isPrimaryKey()).isTrue();
        assertThat(studentId.isGenerated()).isTrue();
        assertThat(studentId.defaultValue()).isEqualTo("autoincrement()");

        FieldMetadata email = student.getField("email");
        assertThat(email.isUnique()).isTrue();

        FieldMetadata deptRelField = student.getField("department");
        assertThat(deptRelField.isRelation()).isTrue();
        assertThat(student.hasRelation("department")).isTrue();
        RelationMetadata deptRel = student.getRelation("department");
        assertThat(deptRel.sourceModel()).isEqualTo("Student");
        assertThat(deptRel.targetModel()).isEqualTo("Department");
        assertThat(deptRel.foreignKeyFields()).containsExactly("departmentId");
        assertThat(deptRel.referenceFields()).containsExactly("id");

        // 3. Verify Department Model
        ModelMetadata dept = registry.getModel("Department");
        assertThat(dept.tableName()).isEqualTo("departments");
        assertThat(dept.primaryKeyFields()).containsExactly("id");

        // 4. Verify StudentDashboard View Model
        ViewModelMetadata dashboard = registry.getViewModel("StudentDashboard");
        assertThat(dashboard.name()).isEqualTo("StudentDashboard");
        assertThat(dashboard.entityType()).isEqualTo(EntityType.VIEW);
        assertThat(dashboard.isReadOnly()).isTrue();
        assertThat(dashboard.isDatabaseBacked()).isFalse();
        assertThat(dashboard.sourceModelName()).isEqualTo("Student");
        assertThat(dashboard.sourceAlias()).isEqualTo("s");
        assertThat(dashboard.joinDefinitions()).hasSize(2);

        ViewJoinMetadata join1 = dashboard.joinDefinitions().get(0);
        assertThat(join1.joinType()).isEqualTo(JoinType.LEFT);
        assertThat(join1.targetModel()).isEqualTo("Department");
        assertThat(join1.alias()).isEqualTo("d");
        assertThat(join1.onCondition()).isEqualTo("s.departmentId == d.id");

        ViewJoinMetadata join2 = dashboard.joinDefinitions().get(1);
        assertThat(join2.joinType()).isEqualTo(JoinType.LEFT);
        assertThat(join2.targetModel()).isEqualTo("Enrollment");
        assertThat(join2.alias()).isEqualTo("e");
        assertThat(join2.onCondition()).isEqualTo("s.id == e.studentId");

        assertThat(dashboard.groupByColumns()).containsExactly("s.id", "s.name", "d.name");

        FieldMetadata studentIdField = dashboard.getField("studentId");
        assertThat(studentIdField.dbColumnName()).isEqualTo("s.id");

        FieldMetadata courseCount = dashboard.getField("courseCount");
        assertThat(courseCount.isAggregate()).isTrue();
        assertThat(courseCount.aggregateFunction()).isEqualTo("COUNT");
        assertThat(courseCount.expression()).isEqualTo("e.id");

        FieldMetadata gpa = dashboard.getField("gpa");
        assertThat(gpa.expression()).isEqualTo("AVG(e.grade_points)");

        // 5. Verify Composite Type Address
        CompositeTypeMetadata address = registry.getCompositeType("Address");
        assertThat(address.name()).isEqualTo("Address");
        assertThat(address.entityType()).isEqualTo(EntityType.COMPOSITE_TYPE);
        assertThat(address.isReadOnly()).isFalse();
        assertThat(address.fields()).hasSize(3);
        assertThat(address.hasField("street")).isTrue();
        assertThat(address.hasField("city")).isTrue();
        assertThat(address.hasField("state")).isTrue();

        // 6. Verify Enum Status
        EnumMetadata status = registry.getEnum("Status");
        assertThat(status.enumName()).isEqualTo("Status");
        assertThat(status.values()).containsExactly("ACTIVE", "INACTIVE");
        assertThat(status.hasValue("ACTIVE")).isTrue();
        assertThat(status.hasValue("DELETED")).isFalse();
    }

    @Test
    @DisplayName("Should transform database-backed view with @@view and @@map")
    void shouldTransformDatabaseBackedView() {
        String schema = """
                view StudentSummary {
                    id             Int    @id
                    name           String
                    departmentName String @map("dept_name")

                    @@view
                    @@map("vw_student_summary")
                }
                """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);

        assertThat(registry.containsViewModel("StudentSummary")).isTrue();
        ViewModelMetadata view = registry.getViewModel("StudentSummary");
        assertThat(view.isDatabaseBacked()).isTrue();
        assertThat(view.viewName()).isEqualTo("vw_student_summary");
        assertThat(view.isReadOnly()).isTrue();
        assertThat(view.getField("departmentName").dbColumnName()).isEqualTo("dept_name");
    }

    @Test
    @DisplayName("Should verify unified query AST construction against transformed ModelRegistry")
    void shouldConstructQueryAstFromTransformedRegistry() {
        String schema = """
                model Student {
                    id   Int    @id @default(autoincrement())
                    name String
                }
                view StudentDashboard {
                    id   Int    @map("s.id")
                    name String @map("s.name")
                    @@from(Student, as: "s")
                }
                """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        ViewModelMetadata vm = registry.getViewModel("StudentDashboard");

        // Construct SelectQuery AST for the view model
        SelectQuery query = SelectQuery.from(vm.sourceModelName())
                .as(vm.sourceAlias())
                .column("s.id", "id")
                .column("s.name", "name")
                .build();

        assertThat(query.source().identifier()).isEqualTo("Student");
        assertThat(query.tableAlias()).isEqualTo("s");
        assertThat(query.columns()).hasSize(2);

        // Assert query safety guards prevent writing to read-only views
        assertThatThrownBy(() -> QueryGuards.assertWritable(vm, "INSERT"))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform INSERT on read-only view 'StudentDashboard'");

        assertThatThrownBy(() -> QueryGuards.createInsert(vm, Map.of("name", "Test")))
                .isInstanceOf(ReadOnlyViewException.class);
    }

    @Test
    @DisplayName("Should transform @auth directive on fields with multiple or single roles")
    void shouldTransformAuthDirectiveOnFields() {
        String schema = """
                model Employee {
                    id         Int    @id @default(autoincrement())
                    name       String
                    ssn        String @auth(roles: ["ADMIN"])
                    salary     Float  @auth(roles: ["ADMIN", "HR"])
                    notes      String @auth("MANAGER")
                }
                """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        ModelMetadata employee = registry.getModel("Employee");

        FieldMetadata name = employee.getField("name");
        assertThat(name.hasAuthRoles()).isFalse();
        assertThat(name.authRoles()).isEmpty();

        FieldMetadata ssn = employee.getField("ssn");
        assertThat(ssn.hasAuthRoles()).isTrue();
        assertThat(ssn.authRoles()).containsExactly("ADMIN");

        FieldMetadata salary = employee.getField("salary");
        assertThat(salary.hasAuthRoles()).isTrue();
        assertThat(salary.authRoles()).containsExactly("ADMIN", "HR");

        FieldMetadata notes = employee.getField("notes");
        assertThat(notes.hasAuthRoles()).isTrue();
        assertThat(notes.authRoles()).containsExactly("MANAGER");
    }
}
