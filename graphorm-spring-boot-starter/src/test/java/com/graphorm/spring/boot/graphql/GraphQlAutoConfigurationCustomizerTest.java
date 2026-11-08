package com.graphorm.spring.boot.graphql;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphQL AutoConfiguration Customizer Tests")
class GraphQlAutoConfigurationCustomizerTest {

    @Test
    @DisplayName("hasAuthDirectives returns true when model has authRoles")
    void testHasAuthDirectivesWhenModelHasRoles() {
        ModelMetadata model = ModelMetadata.builder("SecretDoc")
                .tableName("secret_docs")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .authRoles(List.of("ADMIN"))
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(model).build();
        assertThat(GraphQlAutoConfigurationCustomizer.hasAuthDirectives(registry)).isTrue();
    }

    @Test
    @DisplayName("hasAuthDirectives returns true when view has authRoles")
    void testHasAuthDirectivesWhenViewHasRoles() {
        ViewModelMetadata view = ViewModelMetadata.builder("SecretSummary")
                .viewName("vw_secret")
                .databaseBacked(true)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .authRoles(List.of("AUDITOR"))
                .build();

        ModelRegistry registry = ModelRegistry.builder().addViewModel(view).build();
        assertThat(GraphQlAutoConfigurationCustomizer.hasAuthDirectives(registry)).isTrue();
    }

    @Test
    @DisplayName("hasAuthDirectives returns true when field has authRoles")
    void testHasAuthDirectivesWhenFieldHasRoles() {
        ModelMetadata model = ModelMetadata.builder("User")
                .tableName("users")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("ssn", String.class).authRoles(List.of("ADMIN")).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(model).build();
        assertThat(GraphQlAutoConfigurationCustomizer.hasAuthDirectives(registry)).isTrue();
    }

    @Test
    @DisplayName("hasAuthDirectives returns false when no auth roles exist anywhere")
    void testHasAuthDirectivesWhenNoRoles() {
        ModelMetadata model = ModelMetadata.builder("PublicInfo")
                .tableName("public_info")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("text", String.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder().addModel(model).build();
        assertThat(GraphQlAutoConfigurationCustomizer.hasAuthDirectives(registry)).isFalse();
        assertThat(GraphQlAutoConfigurationCustomizer.hasAuthDirectives((ModelRegistry) null)).isFalse();
    }
}
