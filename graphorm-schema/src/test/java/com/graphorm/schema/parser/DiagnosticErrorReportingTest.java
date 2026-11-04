package com.graphorm.schema.parser;

import com.graphorm.schema.ast.SourcePosition;
import com.graphorm.schema.exception.SchemaValidationException;
import com.graphorm.schema.validation.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Diagnostic Error Reporting & Syntax Listener Unit Tests")
class DiagnosticErrorReportingTest {

    @Test
    @DisplayName("Should report syntax error with line/column coordinates for missing closing brace")
    void shouldReportSyntaxErrorForMissingBrace() {
        String malformed = """
                model User {
                    id Int @id
                // missing closing brace
                """;

        assertThatThrownBy(() -> GraphOrmParserFacade.parse(malformed, "broken.graphorm"))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("Syntax error")
                .hasMessageContaining("broken.graphorm");

        GraphOrmParserFacade.ParseResult result = GraphOrmParserFacade.parseWithDiagnostics(malformed, "broken.graphorm");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).isNotEmpty();
        Diagnostic firstError = result.diagnostics().get(0);
        assertThat(firstError.position().sourceFile()).isEqualTo("broken.graphorm");
        assertThat(firstError.position().line()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should accurately format diagnostic message with caret pointer")
    void shouldFormatDiagnosticWithCaret() {
        String source = """
                model Student {
                    id NonExistentType @id
                }
                """;

        Diagnostic diag = Diagnostic.error("Unknown type 'NonExistentType'", "schema.graphorm", 2, 8);
        String formatted = diag.formatWithCaret(source);

        assertThat(formatted).contains("[ERROR] schema.graphorm:2:8 - Unknown type 'NonExistentType'");
        assertThat(formatted).contains("     2 |     id NonExistentType @id");
        assertThat(formatted).contains("       |        ^");
    }

    @Test
    @DisplayName("Should capture syntax error for invalid characters")
    void shouldCaptureSyntaxErrorForInvalidCharacters() {
        String invalid = """
                model #BadName {
                    id Int @id
                }
                """;

        GraphOrmParserFacade.ParseResult result = GraphOrmParserFacade.parseWithDiagnostics(invalid, "invalid.graphorm");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics().get(0).format()).contains("invalid.graphorm");
    }

    @Test
    @DisplayName("Should construct SchemaValidationException with all diagnostics")
    void shouldConstructSchemaValidationException() {
        Diagnostic d1 = Diagnostic.error("Error 1", SourcePosition.of(1, 1));
        Diagnostic d2 = Diagnostic.warning("Warning 1", SourcePosition.of(2, 5));

        SchemaValidationException ex = new SchemaValidationException(List.of(d1, d2), "line 1\nline 2");
        assertThat(ex.getDiagnostics()).hasSize(2);
        assertThat(ex.getErrors()).hasSize(1);
        assertThat(ex.getMessage()).contains("Error 1");
        assertThat(ex.getMessage()).contains("Warning 1");
        assertThat(ex.getSourceCode()).isEqualTo("line 1\nline 2");
    }
}
