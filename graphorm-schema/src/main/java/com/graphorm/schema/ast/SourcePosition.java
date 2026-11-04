package com.graphorm.schema.ast;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.Objects;

/**
 * Immutable source coordinate tracking for AST nodes and parser diagnostics.
 *
 * @param sourceFile source file path or identifier (e.g., "schema.graphorm")
 * @param line       1-based line number
 * @param column     0-based or 1-based character column position
 * @param endLine    1-based ending line number
 * @param endColumn  ending column position
 */
public record SourcePosition(
        String sourceFile,
        int line,
        int column,
        int endLine,
        int endColumn
) {
    public SourcePosition {
        if (sourceFile == null || sourceFile.isBlank()) {
            sourceFile = "schema.graphorm";
        }
    }

    public static SourcePosition from(Token token) {
        if (token == null) {
            return unknown();
        }
        int line = token.getLine();
        int col = token.getCharPositionInLine() + 1;
        int length = token.getText() != null ? token.getText().length() : 1;
        return new SourcePosition(token.getInputStream() != null ? token.getInputStream().getSourceName() : "schema.graphorm",
                line, col, line, col + length);
    }

    public static SourcePosition from(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return unknown();
        }
        Token start = ctx.getStart();
        Token stop = ctx.getStop() != null ? ctx.getStop() : start;
        int startLine = start.getLine();
        int startCol = start.getCharPositionInLine() + 1;
        int endLine = stop.getLine();
        int endCol = stop.getCharPositionInLine() + 1 + (stop.getText() != null ? stop.getText().length() : 1);
        String file = start.getInputStream() != null ? start.getInputStream().getSourceName() : "schema.graphorm";
        return new SourcePosition(file, startLine, startCol, endLine, endCol);
    }

    public static SourcePosition of(int line, int column) {
        return new SourcePosition("schema.graphorm", line, column, line, column + 1);
    }

    public static SourcePosition unknown() {
        return new SourcePosition("schema.graphorm", 1, 1, 1, 1);
    }

    @Override
    public String toString() {
        return sourceFile + ":" + line + ":" + column;
    }
}
