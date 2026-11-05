package com.graphorm.graphql.schema;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphQL SDL Type Inspector & Model Matching Tests (TASK-10.1)")
class GraphQlTypeInspectorTest {

    private ModelRegistry modelRegistry;

    @BeforeEach
    void setUp() {
        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("email", String.class).build())
                .addPrimaryKeyField("id")
                .build();

        ModelMetadata department = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addPrimaryKeyField("id")
                .build();

        ViewModelMetadata dashboard = ViewModelMetadata.builder("StudentDashboard")
                .viewName("vw_student_dashboard")
                .databaseBacked(true)
                .addField(FieldMetadata.builder("studentId", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("studentName", String.class).build())
                .addField(FieldMetadata.builder("courseCount", Integer.class).build())
                .build();

        modelRegistry = ModelRegistry.builder()
                .addModel(student)
                .addModel(department)
                .addViewModel(dashboard)
                .build();
    }

    @Test
    @DisplayName("inspect: matches model queries and mutations, view queries, and rejects view mutations")
    void testInspectOperations() {
        String sdl = """
                type Student {
                    id: ID!
                    name: String!
                    email: String!
                }
                
                type Department {
                    id: ID!
                    name: String!
                }
                
                type StudentDashboard {
                    studentId: Int!
                    studentName: String!
                    courseCount: Int!
                }
                
                input StudentInput {
                    name: String!
                    email: String!
                }
                
                input DashboardInput {
                    studentName: String!
                }
                
                type Query {
                    students: [Student!]!
                    student(id: ID!): Student
                    departments: [Department!]!
                    department(id: ID!): Department
                    studentDashboards: [StudentDashboard!]!
                    studentDashboard(studentId: Int!): StudentDashboard
                    unrelatedField: String
                }
                
                type Mutation {
                    createStudent(input: StudentInput!): Student!
                    updateStudent(id: ID!, input: StudentInput!): Student!
                    deleteStudent(id: ID!): Boolean!
                    createStudentDashboard(input: DashboardInput!): StudentDashboard!
                }
                """;

        GraphQlTypeInspector inspector = GraphQlTypeInspector.parse(sdl, modelRegistry);
        List<GraphQlOperationBinding> bindings = inspector.inspect();

        // 1. Verify Model list query
        Optional<GraphQlOperationBinding> studentsQuery = inspector.findQueryBinding("students");
        assertThat(studentsQuery).isPresent();
        assertThat(studentsQuery.get().kind()).isEqualTo(GraphQlOperationKind.MODEL_LIST_QUERY);
        assertThat(studentsQuery.get().entity().name()).isEqualTo("Student");

        // 2. Verify Model single query
        Optional<GraphQlOperationBinding> studentQuery = inspector.findQueryBinding("student");
        assertThat(studentQuery).isPresent();
        assertThat(studentQuery.get().kind()).isEqualTo(GraphQlOperationKind.MODEL_SINGLE_QUERY);
        assertThat(studentQuery.get().idArgumentName()).isEqualTo("id");

        // 3. Verify View Model list query
        Optional<GraphQlOperationBinding> dashboardsQuery = inspector.findQueryBinding("studentDashboards");
        assertThat(dashboardsQuery).isPresent();
        assertThat(dashboardsQuery.get().kind()).isEqualTo(GraphQlOperationKind.VIEW_LIST_QUERY);
        assertThat(dashboardsQuery.get().entity().name()).isEqualTo("StudentDashboard");

        // 4. Verify View Model single query with studentId argument
        Optional<GraphQlOperationBinding> dashboardQuery = inspector.findQueryBinding("studentDashboard");
        assertThat(dashboardQuery).isPresent();
        assertThat(dashboardQuery.get().kind()).isEqualTo(GraphQlOperationKind.VIEW_SINGLE_QUERY);
        assertThat(dashboardQuery.get().idArgumentName()).isEqualTo("studentId");

        // 5. Verify Unrelated query field is ignored
        assertThat(inspector.findQueryBinding("unrelatedField")).isEmpty();

        // 6. Verify Model mutations
        Optional<GraphQlOperationBinding> createStudent = inspector.findMutationBinding("createStudent");
        assertThat(createStudent).isPresent();
        assertThat(createStudent.get().kind()).isEqualTo(GraphQlOperationKind.MODEL_CREATE_MUTATION);

        Optional<GraphQlOperationBinding> updateStudent = inspector.findMutationBinding("updateStudent");
        assertThat(updateStudent).isPresent();
        assertThat(updateStudent.get().kind()).isEqualTo(GraphQlOperationKind.MODEL_UPDATE_MUTATION);

        Optional<GraphQlOperationBinding> deleteStudent = inspector.findMutationBinding("deleteStudent");
        assertThat(deleteStudent).isPresent();
        assertThat(deleteStudent.get().kind()).isEqualTo(GraphQlOperationKind.MODEL_DELETE_MUTATION);

        // 7. Strictly verify View Model mutation rejection (TASK-10.4)
        assertThat(inspector.findMutationBinding("createStudentDashboard")).isEmpty();
        List<GraphQlOperationBinding> rejected = inspector.getRejectedViewMutations();
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).fieldName()).isEqualTo("createStudentDashboard");
        assertThat(rejected.get(0).kind()).isEqualTo(GraphQlOperationKind.VIEW_MUTATION_REJECTED);
        assertThat(rejected.get(0).entity().name()).isEqualTo("StudentDashboard");
    }

    @Test
    @DisplayName("inspect: matches operations by naming conventions when types differ")
    void testConventionMatching() {
        String sdl = """
                type StudentPayload {
                    id: ID!
                    name: String!
                }
                
                type Query {
                    students: [StudentPayload]
                }
                """;

        GraphQlTypeInspector inspector = GraphQlTypeInspector.parse(sdl, modelRegistry);
        List<GraphQlOperationBinding> bindings = inspector.inspect();

        Optional<GraphQlOperationBinding> binding = inspector.findQueryBinding("students");
        assertThat(binding).isPresent();
        assertThat(binding.get().kind()).isEqualTo(GraphQlOperationKind.MODEL_LIST_QUERY);
        assertThat(binding.get().entity().name()).isEqualTo("Student");
    }
}
