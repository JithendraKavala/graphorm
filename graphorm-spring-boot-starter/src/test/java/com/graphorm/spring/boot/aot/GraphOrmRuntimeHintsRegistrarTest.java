package com.graphorm.spring.boot.aot;

import com.graphorm.core.exception.DataAccessException;
import com.graphorm.core.exception.GraphOrmException;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.jdbc.dialect.PostgreSqlErrorTranslator;
import com.graphorm.schema.GraphOrmLexer;
import com.graphorm.schema.GraphOrmParser;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphOrmRuntimeHintsRegistrar GraalVM AOT Tests")
class GraphOrmRuntimeHintsRegistrarTest {

    @Test
    @DisplayName("Registers reflection hints for ANTLR4 lexer and parser")
    void shouldRegisterAntlrReflectionHints() {
        RuntimeHints hints = new RuntimeHints();
        new GraphOrmRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(GraphOrmLexer.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(GraphOrmParser.class)).accepts(hints);
    }

    @Test
    @DisplayName("Registers reflection hints for Core Metadata and AST records")
    void shouldRegisterMetadataAndAstReflectionHints() {
        RuntimeHints hints = new RuntimeHints();
        new GraphOrmRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(ModelMetadata.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ViewModelMetadata.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ModelRegistry.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(SchemaDocument.class)).accepts(hints);
    }

    @Test
    @DisplayName("Registers reflection hints for exceptions and error translator")
    void shouldRegisterExceptionAndTranslatorReflectionHints() {
        RuntimeHints hints = new RuntimeHints();
        new GraphOrmRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(PostgreSqlErrorTranslator.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(DataAccessException.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(GraphOrmException.class)).accepts(hints);
    }

    @Test
    @DisplayName("Registers reflection hints for auto-configuration properties")
    void shouldRegisterPropertiesReflectionHints() {
        RuntimeHints hints = new RuntimeHints();
        new GraphOrmRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(GraphOrmProperties.class)).accepts(hints);
    }

    @Test
    @DisplayName("Registers resource hints for schema files and GraphQL SDL")
    void shouldRegisterResourceHints() {
        RuntimeHints hints = new RuntimeHints();
        new GraphOrmRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource().forResource("schema.graphorm")).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("graphql/schema.graphqls")).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")).accepts(hints);
    }

    @Test
    @DisplayName("Registers serialization hints for portable exceptions")
    void shouldRegisterSerializationHints() {
        RuntimeHints hints = new RuntimeHints();
        new GraphOrmRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.serialization().onType(GraphOrmException.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.serialization().onType(DataAccessException.class)).accepts(hints);
    }
}
