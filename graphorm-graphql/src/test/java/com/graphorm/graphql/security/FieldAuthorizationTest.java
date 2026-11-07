package com.graphorm.graphql.security;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.graphql.engine.GraphOrmGraphQlEngine;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.ExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-15.2: Field-Level Authorization Tests (@auth)")
class FieldAuthorizationTest {

    private final String sdl = """
            directive @auth(roles: [String!]!) on FIELD_DEFINITION

            type Student {
              id: ID!
              name: String!
              ssn: String @auth(roles: ["ADMIN"])
            }

            type Query {
              students: [Student!]!
            }
            """;

    private final ModelMetadata studentModel = ModelMetadata.builder("Student")
            .tableName("students")
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("name", String.class).build())
            .addField(FieldMetadata.builder("ssn", String.class).nullable(true).authRoles(List.of("ADMIN")).build())
            .addPrimaryKeyField("id")
            .build();

    private final ModelRegistry registry = ModelRegistry.builder()
            .addModel(studentModel)
            .build();

    private final DataSource dummyDataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{Connection.class},
                            (cProxy, cMethod, cArgs) -> null
                    );
                }
                return null;
            }
    );

    private final JdbcExecutor executor = new JdbcExecutor(dummyDataSource, new PostgreSqlDialect()) {
        @Override
        public List<Map<String, Object>> queryForList(SelectQuery query) {
            return List.of(Map.of("id", 1L, "name", "John Doe", "ssn", "000-11-2222"));
        }
    };

    @Test
    @DisplayName("Allows access to unannotated fields for all users")
    void testUnannotatedFieldAccessAllowed() {
        GraphOrmGraphQlEngine engine = GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .schema(sdl)
                .fieldAuthorizer(FieldAuthorizer.DENY_ALL)
                .build();

        ExecutionResult result = engine.execute("query { students { id name } }");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Blocks access to protected @auth field when authorizer denies role")
    void testProtectedFieldDenied() {
        // Authorizer lacking ADMIN role
        FieldAuthorizer regularUserAuth = roles -> false;
        GraphOrmGraphQlEngine engine = GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .schema(sdl)
                .fieldAuthorizer(regularUserAuth)
                .build();

        ExecutionResult result = engine.execute("query { students { id name ssn } }");
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage()).contains("Access denied");
        assertThat(result.getErrors().get(0).getExtensions()).containsEntry("errorCode", "ACCESS_DENIED");
    }

    @Test
    @DisplayName("Permits access to protected @auth field when authorizer grants role")
    void testProtectedFieldPermitted() {
        // 1. Privileged admin user execution
        FieldAuthorizer adminAuth = roles -> roles.contains("ADMIN");
        GraphOrmGraphQlEngine adminEngine = GraphOrmGraphQlEngine.builder()
                .modelRegistry(registry)
                .executor(executor)
                .schema(sdl)
                .fieldAuthorizer(adminAuth)
                .build();

        ExecutionResult allowedResult = adminEngine.execute("query { students { id name ssn } }");
        assertThat(allowedResult.getErrors()).isEmpty();
        Object data = allowedResult.getData();
        assertThat(data).isNotNull();
    }
}
