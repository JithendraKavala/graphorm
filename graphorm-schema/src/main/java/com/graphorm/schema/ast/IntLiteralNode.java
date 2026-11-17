package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Integer literal AST node (e.g. 10, -5).
 */
public record IntLiteralNode(
        SourcePosition position,
        long value,
        String rawText
) implements ExpressionNode {

    public IntLiteralNode {
        Objects.requireNonNull(position, "position must not be null");
        if (rawText == null) {
            rawText = String.valueOf(value);
        }
    }

    public IntLiteralNode(SourcePosition position, long value) {
        this(position, value, String.valueOf(value));
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitIntLiteral(this);
    }
}
