package com.graphorm.graphql.security;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.graphql.engine.GraphOrmGraphQlEngine;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaxQueryDepthInstrumentation Unit Tests")
class MaxQueryDepthInstrumentationTest {

    static class StubJdbcExecutor extends JdbcExecutor {
        StubJdbcExecutor() {
            super((DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> null
            ), new PostgreSqlDialect());
        }

        @Override
        public List<Map<String, Object>> queryForList(SelectQuery query) {
            return List.of(Map.of("id", 1, "name", "Alice", "departmentId", 1));
        }
    }

    private GraphOrmGraphQlEngine engine;

    @BeforeEach
    void setUp() {
        JdbcExecutor executor = new StubJdbcExecutor();

        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("departmentId", Integer.class).build())
                .addRelation(RelationMetadata.builder("department")
                        .sourceModel("Student")
                        .targetModel("Department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .foreignKeyFields(List.of("departmentId"))
                        .referenceFields(List.of("id"))
                        .build())
                .build();

        ModelMetadata department = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addRelation(RelationMetadata.builder("students")
                        .sourceModel("Department")
                        .targetModel("Student")
                        .relationType(RelationType.ONE_TO_MANY)
                        .foreignKeyFields(List.of("departmentId"))
                        .referenceFields(List.of("id"))
                        .build())
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(student)
                .addModel(department)
                .build();

        String sdl = """
                type Query {
                    students: [Student!]!
                }
                type Student {
                    id: ID!
                    name: String!
                    department: Department
                }
                type Department {
                    id: ID!
                    name: String!
                    students: [Student!]!
                }
                """;

        engine = GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .schema(sdl)
                .maxQueryDepth(3)
                .build();
    }

    @Test
    @DisplayName("Should accept query within maximum depth limit (depth <= 3)")
    void shouldAcceptQueryWithinDepthLimit() {
        // Depth 2: Query(1) -> students(2) -> id, name(3)
        String query = """
                query {
                    students {
                        id
                        name
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject nested query exceeding maximum depth limit (depth > 3)")
    void shouldRejectQueryExceedingDepthLimit() {
        // Depth: Query(1) -> students(2) -> department(3) -> students(4) -> id(5)
        String query = """
                query {
                    students {
                        id
                        department {
                            students {
                                id
                            }
                        }
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().getFirst().getMessage())
                .contains("Maximum query depth exceeded");
        assertThat(result.getErrors().getFirst().getExtensions())
                .containsEntry("errorCode", "GRAPHQL_DEPTH_LIMIT_EXCEEDED");
    }
}
