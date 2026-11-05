package com.graphorm.graphql.engine;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.graphql.schema.GraphQlOperationBinding;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GraphOrmGraphQlRegistrar Unit Tests (TASK-10.1, TASK-10.4)")
class GraphOrmGraphQlRegistrarTest {

    private ModelRegistry modelRegistry;
    private JdbcExecutor mockExecutor;

    @BeforeEach
    void setUp() {
        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addPrimaryKeyField("id")
                .build();

        ViewModelMetadata dashboard = ViewModelMetadata.builder("StudentDashboard")
                .viewName("vw_dashboard")
                .databaseBacked(true)
                .addField(FieldMetadata.builder("studentId", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("studentName", String.class).build())
                .build();

        modelRegistry = ModelRegistry.builder()
                .addModel(student)
                .addViewModel(dashboard)
                .build();

        DataSource stubDs = new DataSourceStub();
        mockExecutor = new JdbcExecutor(stubDs, new PostgreSqlDialect());
    }

    @Test
    @DisplayName("register: wires queries and mutations into GraphQLCodeRegistry")
    void testRegisterCodeRegistry() {
        String sdl = """
                type Student {
                    id: ID!
                    name: String!
                }
                
                type StudentDashboard {
                    studentId: Int!
                    studentName: String!
                }
                
                input StudentInput {
                    name: String!
                }
                
                type Query {
                    students: [Student!]!
                    student(id: ID!): Student
                    studentDashboards: [StudentDashboard!]!
                    studentDashboard(studentId: Int!): StudentDashboard
                }
                
                type Mutation {
                    createStudent(input: StudentInput!): Student!
                    updateStudent(id: ID!, input: StudentInput!): Student!
                    deleteStudent(id: ID!): Boolean!
                }
                """;

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(new StringReader(sdl));
        GraphQLCodeRegistry.Builder codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

        GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(modelRegistry, mockExecutor);
        List<GraphQlOperationBinding> bindings = registrar.register(codeRegistryBuilder, typeRegistry);

        assertThat(bindings).hasSize(7);
        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Query", "students"))).isTrue();
        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Query", "student"))).isTrue();
        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Query", "studentDashboards"))).isTrue();
        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Query", "studentDashboard"))).isTrue();
        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Mutation", "createStudent"))).isTrue();
        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Mutation", "updateStudent"))).isTrue();
        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Mutation", "deleteStudent"))).isTrue();
    }

    @Test
    @DisplayName("register: respects custom resolvers and does not overwrite them")
    void testCustomResolverCoexistence() {
        String sdl = """
                type Student {
                    id: ID!
                    name: String!
                }
                
                type Query {
                    students: [Student!]!
                    student(id: ID!): Student
                }
                """;

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(new StringReader(sdl));
        GraphQLCodeRegistry.Builder codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

        DataFetcher<?> customFetcher = env -> List.of();
        codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "students"), customFetcher);

        GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(modelRegistry, mockExecutor)
                .withCustomResolver("Query", "students");

        registrar.register(codeRegistryBuilder, typeRegistry);

        GraphQLFieldDefinition fieldDef = GraphQLFieldDefinition.newFieldDefinition()
                .name("students")
                .type(Scalars.GraphQLString)
                .build();

        assertThat(codeRegistryBuilder.getDataFetcher(FieldCoordinates.coordinates("Query", "students"), fieldDef))
                .isSameAs(customFetcher);
    }

    @Test
    @DisplayName("register: strictly rejects view mutation when failFast is enabled")
    void testStrictViewMutationRejectionFailFast() {
        String sdl = """
                type StudentDashboard {
                    studentId: Int!
                    studentName: String!
                }
                
                type Query {
                    studentDashboards: [StudentDashboard!]!
                }
                
                type Mutation {
                    createStudentDashboard(name: String!): StudentDashboard!
                }
                """;

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(new StringReader(sdl));
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();

        GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(modelRegistry, mockExecutor)
                .failFastOnViewMutation(true);

        assertThatThrownBy(() -> registrar.register(wiringBuilder, typeRegistry))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("StudentDashboard")
                .hasMessageContaining("strictly read-only");
    }

    @Test
    @DisplayName("register: strictly omits view mutation registration when failFast is disabled")
    void testStrictViewMutationOmissionWhenNotFailFast() {
        String sdl = """
                type StudentDashboard {
                    studentId: Int!
                    studentName: String!
                }
                
                type Query {
                    studentDashboards: [StudentDashboard!]!
                }
                
                type Mutation {
                    createStudentDashboard(name: String!): StudentDashboard!
                }
                """;

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(new StringReader(sdl));
        GraphQLCodeRegistry.Builder codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

        GraphOrmGraphQlRegistrar registrar = new GraphOrmGraphQlRegistrar(modelRegistry, mockExecutor)
                .failFastOnViewMutation(false);

        registrar.register(codeRegistryBuilder, typeRegistry);

        assertThat(codeRegistryBuilder.hasDataFetcher(FieldCoordinates.coordinates("Mutation", "createStudentDashboard")))
                .isFalse();
    }

    private static class DataSourceStub implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException("Stub DataSource");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException("Stub DataSource");
        }

        @Override
        public java.io.PrintWriter getLogWriter() { return null; }
        @Override
        public void setLogWriter(java.io.PrintWriter out) {}
        @Override
        public void setLoginTimeout(int seconds) {}
        @Override
        public int getLoginTimeout() { return 0; }
        @Override
        public java.util.logging.Logger getParentLogger() { return null; }
        @Override
        public <T> T unwrap(Class<T> iface) { return null; }
        @Override
        public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
