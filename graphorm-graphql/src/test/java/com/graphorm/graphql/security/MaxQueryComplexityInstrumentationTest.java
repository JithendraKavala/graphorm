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

@DisplayName("MaxQueryComplexityInstrumentation Unit Tests")
class MaxQueryComplexityInstrumentationTest {

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
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(student)
                .addModel(department)
                .build();

        String sdl = """
                type Query {
                    students(limit: Int): [Student!]!
                }
                type Student {
                    id: ID!
                    name: String!
                    department: Department
                }
                type Department {
                    id: ID!
                    name: String!
                }
                """;

        engine = GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .schema(sdl)
                .maxQueryComplexity(15)
                .build();
    }

    @Test
    @DisplayName("Should accept query within maximum complexity score (complexity <= 15)")
    void shouldAcceptQueryWithinComplexityLimit() {
        // students(5) -> id(1) + name(1) = 7 <= 15
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
    @DisplayName("Should reject query exceeding maximum complexity score (complexity > 15)")
    void shouldRejectQueryExceedingComplexityLimit() {
        // students with limit: 20 -> base(5) + 20 * childComplexity > 15
        String query = """
                query {
                    students(limit: 20) {
                        id
                        name
                        department {
                            id
                            name
                        }
                    }
                }
                """;

        ExecutionResult result = engine.execute(query);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().getFirst().getMessage())
                .contains("Maximum query complexity exceeded");
        assertThat(result.getErrors().getFirst().getExtensions())
                .containsEntry("errorCode", "GRAPHQL_COMPLEXITY_LIMIT_EXCEEDED");
    }
}
