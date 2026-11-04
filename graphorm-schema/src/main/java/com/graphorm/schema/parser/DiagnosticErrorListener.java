package com.graphorm.schema.parser;

import com.graphorm.schema.ast.SourcePosition;
import com.graphorm.schema.validation.Diagnostic;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom ANTLR error listener that captures syntax errors into strongly-typed diagnostics.
 */
public class DiagnosticErrorListener extends BaseErrorListener {

    private final String sourceFile;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public DiagnosticErrorListener() {
        this("schema.graphorm");
    }

    public DiagnosticErrorListener(String sourceFile) {
        this.sourceFile = sourceFile != null ? sourceFile : "schema.graphorm";
    }

    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e
    ) {
        int col = charPositionInLine + 1;
        int endCol = col;
        if (offendingSymbol instanceof Token token) {
            String text = token.getText();
            if (text != null) {
                endCol = col + text.length();
            }
        }
        SourcePosition pos = new SourcePosition(sourceFile, line, col, line, endCol);
        diagnostics.add(Diagnostic.error("Syntax error: " + msg, pos));
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public List<Diagnostic> getErrors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }
}
