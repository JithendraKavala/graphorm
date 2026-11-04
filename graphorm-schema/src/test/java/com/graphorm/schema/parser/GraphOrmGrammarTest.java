package com.graphorm.schema.parser;

import com.graphorm.schema.ast.SchemaDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GraphOrm Grammar & Lexer Unit Tests")
class GraphOrmGrammarTest {

    @Test
    @DisplayName("Should parse comments and whitespace seamlessly")
    void shouldParseCommentsAndWhitespace() {
        String schema = """
                // Single line header comment
                /*
                 * Multi-line block comment
                 */
                datasource db {
                    provider = "postgresql" // inline comment
                    url      = env("DATABASE_URL")
                }

                // Another comment before model
                model User {
                    id Int @id /* inline block comment */ @default(autoincrement())
                    name String
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        assertThat(doc.datasources()).hasSize(1);
        assertThat(doc.models()).hasSize(1);
        assertThat(doc.models().get(0).name()).isEqualTo("User");
    }

    @Test
    @DisplayName("Should parse datasource and generator configuration blocks")
    void shouldParseDatasourceAndGenerator() {
        String schema = """
                datasource db {
                    provider = "postgresql"
                    url      = "postgresql://localhost:5432/mydb"
                }

                generator client {
                    provider = "graphorm-client-java"
                    output   = "./generated"
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        assertThat(doc.datasources()).hasSize(1);
        assertThat(doc.datasources().get(0).name()).isEqualTo("db");
        assertThat(doc.findDatasource("db")).isPresent();

        assertThat(doc.generators()).hasSize(1);
        assertThat(doc.generators().get(0).name()).isEqualTo("client");
        assertThat(doc.findGenerator("client")).isPresent();
    }

    @Test
    @DisplayName("Should parse various literal types: strings with escapes, numbers, booleans, arrays")
    void shouldParseVariousLiteralTypes() {
        String schema = """
                model Config {
                    id Int @id
                    str String @default("hello\\nworld\\t\\"quoted\\"")
                    active Boolean @default(true)
                    inactive Boolean @default(false)
                    count Int @default(42)
                    negative Int @default(-10)
                    rating Float @default(4.75)
                    negativeFloat Float @default(-3.14)
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        assertThat(doc.models()).hasSize(1);
        assertThat(doc.models().get(0).fields()).hasSize(8);
    }

    @Test
    @DisplayName("Should parse single quote string literals")
    void shouldParseSingleQuoteStrings() {
        String schema = """
                model Item {
                    id Int @id
                    label String @default('sample')
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        assertThat(doc.models()).hasSize(1);
    }
}
