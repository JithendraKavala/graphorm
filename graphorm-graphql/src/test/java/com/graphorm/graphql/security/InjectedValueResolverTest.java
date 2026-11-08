package com.graphorm.graphql.security;

import com.graphorm.core.metadata.FieldMetadata;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-15.3: Backend-Injected Column Resolver Tests (@injected)")
class InjectedValueResolverTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private DataFetchingEnvironment mockEnv(GraphQLContext graphQlContext, Object localContext) {
        return (DataFetchingEnvironment) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingEnvironment.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGraphQlContext" -> graphQlContext;
                    case "getLocalContext" -> localContext;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Should resolve direct key from GraphQLContext")
    void shouldResolveDirectKeyFromGraphQlContext() {
        GraphQLContext context = GraphQLContext.newContext()
                .of("tenantId", "tenant-123", "orgId", 999L)
                .build();
        DataFetchingEnvironment env = mockEnv(context, null);

        Object tenantId = InjectedValueResolver.resolve("tenantId", env);
        Object orgId = InjectedValueResolver.resolve("orgId", env);

        assertThat(tenantId).isEqualTo("tenant-123");
        assertThat(orgId).isEqualTo(999L);
    }

    @Test
    @DisplayName("Should resolve auth map from GraphQLContext")
    void shouldResolveAuthMapFromGraphQlContext() {
        GraphQLContext context = GraphQLContext.newContext()
                .of("auth", Map.of("username", "alice", "role", "ADMIN", "department", "Engineering"))
                .build();
        DataFetchingEnvironment env = mockEnv(context, null);

        assertThat(InjectedValueResolver.resolve("auth.username", env)).isEqualTo("alice");
        assertThat(InjectedValueResolver.resolve("auth.role", env)).isEqualTo("ADMIN");
        assertThat(InjectedValueResolver.resolve("auth.department", env)).isEqualTo("Engineering");
    }

    @Test
    @DisplayName("Should resolve from LocalContext if absent in GraphQLContext")
    void shouldResolveFromLocalContext() {
        Map<String, Object> local = Map.of(
                "tenantId", "local-tenant",
                "auth", Map.of("role", "MANAGER", "username", "bob")
        );
        DataFetchingEnvironment env = mockEnv(GraphQLContext.newContext().build(), local);

        assertThat(InjectedValueResolver.resolve("tenantId", env)).isEqualTo("local-tenant");
        assertThat(InjectedValueResolver.resolve("auth.role", env)).isEqualTo("MANAGER");
        assertThat(InjectedValueResolver.resolve("auth.username", env)).isEqualTo("bob");
    }

    @Test
    @DisplayName("Should resolve auth.username and auth.role from Spring SecurityContextHolder")
    void shouldResolveFromSpringSecurityContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                "john.doe",
                "secret",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"), new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        DataFetchingEnvironment env = mockEnv(GraphQLContext.newContext().build(), null);

        assertThat(InjectedValueResolver.resolve("auth.username", env)).isEqualTo("john.doe");
        assertThat(InjectedValueResolver.resolve("auth.name", env)).isEqualTo("john.doe");
        assertThat(InjectedValueResolver.resolve("auth.role", env)).isEqualTo("STAFF");
    }

    @Test
    @DisplayName("Should handle role without ROLE_ prefix in Spring Security")
    void shouldHandleRoleWithoutPrefix() {
        var auth = new UsernamePasswordAuthenticationToken(
                "charlie",
                "secret",
                List.of(new SimpleGrantedAuthority("SUPERUSER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        DataFetchingEnvironment env = mockEnv(GraphQLContext.newContext().build(), null);

        assertThat(InjectedValueResolver.resolve("auth.role", env)).isEqualTo("SUPERUSER");
    }

    @Test
    @DisplayName("Should resolve using FieldMetadata")
    void shouldResolveUsingFieldMetadata() {
        GraphQLContext context = GraphQLContext.newContext()
                .of("tenantId", "tenant-xyz")
                .build();
        DataFetchingEnvironment env = mockEnv(context, null);

        FieldMetadata injectedField = FieldMetadata.builder("tenantId", String.class)
                .injected(true, "tenantId")
                .build();
        FieldMetadata normalField = FieldMetadata.builder("name", String.class).build();

        assertThat(InjectedValueResolver.resolve(injectedField, env)).isEqualTo("tenant-xyz");
        assertThat(InjectedValueResolver.resolve(normalField, env)).isNull();
        assertThat(InjectedValueResolver.resolve((FieldMetadata) null, env)).isNull();
    }

    @Test
    @DisplayName("Should return null when key not found")
    void shouldReturnNullWhenNotFound() {
        DataFetchingEnvironment env = mockEnv(GraphQLContext.newContext().build(), null);

        assertThat(InjectedValueResolver.resolve("nonExistent", env)).isNull();
        assertThat(InjectedValueResolver.resolve("auth.unknown", env)).isNull();
        assertThat(InjectedValueResolver.resolve("tenantId", null)).isNull();
    }
}
